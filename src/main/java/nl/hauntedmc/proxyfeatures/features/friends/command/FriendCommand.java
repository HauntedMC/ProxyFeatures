package nl.hauntedmc.proxyfeatures.features.friends.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.friends.Friends;
import nl.hauntedmc.proxyfeatures.features.friends.FriendsService;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendRelationEntity;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendStatus;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class FriendCommand extends FeatureCommand {

    private final Friends feature;
    private final FriendsService svc;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public FriendCommand(Friends feature) {
        this.feature = feature;
        this.svc     = feature.getService();
    }

    @Override
    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] args     = inv.arguments();

        if (!(src instanceof Player player)) {
            src.sendMessage(Component.text("Alleen voor spelers."));
            return;
        }

        if (args.length == 0) {
            // show basic dashboard
            PlayerEntity me = getEntity(player);
            if (!svc.getOrCreateSettings(me).isEnabled()) {
                sendMsg(player, "friend.mode_disabled");
                return;
            }

            /* online friends */
            var on = svc.acceptedRelations(me).stream()
                    .map(r -> feature.getPlugin().getProxy()
                            .getPlayer(UUID.fromString(r.getFriend().getUuid()))
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .toList();

            if (on.isEmpty()) {
                player.sendMessage(Component.text("Geen vrienden online.").color(NamedTextColor.GRAY));
            } else {
                player.sendMessage(Component.text("Vrienden online:").color(NamedTextColor.YELLOW));
                on.forEach(f -> {
                    String srv = f.getCurrentServer()
                            .map(conn -> conn.getServer().getServerInfo().getName())
                            .orElse("?");
                    player.sendMessage(Component.text("- " + f.getUsername() + " ("+srv+")")
                            .color(NamedTextColor.GRAY));
                });
            }

            /* pending requests */
            int pending = svc.incomingRequests(me).size();
            if (pending > 0) {
                player.sendMessage(Component.text("Je hebt " + pending +
                                " openstaand(e) vriendverzoek(en). /friend requests")
                        .color(NamedTextColor.YELLOW));
            }
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "list"      -> list(player);
            case "info"      -> info(player, args);
            case "add"       -> add(player, args);
            case "remove"    -> remove(player, args);
            case "accept"    -> accept(player, args);
            case "deny"      -> deny(player, args);
            case "acceptall" -> acceptAll(player);
            case "denyall"   -> denyAll(player);
            case "server"    -> connect(player, args);
            case "requests"  -> requests(player);
            case "block"     -> block(player, args);
            case "unblock"   -> unblock(player, args);
            case "disable"   -> disable(player);
            case "enable"    -> enable(player);
            default          -> sendMsg(src, "friend.usage");
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.friend.command.friends");
    }

    private void list(Player p) {
        PlayerEntity me = getEntity(p);
        var friends = svc.acceptedRelations(me);

        long onlineCount = friends.stream().filter(f ->
                feature.getPlugin().getProxy()
                        .getPlayer(UUID.fromString(f.getFriend().getUuid()))
                        .isPresent()).count();

        p.sendMessage(feature.getLocalizationHandler()
                .getMessage("friend.list.header")
                .withPlaceholders(Map.of(
                        "online", String.valueOf(onlineCount),
                        "total",  String.valueOf(friends.size())))
                .build());

        for (var rel : friends) {
            UUID fid = UUID.fromString(rel.getFriend().getUuid());
            Optional<Player> online = feature.getPlugin().getProxy().getPlayer(fid);

            boolean isOnline = online.isPresent();
            String status = isOnline ? "&a● " : "&c● ";
            String name   = online.map(Player::getUsername)
                    .orElse(rel.getFriend().getUsername());

            String serverSuffix = "";
            if (isOnline) {
                serverSuffix = online.flatMap(Player::getCurrentServer)
                        .map(ServerConnection::getServer)
                        .map(rs -> " &7(&f" + rs.getServerInfo().getName() + "&7)")
                        .orElse("");
            }

            p.sendMessage(feature.getLocalizationHandler()
                    .getMessage("friend.list.entry")
                    .withPlaceholders(Map.of(
                            "status", status,
                            "player", name + serverSuffix))
                    .build());
        }
    }

    private void info(Player p, String[] args) {
        if (args.length != 2) { sendMsg(p, "friend.usage"); return; }

        Player targetOnline = feature.getPlugin().getProxy().getPlayer(args[1]).orElse(null);
        PlayerEntity me = getEntity(p);
        PlayerEntity target = resolvePlayer(args[1]);

        if (target == null) { sendMsg(p, "friend.not_found"); return; }

        Optional<FriendRelationEntity> rel = svc.relation(me, target)
                .filter(r -> r.getStatus() == FriendStatus.ACCEPTED);

        if (rel.isEmpty()) { sendMsg(p, "friend.not_friends"); return; }

        p.sendMessage(feature.getLocalizationHandler()
                .getMessage("friend.info.header")
                .withPlaceholders(Map.of("player", target.getUsername()))
                .build());

        if (targetOnline != null) {
            RegisteredServer srv = targetOnline.getCurrentServer()
                    .map(ServerConnection::getServer)
                    .orElse(null);
            String serverName = srv != null ? srv.getServerInfo().getName() : "Onbekend";
            sendLine(p, "friend.info.online", Map.of("server", serverName));
        } else {
        // --- before ---
            //        /*
            //        String last = Instant.ofEpochMilli(target.getLastSeen())
            //                .atZone(ZoneId.systemDefault())
            //                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"));
            //        sendLine(p, "friend.info.offline", Map.of("last", last));
            //        */
            //
            //        // --- after ---
            String last = "not implemented yet";
            sendLine(p, "friend.info.offline", Map.of("last", last));
        }
    }

    private void add(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerEntity me = getEntity(p);

        PlayerEntity target = resolvePlayer(args[1]);
        if (target == null) { sendMsg(p, "friend.not_found"); return; }
        if (target.getId().equals(me.getId())) { return; }

        // Two‑way checks
        Optional<FriendRelationEntity> existing =
                svc.relation(me, target).or(() -> svc.relation(target, me));

        if (existing.isPresent()) {
            FriendStatus st = existing.get().getStatus();
            if (st == FriendStatus.ACCEPTED) { sendMsg(p, "friend.already_friends"); }
            else                             { sendMsg(p, "friend.request_exists");   }
            return;
        }

        FriendRelationEntity req = new FriendRelationEntity(
                me, target, FriendStatus.PENDING);
        svc.saveRelation(req);

        sendMsg(p, "friend.add.sent", Map.of("player", target.getUsername()));

        // notify target if online
        feature.getPlugin().getProxy().getPlayer(UUID.fromString(target.getUuid()))
                .ifPresent(t -> sendMsg(t, "friend.add.received", Map.of("player", p.getUsername())));
    }

    private void remove(Player p, String[] args) {
        if (args.length != 2) { sendMsg(p, "friend.usage"); return; }
        PlayerEntity me = getEntity(p);
        PlayerEntity target = resolvePlayer(args[1]);
        if (target == null) { sendMsg(p, "friend.not_found"); return; }

        svc.relation(me, target).ifPresentOrElse(rel -> {
            svc.deleteRelation(rel);
            sendMsg(p, "friend.removed", Map.of("player", target.getUsername()));
            // Also delete reverse relation if exists
            svc.relation(target, me).ifPresent(svc::deleteRelation);
        }, () -> sendMsg(p, "friend.not_friends"));
    }

    private void accept(Player p, String[] args) {
        if (args.length != 2) { sendMsg(p, "friend.usage"); return; }
        PlayerEntity me = getEntity(p);
        PlayerEntity from = resolvePlayer(args[1]);
        if (from == null) { sendMsg(p, "friend.not_found"); return; }

        svc.relation(from, me).ifPresentOrElse(rel -> {
            if (rel.getStatus() != FriendStatus.PENDING) { return; }
            rel.setStatus(FriendStatus.ACCEPTED);
            svc.saveRelation(rel);

            // store reverse relation
            svc.saveRelation(new FriendRelationEntity(me, from, FriendStatus.ACCEPTED));

            sendMsg(p, "friend.accepted", Map.of("player", from.getUsername()));
            feature.getPlugin().getProxy().getPlayer(UUID.fromString(from.getUuid()))
                    .ifPresent(t -> sendMsg(t, "friend.accepted", Map.of("player", p.getUsername())));
        }, () -> sendMsg(p, "friend.not_found"));
    }

    private void deny(Player p, String[] args) {
        if (args.length != 2) { sendMsg(p, "friend.usage"); return; }
        PlayerEntity me = getEntity(p);
        PlayerEntity from = resolvePlayer(args[1]);
        if (from == null) { sendMsg(p, "friend.not_found"); return; }

        svc.relation(from, me).ifPresentOrElse(rel -> {
            if (rel.getStatus() == FriendStatus.PENDING) {
                svc.deleteRelation(rel);
                sendMsg(p, "friend.denied", Map.of("player", from.getUsername()));
            }
        }, () -> sendMsg(p, "friend.not_found"));
    }

    private void acceptAll(Player p) {
        PlayerEntity me = getEntity(p);
        var reqs = svc.incomingRequests(me);
        if (reqs.isEmpty()) { sendMsg(p, "friend.no_requests"); return; }

        reqs.forEach(rel -> {
            rel.setStatus(FriendStatus.ACCEPTED);
            svc.saveRelation(rel);
            svc.saveRelation(new FriendRelationEntity(me, rel.getPlayer(), FriendStatus.ACCEPTED));
        });
        sendMsg(p, "friend.accepted", Map.of("player", reqs.size()+" verzoeken"));
    }

    private void denyAll(Player p) {
        PlayerEntity me = getEntity(p);
        var reqs = svc.incomingRequests(me);
        if (reqs.isEmpty()) { sendMsg(p, "friend.no_requests"); return; }

        reqs.forEach(svc::deleteRelation);
        sendMsg(p, "friend.denied", Map.of("player", reqs.size()+" verzoeken"));
    }

    private void connect(Player p, String[] args) {
        if (args.length != 2) { sendMsg(p, "friend.usage"); return; }
        PlayerEntity me = getEntity(p);
        PlayerEntity target = resolvePlayer(args[1]);
        if (target == null) { sendMsg(p, "friend.not_found"); return; }

        Optional<FriendRelationEntity> rel = svc.relation(me, target)
                .filter(r -> r.getStatus() == FriendStatus.ACCEPTED);
        if (rel.isEmpty()) { sendMsg(p, "friend.not_friends"); return; }

        feature.getPlugin().getProxy().getPlayer(UUID.fromString(target.getUuid()))
                .ifPresentOrElse(t -> t.getCurrentServer().ifPresent(conn -> {
                    RegisteredServer srv = conn.getServer();
                    p.createConnectionRequest(srv).fireAndForget();
                    sendMsg(p, "friend.connected", Map.of("player", t.getUsername()));
                }), () -> sendMsg(p, "friend.not_online"));
    }

    private void requests(Player p) {
        PlayerEntity me = getEntity(p);
        var reqs = svc.incomingRequests(me);
        if (reqs.isEmpty()) { sendMsg(p, "friend.no_requests"); return; }
        p.sendMessage(Component.text("&eOpenstaande verzoeken:").color(NamedTextColor.YELLOW));
        reqs.forEach(r -> p.sendMessage(Component.text("- "+r.getPlayer().getUsername())));
    }

    private void block(Player p, String[] args) {
        if (args.length != 2) { sendMsg(p, "friend.usage"); return; }
        PlayerEntity me = getEntity(p);
        PlayerEntity target = resolvePlayer(args[1]);
        if (target == null) { sendMsg(p, "friend.not_found"); return; }

        svc.relation(me, target).ifPresentOrElse(rel -> {
            if (rel.getStatus() == FriendStatus.BLOCKED) { sendMsg(p, "friend.already_blocked"); }
            else {
                rel.setStatus(FriendStatus.BLOCKED); svc.saveRelation(rel);
                sendMsg(p, "friend.blocked", Map.of("player", target.getUsername()));
            }
        }, () -> {
            svc.saveRelation(new FriendRelationEntity(me, target, FriendStatus.BLOCKED));
            sendMsg(p, "friend.blocked", Map.of("player", target.getUsername()));
        });
    }

    private void unblock(Player p, String[] args) {
        if (args.length != 2) { sendMsg(p, "friend.usage"); return; }
        PlayerEntity me = getEntity(p);
        PlayerEntity target = resolvePlayer(args[1]);
        if (target == null) { sendMsg(p, "friend.not_found"); return; }

        svc.relation(me, target).ifPresentOrElse(rel -> {
            if (rel.getStatus() != FriendStatus.BLOCKED) {
                sendMsg(p, "friend.not_blocked");
            } else {
                svc.deleteRelation(rel);
                sendMsg(p, "friend.unblocked", Map.of("player", target.getUsername()));
            }
        }, () -> sendMsg(p, "friend.not_blocked"));
    }

    private void disable(Player p) {
        PlayerEntity me = getEntity(p);
        svc.setEnabled(me, false);
        sendMsg(p, "friend.mode_now_disabled");
    }

    private void enable(Player p) {
        PlayerEntity me = getEntity(p);
        svc.setEnabled(me, true);
        sendMsg(p, "friend.mode_enabled");
    }

    private void sendMsg(CommandSource src, String key) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key)
                .forAudience(src).build());
    }
    private void sendMsg(CommandSource src, String key, Map<String,String> ph) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key)
                .withPlaceholders(ph)
                .forAudience(src)
                .build());
    }
    private void sendLine(Player p, String key, Map<String,String> ph) {
        p.sendMessage(feature.getLocalizationHandler().getMessage(key)
                .withPlaceholders(ph)
                .forAudience(p)
                .build());
    }

    private PlayerEntity getEntity(Player p) {
        return svc.getPlayer(p.getUniqueId().toString()).orElseThrow();
    }

    private PlayerEntity resolvePlayer(String name) {
        return feature.getOrm().runInTransaction(s ->
                s.createQuery("FROM PlayerEntity WHERE username = :u",
                                PlayerEntity.class)
                        .setParameter("u", name)
                        .uniqueResultOptional()).orElse(null);
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation inv) {
        String[] a = inv.arguments();
        List<String> subs = List.of(
                "list","info","add","remove","accept","deny",
                "acceptall","denyall","server","requests","block","unblock",
                "disable","enable"
        );

        // no args yet (just “/friend␣”) or empty first arg → show everything
        if (a.length == 0 || (a.length == 1 && a[0].isEmpty())) {
            return CompletableFuture.completedFuture(subs);
        }

        // filtering for first argument
        if (a.length == 1) {
            String partial = a[0].toLowerCase(Locale.ROOT);
            return CompletableFuture.completedFuture(
                    subs.stream()
                            .filter(s -> s.startsWith(partial))
                            .collect(Collectors.toList())
            );
        }

        // for a second argument, suggest player names
        if (a.length == 2) {
            String partial = a[1].toLowerCase(Locale.ROOT);
            return CompletableFuture.completedFuture(
                    feature.getPlugin().getProxy().getAllPlayers().stream()
                            .map(Player::getUsername)
                            .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                            .collect(Collectors.toList())
            );
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
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
