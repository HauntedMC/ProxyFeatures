package nl.hauntedmc.proxyfeatures.features.friends.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.common.util.APIRegistry;
import nl.hauntedmc.proxyfeatures.features.friends.Friends;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendsService;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendStatus;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.VanishAPI;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class FriendCommand extends FeatureCommand {

    private final Friends feature;
    private final FriendsService svc;

    public FriendCommand(Friends feature) {
        this.feature = feature;
        this.svc = feature.getService();
    }

    @Override
    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] args = inv.arguments();

        if (!(src instanceof Player player)) {
            sendMsg(src, "friend.player_only");
            return;
        }

        if (args.length == 0) {
            PlayerEntity me = getEntity(player);
            if (!svc.getOrCreateSettings(me).isEnabled()) {
                sendMsg(player, "friend.mode_disabled");
                return;
            }

            // Use cached snapshots + live proxy lookups (skip vanished)
            var friendSnaps = svc.acceptedFriendSnapshots(me);
            var onlineFriends = friendSnaps.stream()
                    .map(snap -> feature.getPlugin().getProxy()
                            .getPlayer(java.util.UUID.fromString(snap.uuid()))
                            .filter(pl -> !APIRegistry.get(VanishAPI.class)
                                    .map(api -> api.isVanished(pl.getUniqueId()))
                                    .orElse(false))
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .toList();

            if (onlineFriends.isEmpty()) {
                sendMsg(player, "friend.no_friends_online");
            } else {
                sendMsg(player, "friend.online_header");
                onlineFriends.forEach(f -> {
                    String srv = f.getCurrentServer()
                            .map(conn -> conn.getServer().getServerInfo().getName())
                            .orElse("?");
                    player.sendMessage(feature.getLocalizationHandler()
                            .getMessage("friend.online_entry")
                            .withPlaceholders(Map.of(
                                    "player", f.getUsername(),
                                    "server", srv))
                            .build());
                });
            }

            // Use cached, cheap projection instead of loading full entities
            int pending = svc.incomingRequestUsernames(me).size();
            if (pending > 0) {
                sendMsg(player, "friend.pending_requests",
                        Map.of("count", String.valueOf(pending)));
            }
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "list" -> list(player);
            case "add" -> add(player, args);
            case "remove" -> remove(player, args);
            case "accept" -> accept(player, args);
            case "deny" -> deny(player, args);
            case "cancel" -> cancel(player, args);
            case "acceptall" -> acceptAll(player);
            case "denyall" -> denyAll(player);
            case "server" -> connect(player, args);
            case "requests" -> requests(player);
            case "block" -> block(player, args);
            case "unblock" -> unblock(player, args);
            case "disable" -> disable(player);
            case "enable" -> enable(player);
            default -> sendMsg(src, "friend.usage");
        }
    }

    @Override
    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.friend.command.friends");
    }

    private void list(Player p) {
        PlayerEntity me = getEntity(p);

        // Use snapshots to avoid lazy loads
        var friends = svc.acceptedFriendSnapshots(me);

        long online = friends.stream().filter(f ->
                feature.getPlugin().getProxy()
                        .getPlayer(java.util.UUID.fromString(f.uuid()))
                        .filter(pl -> !APIRegistry.get(VanishAPI.class)
                                .map(api -> api.isVanished(pl.getUniqueId()))
                                .orElse(false))
                        .isPresent()).count();

        p.sendMessage(feature.getLocalizationHandler()
                .getMessage("friend.list.header")
                .withPlaceholders(Map.of(
                        "online", String.valueOf(online),
                        "total", String.valueOf(friends.size())))
                .build());

        for (var snap : friends) {
            java.util.UUID fid = java.util.UUID.fromString(snap.uuid());
            Optional<Player> onlineP = feature.getPlugin().getProxy()
                    .getPlayer(fid)
                    .filter(pl -> !APIRegistry.get(VanishAPI.class)
                            .map(api -> api.isVanished(pl.getUniqueId()))
                            .orElse(false));

            boolean onl = onlineP.isPresent();
            String status = onl ? "&a● " : "&c● ";
            String name = onlineP.map(Player::getUsername).orElse(snap.username());

            String suffix = "";
            if (onl) {
                suffix = onlineP.flatMap(Player::getCurrentServer)
                        .map(ServerConnection::getServer)
                        .map(rs -> " &7(&f" + rs.getServerInfo().getName() + "&7)")
                        .orElse("");
            }

            p.sendMessage(feature.getLocalizationHandler()
                    .getMessage("friend.list.entry")
                    .withPlaceholders(Map.of(
                            "status", status,
                            "player", name + suffix))
                    .build());
        }
    }

    private void add(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerEntity me = getEntity(p);

        PlayerEntity target = resolvePlayer(args[1]);
        if (target == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (target.getId().equals(me.getId())) {
            sendMsg(p, "friend.self");
            return;
        }

        // Mode disabled check (target)
        if (!svc.targetAcceptsRequests(target)) {
            sendMsg(p, "friend.target_disabled");
            return;
        }

        // Block checks (cache-backed)
        if (svc.didIBlockTarget(me, target)) {
            sendMsg(p, "friend.you_blocked_target", Map.of("player", target.getUsername()));
            return;
        }
        if (svc.isBlockedByTarget(me, target)) {
            sendMsg(p, "friend.blocked_by_target", Map.of("player", target.getUsername()));
            return;
        }

        // Existing relation checks (both directions).
        // Use entity-based relation here (not relationStatus) to avoid writing a negative into the cache
        // just before we create a pending relation, which could cause brief staleness.
        Optional<nl.hauntedmc.proxyfeatures.features.friends.entity.FriendRelationEntity> meToTarget = svc.relation(me, target);
        Optional<nl.hauntedmc.proxyfeatures.features.friends.entity.FriendRelationEntity> targetToMe = svc.relation(target, me);

        if (meToTarget.isPresent()) {
            FriendStatus st = meToTarget.get().getStatus();
            if (st == FriendStatus.ACCEPTED) {
                sendMsg(p, "friend.already_friends");
                return;
            }
            if (st == FriendStatus.PENDING) {
                sendMsg(p, "friend.request_exists");
                return;
            }
            if (st == FriendStatus.BLOCKED) {
                sendMsg(p, "friend.you_blocked_target", Map.of("player", target.getUsername()));
                return;
            }
        }

        if (targetToMe.isPresent()) {
            FriendStatus st = targetToMe.get().getStatus();
            if (st == FriendStatus.ACCEPTED) {
                sendMsg(p, "friend.already_friends");
                return;
            }
            if (st == FriendStatus.BLOCKED) {
                sendMsg(p, "friend.blocked_by_target", Map.of("player", target.getUsername()));
                return;
            }
            if (st == FriendStatus.PENDING) {
                // Mutual request -> auto-accept atomically
                boolean ok = svc.acceptPending(target, me);
                if (ok) {
                    sendMsg(p, "friend.accepted", Map.of("player", target.getUsername()));
                    feature.getPlugin().getProxy().getPlayer(UUID.fromString(target.getUuid()))
                            .filter(pl -> !APIRegistry.get(VanishAPI.class)
                                    .map(api -> api.isVanished(pl.getUniqueId()))
                                    .orElse(false))
                            .ifPresent(t -> sendMsg(t, "friend.accepted",
                                    Map.of("player", p.getUsername())));
                } else {
                    sendMsg(p, "friend.request_exists");
                }
                return;
            }
        }

        // Create outgoing pending (kept here). Note: service handles most invalidations,
        // but pending creation goes straight to DB; short TTLs keep cache fresh enough.
        feature.getOrm().runInTransaction(s -> {
            PlayerEntity m = s.get(PlayerEntity.class, me.getId());
            PlayerEntity t = s.get(PlayerEntity.class, target.getId());
            s.persist(new nl.hauntedmc.proxyfeatures.features.friends.entity.FriendRelationEntity(m, t, FriendStatus.PENDING));
            return null;
        });

        sendMsg(p, "friend.add.sent", Map.of("player", target.getUsername()));

        feature.getPlugin().getProxy().getPlayer(UUID.fromString(target.getUuid()))
                .filter(pl -> !APIRegistry.get(VanishAPI.class)
                        .map(api -> api.isVanished(pl.getUniqueId()))
                        .orElse(false))
                .ifPresent(t -> sendMsg(t, "friend.add.received",
                        Map.of("player", p.getUsername())));
    }

    private void remove(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerEntity me = getEntity(p);
        PlayerEntity target = resolvePlayer(args[1]);
        if (target == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (target.getId().equals(me.getId())) {
            sendMsg(p, "friend.self");
            return;
        }

        boolean removed = svc.removeFriendship(me, target);
        if (removed) {
            sendMsg(p, "friend.removed", Map.of("player", target.getUsername()));
        } else {
            sendMsg(p, "friend.not_friends");
        }
    }

    private void accept(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerEntity me = getEntity(p);
        PlayerEntity from = resolvePlayer(args[1]);
        if (from == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (from.getId().equals(me.getId())) {
            sendMsg(p, "friend.self");
            return;
        }

        // Respect blocks or disabled states
        if (svc.didIBlockTarget(me, from) || svc.isBlockedByTarget(me, from)) {
            sendMsg(p, "friend.request_exists");
            return;
        }

        boolean ok = svc.acceptPending(from, me);
        if (ok) {
            sendMsg(p, "friend.accepted", Map.of("player", from.getUsername()));
            feature.getPlugin().getProxy().getPlayer(UUID.fromString(from.getUuid()))
                    .filter(pl -> !APIRegistry.get(VanishAPI.class)
                            .map(api -> api.isVanished(pl.getUniqueId()))
                            .orElse(false))
                    .ifPresent(t -> sendMsg(t, "friend.accepted",
                            Map.of("player", p.getUsername())));
        } else {
            sendMsg(p, "friend.not_found");
        }
    }

    private void deny(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerEntity me = getEntity(p);
        PlayerEntity other = resolvePlayer(args[1]);
        if (other == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (other.getId().equals(me.getId())) {
            sendMsg(p, "friend.self");
            return;
        }

        // Incoming?
        boolean denied = svc.denyIncoming(me, other);
        if (denied) {
            sendMsg(p, "friend.denied", Map.of("player", other.getUsername()));
            return;
        }

        // Outgoing pending? Then instruct to cancel.
        Optional<FriendStatus> outStatus = svc.relationStatus(me, other);
        if (outStatus.isPresent() && outStatus.get() == FriendStatus.PENDING) {
            sendMsg(p, "friend.cannot_deny_outgoing", Map.of("player", other.getUsername()));
            return;
        }

        sendMsg(p, "friend.not_found");
    }

    private void cancel(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerEntity me = getEntity(p);
        PlayerEntity target = resolvePlayer(args[1]);
        if (target == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (target.getId().equals(me.getId())) {
            sendMsg(p, "friend.self");
            return;
        }

        boolean ok = svc.cancelPending(me, target);
        if (ok) {
            sendMsg(p, "friend.cancelled", Map.of("player", target.getUsername()));
        } else {
            sendMsg(p, "friend.not_found");
        }
    }

    private void acceptAll(Player p) {
        PlayerEntity me = getEntity(p);
        int n = svc.acceptAll(me);
        if (n == 0) {
            sendMsg(p, "friend.no_requests");
        } else {
            sendMsg(p, "friend.accepted_many", Map.of("count", String.valueOf(n)));
        }
    }

    private void denyAll(Player p) {
        PlayerEntity me = getEntity(p);
        int n = svc.denyAll(me);
        if (n == 0) {
            sendMsg(p, "friend.no_requests");
        } else {
            sendMsg(p, "friend.denied_many", Map.of("count", String.valueOf(n)));
        }
    }

    private void connect(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerEntity me = getEntity(p);
        PlayerEntity target = resolvePlayer(args[1]);
        if (target == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (target.getId().equals(me.getId())) {
            sendMsg(p, "friend.self");
            return;
        }

        // Only need to know if accepted
        Optional<FriendStatus> st = svc.relationStatus(me, target);
        if (st.isEmpty() || st.get() != FriendStatus.ACCEPTED) {
            sendMsg(p, "friend.not_friends");
            return;
        }

        feature.getPlugin().getProxy().getPlayer(UUID.fromString(target.getUuid()))
                .filter(pl -> !APIRegistry.get(VanishAPI.class)
                        .map(api -> api.isVanished(pl.getUniqueId()))
                        .orElse(false))
                .ifPresentOrElse(t -> t.getCurrentServer().ifPresent(conn -> {
                    RegisteredServer srv = conn.getServer();
                    p.createConnectionRequest(srv).fireAndForget();
                    sendMsg(p, "friend.connected", Map.of("player", t.getUsername()));
                }), () -> sendMsg(p, "friend.not_online"));
    }

    private void requests(Player p) {
        PlayerEntity me = getEntity(p);

        // Project to plain usernames (cached)
        List<String> incoming = svc.incomingRequestUsernames(me);
        List<String> outgoing = svc.outgoingRequestUsernames(me);

        if (incoming.isEmpty() && outgoing.isEmpty()) {
            sendMsg(p, "friend.no_requests");
            return;
        }
        sendMsg(p, "friend.requests_header");

        incoming.forEach(u -> p.sendMessage(
                feature.getLocalizationHandler()
                        .getMessage("friend.requests.incoming_entry")
                        .withPlaceholders(Map.of("player", u))
                        .forAudience(p)
                        .build()
        ));

        outgoing.forEach(u -> p.sendMessage(
                feature.getLocalizationHandler()
                        .getMessage("friend.requests.outgoing_entry")
                        .withPlaceholders(Map.of("player", u))
                        .forAudience(p)
                        .build()
        ));
    }

    private void block(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerEntity me = getEntity(p);
        PlayerEntity target = resolvePlayer(args[1]);
        if (target == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (target.getId().equals(me.getId())) {
            sendMsg(p, "friend.self");
            return;
        }

        // Already blocked by me?
        if (svc.didIBlockTarget(me, target)) {
            sendMsg(p, "friend.already_blocked");
            return;
        }

        svc.block(me, target);
        sendMsg(p, "friend.blocked", Map.of("player", target.getUsername()));
    }

    private void unblock(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerEntity me = getEntity(p);
        PlayerEntity target = resolvePlayer(args[1]);
        if (target == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (target.getId().equals(me.getId())) {
            sendMsg(p, "friend.self");
            return;
        }

        boolean ok = svc.unblock(me, target);
        if (ok) {
            sendMsg(p, "friend.unblocked", Map.of("player", target.getUsername()));
        } else {
            sendMsg(p, "friend.not_blocked");
        }
    }

    private void disable(Player p) {
        svc.setEnabled(getEntity(p), false);
        sendMsg(p, "friend.mode_now_disabled");
    }

    private void enable(Player p) {
        svc.setEnabled(getEntity(p), true);
        sendMsg(p, "friend.mode_enabled");
    }

    private void sendMsg(CommandSource src, String key) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key)
                .forAudience(src).build());
    }

    private void sendMsg(CommandSource src, String key, Map<String, String> ph) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key)
                .withPlaceholders(ph).forAudience(src).build());
    }

    private PlayerEntity getEntity(Player p) {
        return svc.getPlayer(p.getUniqueId().toString()).orElseThrow();
    }

    private PlayerEntity resolvePlayer(String name) {
        // Use cache-backed, case-insensitive resolver
        return svc.resolvePlayerByUsernameCaseInsensitive(name).orElse(null);
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation inv) {
        CommandSource src = inv.source();
        if (!(src instanceof Player p)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String[] a = inv.arguments();
        List<String> subs = List.of(
                "list", "add", "remove", "accept", "deny",
                "acceptall", "denyall", "server", "requests", "block", "unblock",
                "disable", "enable", "cancel");

        // 0 of 1 argument: subcommand suggestions
        if (a.length == 0 || (a.length == 1 && a[0].isEmpty())) {
            return CompletableFuture.completedFuture(subs);
        }
        if (a.length == 1) {
            String partial = a[0].toLowerCase(Locale.ROOT);
            List<String> out = subs.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(out);
        }

        // From here: a.length >= 2 -> name suggestions per subcommand
        String sub = a[0].toLowerCase(Locale.ROOT);
        String partial = a[1].toLowerCase(Locale.ROOT);

        PlayerEntity me = getEntity(p);

        // Helpers
        java.util.function.Predicate<String> startsWith = name ->
                name != null && name.toLowerCase(Locale.ROOT).startsWith(partial);

        // Online & non-vanished players (usernames), excluding self
        List<Player> onlineNonVanished = feature.getPlugin().getProxy().getAllPlayers().stream()
                .filter(pl -> !isVanished(pl))
                .filter(pl -> !pl.getUniqueId().equals(p.getUniqueId()))
                .toList();

        List<String> onlineNonVanishedNames = onlineNonVanished.stream()
                .map(Player::getUsername)
                .toList();

        // Sets from database (via cached service methods)
        List<String> friendNames = svc.acceptedFriendUsernames(me);
        List<String> blockedNames = svc.blockedUsernames(me);
        List<String> incomingNames = svc.incomingRequestUsernames(me);
        List<String> outgoingNames = svc.outgoingRequestUsernames(me);

        // For /server we only want online friends (non-vanished)
        Set<UUID> friendUUIDs = svc.acceptedFriendSnapshots(me).stream()
                .map(snap -> java.util.UUID.fromString(snap.uuid()))
                .collect(Collectors.toSet());

        List<String> onlineFriendNames = onlineNonVanished.stream()
                .filter(pl -> friendUUIDs.contains(pl.getUniqueId()))
                .map(Player::getUsername)
                .toList();

        // Subcommand-specific choices
        List<String> result;
        switch (sub) {
            case "remove":
                result = friendNames.stream().filter(startsWith).toList();
                break;

            case "server":
                result = onlineFriendNames.stream().filter(startsWith).toList();
                break;

            case "accept":
            case "deny":
                result = incomingNames.stream().filter(startsWith).toList();
                break;

            case "cancel":
                result = outgoingNames.stream().filter(startsWith).toList();
                break;

            case "unblock":
                result = blockedNames.stream().filter(startsWith).toList();
                break;

            case "block":
                result = onlineNonVanishedNames.stream()
                        .filter(name -> !blockedNames.contains(name))
                        .filter(startsWith)
                        .toList();
                break;

            case "add":
                Set<String> exclude = new HashSet<>();
                exclude.addAll(friendNames);
                exclude.addAll(incomingNames);
                exclude.addAll(outgoingNames);
                exclude.addAll(blockedNames); // don't suggest players I blocked

                result = onlineNonVanishedNames.stream()
                        .filter(name -> !exclude.contains(name))
                        .filter(startsWith)
                        .toList();
                break;

            // Subcommands without a name argument
            case "list":
            case "requests":
            case "acceptall":
            case "denyall":
            case "disable":
            case "enable":
            default:
                result = Collections.emptyList();
        }

        return CompletableFuture.completedFuture(result);
    }

    // Small util to consistently check vanish
    private boolean isVanished(Player pl) {
        return APIRegistry.get(VanishAPI.class)
                .map(api -> api.isVanished(pl.getUniqueId()))
                .orElse(false);
    }

    @Override
    public String getName() {
        return "friends";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"fr", "friend"};
    }
}
