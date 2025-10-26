package nl.hauntedmc.proxyfeatures.features.friends.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.api.APIRegistry;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.proxyfeatures.api.util.tools.Paginator;
import nl.hauntedmc.proxyfeatures.features.friends.Friends;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendStatus;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendsService;
import nl.hauntedmc.proxyfeatures.features.friends.entity.PlayerRef;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.VanishAPI;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class FriendCommand implements FeatureCommand {

    private final Friends feature;
    private final FriendsService svc;
    private final Optional<VanishAPI> vanishApi;

    public FriendCommand(Friends feature) {
        this.feature = feature;
        this.svc = feature.getService();
        this.vanishApi = APIRegistry.get(VanishAPI.class);
    }

    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] args = inv.arguments();

        if (!(src instanceof Player player)) {
            sendMsg(src, "friend.player_only");
            return;
        }

        // Support `/fr` (page 1) and `/fr <page>` for the online list view.
        if (args.length == 0 || isPositiveInt(args[0])) {
            int page = 1;
            if (args.length >= 1 && isPositiveInt(args[0])) {
                page = Integer.parseInt(args[0]);
            }
            showOnlineList(player, page);
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "list" -> list(player, args);
            case "add" -> add(player, args);
            case "remove" -> remove(player, args);
            case "accept" -> accept(player, args);
            case "deny" -> deny(player, args);
            case "cancel" -> cancel(player, args);
            case "acceptall" -> acceptAll(player);
            case "denyall" -> denyAll(player);
            case "server" -> connect(player, args);
            case "requests" -> requests(player, args);
            case "block" -> block(player, args);
            case "unblock" -> unblock(player, args);
            case "disable" -> disable(player);
            case "enable" -> enable(player);
            default -> sendMsg(src, "friend.usage");
        }
    }

    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.friend.command.friends");
    }

    /**
     * Base `/fr` (and `/fr <page>`) – online friends only, paginated.
     */
    private void showOnlineList(Player p, int requestedPage) {
        PlayerRef me = getRef(p);
        if (!svc.getOrCreateSettings(me).isEnabled()) {
            sendMsg(p, "friend.mode_disabled");
            return;
        }

        // Build online list (non-vanished), sort A–Z
        var friendSnaps = svc.acceptedFriendSnapshots(me);
        List<Component> entries = new ArrayList<>();

        List<String> onlineNames = new ArrayList<>();
        Map<String, String> onlineSuffix = new HashMap<>();

        for (var snap : friendSnaps) {
            UUID fid = UUID.fromString(snap.uuid());
            feature.getPlugin().getProxy().getPlayer(fid)
                    .filter(this::notVanished)
                    .ifPresent(pl -> {
                        String name = pl.getUsername();
                        onlineNames.add(name);
                        String suffix = pl.getCurrentServer()
                                .map(ServerConnection::getServer)
                                .map(rs -> " &7(&f" + rs.getServerInfo().getName() + "&7)")
                                .orElse("");
                        onlineSuffix.put(name, suffix);
                    });
        }

        onlineNames.sort(Comparator.comparing(s -> s.toLowerCase(Locale.ROOT)));

        for (String name : onlineNames) {
            entries.add(feature.getLocalizationHandler()
                    .getMessage("friend.online_entry")
                    .with("player", name)
                    .with("server", onlineSuffix.getOrDefault(name, "?"))
                    .forAudience(p)
                    .build());
        }

        if (entries.isEmpty()) {
            sendMsg(p, "friend.no_friends_online");
        } else {
            sendMsg(p, "friend.online_header");

            int pageSize = getListPageSize();
            var page = Paginator.paginate(entries, requestedPage, pageSize);

            for (Component c : page.items()) {
                p.sendMessage(c);
            }

            // Footer for consistency with /fr list
            p.sendMessage(feature.getLocalizationHandler()
                    .getMessage("friend.list.page")
                    .with("page", String.valueOf(page.page()))
                    .with("pages", String.valueOf(page.totalPages()))
                    .with("size", String.valueOf(page.pageSize()))
                    .forAudience(p)
                    .build());
        }

        // Pending requests hint (unchanged)
        int pending = svc.incomingRequestUsernames(me).size();
        if (pending > 0) {
            sendMsg(p, "friend.pending_requests",
                    Map.of("count", String.valueOf(pending)));
        }
    }

    /**
     * `/fr list [page]` – online first then offline, both A–Z, paginated.
     */
    private void list(Player p, String[] args) {
        PlayerRef me = getRef(p);

        var friends = svc.acceptedFriendSnapshots(me);
        int total = friends.size();

        // Build online/offline groups with display info
        List<String> onlineNames = new ArrayList<>();
        Map<String, String> onlineSuffix = new HashMap<>();
        List<String> offlineNames = new ArrayList<>();

        for (var snap : friends) {
            UUID fid = UUID.fromString(snap.uuid());
            feature.getPlugin().getProxy().getPlayer(fid)
                    .filter(this::notVanished)
                    .ifPresentOrElse(pl -> {
                        String name = pl.getUsername();
                        onlineNames.add(name);
                        String suffix = pl.getCurrentServer()
                                .map(ServerConnection::getServer)
                                .map(rs -> " &7(&f" + rs.getServerInfo().getName() + "&7)")
                                .orElse("");
                        onlineSuffix.put(name, suffix);
                    }, () -> offlineNames.add(snap.username()));
        }

        // Sort each subgroup alphabetically (case-insensitive)
        Comparator<String> byName = Comparator.comparing(s -> s.toLowerCase(Locale.ROOT));
        onlineNames.sort(byName);
        offlineNames.sort(byName);

        // Compose final ordered list of formatted lines (Components)
        List<Component> lines = new ArrayList<>(total);
        for (String name : onlineNames) {
            String suffix = onlineSuffix.getOrDefault(name, "");
            String status = "&a● ";
            lines.add(feature.getLocalizationHandler()
                    .getMessage("friend.list.entry")
                    .with("status", status)
                    .with("player", name + suffix)
                    .forAudience(p)
                    .build());
        }
        for (String name : offlineNames) {
            String status = "&c● ";
            lines.add(feature.getLocalizationHandler()
                    .getMessage("friend.list.entry")
                    .with("status", status)
                    .with("player", name)
                    .forAudience(p)
                    .build());
        }

        long onlineCount = onlineNames.size();
        p.sendMessage(feature.getLocalizationHandler()
                .getMessage("friend.list.header")
                .with("online", String.valueOf(onlineCount))
                .with("total", String.valueOf(total))
                .forAudience(p)
                .build());

        // Pagination
        int pageSize = getListPageSize();
        int requestedPage = 1;
        if (args.length >= 2) {
            try {
                requestedPage = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        var page = Paginator.paginate(lines, requestedPage, pageSize);

        // Output page items
        for (Component line : page.items()) {
            p.sendMessage(line);
        }

        // Footer with page info
        p.sendMessage(feature.getLocalizationHandler()
                .getMessage("friend.list.page")
                .with("page", String.valueOf(page.page()))
                .with("pages", String.valueOf(page.totalPages()))
                .with("size", String.valueOf(page.pageSize()))
                .forAudience(p)
                .build());
    }

    private void add(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerRef me = getRef(p);

        PlayerRef target = resolvePlayerRef(args[1]);
        if (target == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (target.id().equals(me.id())) {
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
            sendMsg(p, "friend.you_blocked_target", Map.of("player", target.username()));
            return;
        }
        if (svc.isBlockedByTarget(me, target)) {
            sendMsg(p, "friend.blocked_by_target", Map.of("player", target.username()));
            return;
        }

        // Cached status checks (both directions, scalar)
        Optional<FriendStatus> meToTarget = svc.relationStatus(me, target);
        Optional<FriendStatus> targetToMe = svc.relationStatus(target, me);

        if (meToTarget.isPresent()) {
            FriendStatus st = meToTarget.get();
            if (st == FriendStatus.ACCEPTED) {
                sendMsg(p, "friend.already_friends");
                return;
            }
            if (st == FriendStatus.PENDING) {
                sendMsg(p, "friend.request_exists");
                return;
            }
            if (st == FriendStatus.BLOCKED) {
                sendMsg(p, "friend.you_blocked_target", Map.of("player", target.username()));
                return;
            }
        }

        if (targetToMe.isPresent()) {
            FriendStatus st = targetToMe.get();
            if (st == FriendStatus.ACCEPTED) {
                sendMsg(p, "friend.already_friends");
                return;
            }
            if (st == FriendStatus.BLOCKED) {
                sendMsg(p, "friend.blocked_by_target", Map.of("player", target.username()));
                return;
            }
            if (st == FriendStatus.PENDING) {
                // Mutual request -> auto-accept atomically
                boolean ok = svc.acceptPending(target, me);
                if (ok) {
                    sendMsg(p, "friend.accepted", Map.of("player", target.username()));
                    feature.getPlugin().getProxy().getPlayer(UUID.fromString(target.uuid()))
                            .filter(this::notVanished)
                            .ifPresent(t -> sendMsg(t, "friend.accepted",
                                    Map.of("player", p.getUsername())));
                } else {
                    sendMsg(p, "friend.request_exists");
                }
                return;
            }
        }

        // Create outgoing pending via service (handles invalidation)
        boolean created = svc.createPending(me, target);
        if (!created) {
            sendMsg(p, "friend.request_exists");
            return;
        }

        sendMsg(p, "friend.add.sent", Map.of("player", target.username()));

        feature.getPlugin().getProxy().getPlayer(UUID.fromString(target.uuid()))
                .filter(this::notVanished)
                .ifPresent(t -> sendMsg(t, "friend.add.received",
                        Map.of("player", p.getUsername())));
    }

    private void remove(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerRef me = getRef(p);
        PlayerRef target = resolvePlayerRef(args[1]);
        if (target == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (target.id().equals(me.id())) {
            sendMsg(p, "friend.self");
            return;
        }

        boolean removed = svc.removeFriendship(me, target);
        if (removed) {
            sendMsg(p, "friend.removed", Map.of("player", target.username()));
        } else {
            sendMsg(p, "friend.not_friends");
        }
    }

    private void accept(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerRef me = getRef(p);
        PlayerRef from = resolvePlayerRef(args[1]);
        if (from == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (from.id().equals(me.id())) {
            sendMsg(p, "friend.self");
            return;
        }

        // Respect blocks
        if (svc.didIBlockTarget(me, from)) {
            sendMsg(p, "friend.you_blocked_target", Map.of("player", from.username()));
            return;
        }
        if (svc.isBlockedByTarget(me, from)) {
            sendMsg(p, "friend.blocked_by_target", Map.of("player", from.username()));
            return;
        }

        boolean ok = svc.acceptPending(from, me);
        if (ok) {
            sendMsg(p, "friend.accepted", Map.of("player", from.username()));
            feature.getPlugin().getProxy().getPlayer(UUID.fromString(from.uuid()))
                    .filter(this::notVanished)
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
        PlayerRef me = getRef(p);
        PlayerRef other = resolvePlayerRef(args[1]);
        if (other == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (other.id().equals(me.id())) {
            sendMsg(p, "friend.self");
            return;
        }

        // Incoming?
        boolean denied = svc.denyIncoming(me, other);
        if (denied) {
            sendMsg(p, "friend.denied", Map.of("player", other.username()));
            return;
        }

        // Outgoing pending? Then instruct to cancel.
        Optional<FriendStatus> outStatus = svc.relationStatus(me, other);
        if (outStatus.isPresent() && outStatus.get() == FriendStatus.PENDING) {
            sendMsg(p, "friend.cannot_deny_outgoing", Map.of("player", other.username()));
            return;
        }

        sendMsg(p, "friend.not_found");
    }

    private void cancel(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerRef me = getRef(p);
        PlayerRef target = resolvePlayerRef(args[1]);
        if (target == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (target.id().equals(me.id())) {
            sendMsg(p, "friend.self");
            return;
        }

        boolean ok = svc.cancelPending(me, target);
        if (ok) {
            sendMsg(p, "friend.cancelled", Map.of("player", target.username()));
        } else {
            sendMsg(p, "friend.not_found");
        }
    }

    private void acceptAll(Player p) {
        PlayerRef me = getRef(p);
        int n = svc.acceptAll(me);
        if (n == 0) {
            sendMsg(p, "friend.no_requests");
        } else {
            sendMsg(p, "friend.accepted_many", Map.of("count", String.valueOf(n)));
        }
    }

    private void denyAll(Player p) {
        PlayerRef me = getRef(p);
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
        PlayerRef me = getRef(p);
        PlayerRef target = resolvePlayerRef(args[1]);
        if (target == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (target.id().equals(me.id())) {
            sendMsg(p, "friend.self");
            return;
        }

        // Only need to know if accepted
        Optional<FriendStatus> st = svc.relationStatus(me, target);
        if (st.isEmpty() || st.get() != FriendStatus.ACCEPTED) {
            sendMsg(p, "friend.not_friends");
            return;
        }

        feature.getPlugin().getProxy().getPlayer(UUID.fromString(target.uuid()))
                .filter(this::notVanished)
                .ifPresentOrElse(t -> t.getCurrentServer().ifPresent(conn -> {
                    RegisteredServer srv = conn.getServer();
                    p.createConnectionRequest(srv).fireAndForget();
                    sendMsg(p, "friend.connected", Map.of("player", t.getUsername()));
                }), () -> sendMsg(p, "friend.not_online"));
    }

    /**
     * `/fr requests [page]` – incoming then outgoing, paginated.
     */
    private void requests(Player p, String[] args) {
        PlayerRef me = getRef(p);

        List<String> incoming = svc.incomingRequestUsernames(me);
        List<String> outgoing = svc.outgoingRequestUsernames(me);

        if (incoming.isEmpty() && outgoing.isEmpty()) {
            sendMsg(p, "friend.no_requests");
            return;
        }

        // Build combined list (incoming first, then outgoing)
        List<Component> lines = new ArrayList<>(incoming.size() + outgoing.size());
        for (String u : incoming) {
            lines.add(feature.getLocalizationHandler()
                    .getMessage("friend.requests.incoming_entry")
                    .with("player", u)
                    .forAudience(p)
                    .build());
        }
        for (String u : outgoing) {
            lines.add(feature.getLocalizationHandler()
                    .getMessage("friend.requests.outgoing_entry")
                    .with("player", u)
                    .forAudience(p)
                    .build());
        }

        sendMsg(p, "friend.requests_header");

        int pageSize = getListPageSize();
        int requestedPage = 1;
        if (args.length >= 2) {
            try {
                requestedPage = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        var page = Paginator.paginate(lines, requestedPage, pageSize);

        for (Component c : page.items()) {
            p.sendMessage(c);
        }

        // Footer reusing the same page message
        p.sendMessage(feature.getLocalizationHandler()
                .getMessage("friend.list.page")
                .with("page", String.valueOf(page.page()))
                .with("pages", String.valueOf(page.totalPages()))
                .with("size", String.valueOf(page.pageSize()))
                .forAudience(p)
                .build());
    }

    private void block(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerRef me = getRef(p);
        PlayerRef target = resolvePlayerRef(args[1]);
        if (target == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (target.id().equals(me.id())) {
            sendMsg(p, "friend.self");
            return;
        }

        // Already blocked by me?
        if (svc.didIBlockTarget(me, target)) {
            sendMsg(p, "friend.already_blocked");
            return;
        }

        svc.block(me, target);
        sendMsg(p, "friend.blocked", Map.of("player", target.username()));
    }

    private void unblock(Player p, String[] args) {
        if (args.length != 2) {
            sendMsg(p, "friend.usage");
            return;
        }
        PlayerRef me = getRef(p);
        PlayerRef target = resolvePlayerRef(args[1]);
        if (target == null) {
            sendMsg(p, "friend.not_found");
            return;
        }
        if (target.id().equals(me.id())) {
            sendMsg(p, "friend.self");
            return;
        }

        boolean ok = svc.unblock(me, target);
        if (ok) {
            sendMsg(p, "friend.unblocked", Map.of("player", target.username()));
        } else {
            sendMsg(p, "friend.not_blocked");
        }
    }

    private void disable(Player p) {
        svc.setEnabled(getRef(p), false);
        sendMsg(p, "friend.mode_now_disabled");
    }

    private void enable(Player p) {
        svc.setEnabled(getRef(p), true);
        sendMsg(p, "friend.mode_enabled");
    }

    private void sendMsg(CommandSource src, String key) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key)
                .forAudience(src).build());
    }

    private void sendMsg(CommandSource src, String key, Map<String, String> ph) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key)
                .withPlaceholders(MessagePlaceholders.of(ph)).forAudience(src).build());
    }

    private PlayerRef getRef(Player p) {
        return svc.getPlayer(p.getUniqueId().toString()).orElseThrow();
    }

    private PlayerRef resolvePlayerRef(String name) {
        // Use cache-backed, case-insensitive resolver
        return svc.resolvePlayerByUsernameCaseInsensitive(name).orElse(null);
    }

    private boolean notVanished(Player pl) {
        return vanishApi.map(api -> !api.isVanished(pl.getUniqueId())).orElse(true);
    }

    private int getListPageSize() {
        try {
            Object raw = feature.getConfigHandler().getSetting("list_page_size");
            int v;
            if (raw instanceof Number n) v = n.intValue();
            else if (raw instanceof String s) v = Integer.parseInt(s);
            else v = 10;
            return Math.max(1, v);
        } catch (Exception e) {
            return 10;
        }
    }

    private boolean isPositiveInt(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        try {
            return Integer.parseInt(s) >= 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

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

        // 0 or 1st argument: subcommand suggestions, or offer page numbers if user typed digits
        if (a.length == 0 || (a.length == 1 && a[0].isEmpty())) {
            return CompletableFuture.completedFuture(subs);
        }
        if (a.length == 1) {
            String token = a[0].toLowerCase(Locale.ROOT);
            // If numeric, suggest a few page options for the base view
            if (isPositiveInt(token)) {
                int size = getListPageSize();
                int total = svc.acceptedFriendSnapshots(getRef(p)).size(); // online subset size unknown here, use total for rough pages
                int pages = Math.max(1, (int) Math.ceil(total / (double) size));
                List<String> out = new ArrayList<>();
                for (int i = 1; i <= Math.min(20, pages); i++) out.add(String.valueOf(i));
                return CompletableFuture.completedFuture(out);
            }
            List<String> out = subs.stream()
                    .filter(s -> s.startsWith(token))
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(out);
        }

        // From here: a.length >= 2 -> name/page suggestions per subcommand
        String sub = a[0].toLowerCase(Locale.ROOT);
        String partial = a[1].toLowerCase(Locale.ROOT);

        PlayerRef me = getRef(p);

        // Helpers
        java.util.function.Predicate<String> startsWith = name ->
                name != null && name.toLowerCase(Locale.ROOT).startsWith(partial);

        if (sub.equals("list")) {
            int size = getListPageSize();
            int total = svc.acceptedFriendSnapshots(me).size();
            int pages = Math.max(1, (int) Math.ceil(total / (double) size));
            List<String> pageOpts = new ArrayList<>(pages);
            for (int i = 1; i <= pages && i <= 20; i++) pageOpts.add(String.valueOf(i));
            return CompletableFuture.completedFuture(
                    pageOpts.stream().filter(startsWith).toList()
            );
        }

        if (sub.equals("requests")) {
            int size = getListPageSize();
            int total = svc.incomingRequestUsernames(me).size() + svc.outgoingRequestUsernames(me).size();
            int pages = Math.max(1, (int) Math.ceil(total / (double) size));
            List<String> pageOpts = new ArrayList<>(pages);
            for (int i = 1; i <= pages && i <= 20; i++) pageOpts.add(String.valueOf(i));
            return CompletableFuture.completedFuture(
                    pageOpts.stream().filter(startsWith).toList()
            );
        }

        // Online & non-vanished players (usernames), excluding self
        List<Player> onlineNonVanished = feature.getPlugin().getProxy().getAllPlayers().stream()
                .filter(this::notVanished)
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
            case "acceptall":
            case "denyall":
            case "disable":
            case "enable":
            default:
                result = Collections.emptyList();
        }

        return CompletableFuture.completedFuture(result);
    }

    public String getName() {
        return "friends";
    }

    public String[] getAliases() {
        return new String[]{"fr", "friend"};
    }
}
