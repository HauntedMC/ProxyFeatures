package nl.hauntedmc.proxyfeatures.features.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.hauntedmc.proxyfeatures.features.queue.model.EnqueueDecision;
import nl.hauntedmc.proxyfeatures.features.queue.model.QueueEntry;
import nl.hauntedmc.proxyfeatures.features.queue.model.ServerQueue;
import nl.hauntedmc.proxyfeatures.features.queue.model.ServerStatus;
import nl.hauntedmc.proxyfeatures.features.queue.util.PriorityResolver;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class QueueManager {

    private final Queue feature;
    private final ProxyServer proxy;
    private final Logger logger;

    private final Map<String, ServerQueue> queues = new ConcurrentHashMap<>();
    private final Map<String, ServerStatus> statusCache = new ConcurrentHashMap<>();

    // One-shot bypass tickets for players being advanced by the queue.
    private final Map<UUID, String> advanceTickets = new ConcurrentHashMap<>();

    // Tracks which actionbar message to show next for each queued player (0..2).
    private final Map<UUID, Integer> actionbarCycle = new ConcurrentHashMap<>();

    private final PriorityResolver priorityResolver;

    private ScheduledTask pollTask;
    private ScheduledTask updateTask;

    public QueueManager(Queue feature, Logger logger) {
        this.feature = feature;
        this.proxy = feature.getPlugin().getProxy();
        this.logger = logger;
        this.priorityResolver = new PriorityResolver();
        initializeQueuesFromConfig();
    }

    private void initializeQueuesFromConfig() {
        Object list = feature.getConfigHandler().getSetting("servers-whitelist");
        if (list instanceof List<?> servers) {
            for (Object o : servers) {
                String name = String.valueOf(o).toLowerCase(Locale.ROOT);
                queues.put(name, new ServerQueue(name));
            }
        }
    }

    // ------------------------------------------------------------
    // Scheduling
    // ------------------------------------------------------------
    public void startSchedulers(Duration pollEvery, Duration updateEvery) {
        pollTask = feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
            try {
                tick();
            } catch (Throwable t) {
                logger.error("Queue poll tick failed", t);
            }
        }, pollEvery, pollEvery);

        updateTask = feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
            try {
                sendActionBarUpdates();
            } catch (Throwable t) {
                logger.error("Queue actionbar tick failed", t);
            }
        }, updateEvery, updateEvery);
    }

    public void shutdown() {
        if (pollTask != null) feature.getLifecycleManager().getTaskManager().cancelTask(pollTask);
        if (updateTask != null) feature.getLifecycleManager().getTaskManager().cancelTask(updateTask);
        queues.clear();
        statusCache.clear();
        advanceTickets.clear();
        actionbarCycle.clear();
    }

    // ------------------------------------------------------------
    // Core loop: ping & drain
    // ------------------------------------------------------------
    private void tick() {
        // Refresh pings for all whitelisted servers
        List<CompletableFuture<Void>> pings = new ArrayList<>();
        for (String server : queues.keySet()) {
            RegisteredServer rs = proxy.getServer(server).orElse(null);
            if (rs == null) continue;
            pings.add(rs.ping().handle((ping, err) -> {
                if (err != null || ping == null) {
                    statusCache.put(server, ServerStatus.offline());
                } else {
                    ServerPing.Players players = ping.getPlayers().orElse(null);
                    int online = players != null ? players.getOnline() : -1;
                    int max = players != null ? players.getMax() : -1;
                    statusCache.put(server, ServerStatus.online(online, max));
                }
                return null;
            }));
        }
        CompletableFuture.allOf(pings.toArray(CompletableFuture[]::new)).join();

        // Drain queues where capacity exists
        for (Map.Entry<String, ServerQueue> e : queues.entrySet()) {
            String server = e.getKey();
            ServerStatus st = statusCache.get(server);
            if (st == null || !st.isOnline()) continue;

            int capacity = Math.max(0, st.maxPlayers - st.onlinePlayers);
            if (capacity == 0) continue;
            drain(server, capacity);
        }

        // Expire grace reservations
        expireGraces();
    }

    private void drain(@NotNull String serverName, int slots) {
        ServerQueue queue = queues.get(serverName);
        if (queue == null) return;
        RegisteredServer target = proxy.getServer(serverName).orElse(null);
        if (target == null) return;

        for (int i = 0; i < slots; i++) {
            QueueEntry next = queue.pollNextConnectable();
            if (next == null) break;

            Player player = proxy.getPlayer(next.playerId()).orElse(null);
            if (player == null) {
                continue;
            }

            // Grant a one-shot bypass so ServerPreConnectEvent won't deny this internal connect.
            grantAdvanceTicket(player.getUniqueId(), serverName);

            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.advance.now_connecting")
                    .withPlaceholders(Map.of("server", serverName))
                    .forAudience(player)
                    .build());

            feature.getLifecycleManager().getTaskManager().scheduleDelayedTask(() -> player.createConnectionRequest(target).connect().thenAccept(res -> {
                if (!res.isSuccessful()) {
                    res.getReasonComponent().ifPresent(component -> {
                        String reason = LegacyComponentSerializer.legacyAmpersand().serialize(component);
                        player.sendMessage(feature.getLocalizationHandler()
                                .getMessage("queue.join.connection_failure")
                                .withPlaceholders(Map.of("server", serverName, "reason", reason))
                                .forAudience(player)
                                .build());
                    });
                    // We intentionally do NOT requeue automatically to avoid duplicates / loops.
                } else {
                    queue.clearReservation(next.playerId());
                    consumeAdvanceTicket(player.getUniqueId(), serverName);
                    actionbarCycle.remove(player.getUniqueId());
                }
            }), Duration.ofSeconds(3));
        }
    }

    private void expireGraces() {
        queues.values().forEach(ServerQueue::expireGraces);
    }

    /**
     * Rotate actionbar messages for all queued players.
     * We send (in order): status with position, hint to leave, store/rank hint.
     * The rotation index is tracked per player and advanced on each tick.
     */
    private void sendActionBarUpdates() {
        Set<UUID> active = new HashSet<>();

        for (ServerQueue q : queues.values()) {
            q.forEachIndexed((idx, entry) -> {
                UUID id = entry.playerId();
                active.add(id);

                Player p = proxy.getPlayer(id).orElse(null);
                if (p == null) return;

                int next = (actionbarCycle.getOrDefault(id, -1) + 1) % 6;
                actionbarCycle.put(id, next);

                switch (next) {
                    case 0, 1 -> p.sendActionBar(feature.getLocalizationHandler().getMessage("queue.actionbar.status")
                            .withPlaceholders(Map.of(
                                    "server", q.serverName(),
                                    "position", String.valueOf(idx + 1)
                            ))
                            .forAudience(p)
                            .build());
                    case 2, 3 -> p.sendActionBar(feature.getLocalizationHandler()
                            .getMessage("queue.actionbar.leave")
                            .forAudience(p)
                            .build());
                    case 4, 5 -> p.sendActionBar(feature.getLocalizationHandler()
                            .getMessage("queue.actionbar.rank")
                            .forAudience(p)
                            .build());
                }
            });
        }

        // Cleanup cycle state for players no longer queued
        actionbarCycle.keySet().retainAll(active);
    }

    // ------------------------------------------------------------
    // API for listeners & commands
    // ------------------------------------------------------------
    public boolean isServerQueued(String server) {
        return queues.containsKey(server.toLowerCase(Locale.ROOT));
    }

    public Optional<ServerQueue> getQueue(String server) {
        return Optional.ofNullable(queues.get(server.toLowerCase(Locale.ROOT)));
    }

    public ServerStatus getStatus(String server) {
        return statusCache.getOrDefault(server.toLowerCase(Locale.ROOT), ServerStatus.unknown());
    }

    public int resolvePriority(Player player) {
        return priorityResolver.resolve(player);
    }

    public boolean hasBypass(Player player) {
        return player.hasPermission("queue.bypass");
    }

    public EnqueueDecision handlePreConnect(Player player, String targetServer) {
        String server = targetServer.toLowerCase(Locale.ROOT);
        if (!isServerQueued(server)) {
            return EnqueueDecision.ALLOW;
        }

        if (consumeAdvanceTicket(player.getUniqueId(), server)) {
            return EnqueueDecision.ALLOW;
        }

        if (hasBypass(player)) {
            return EnqueueDecision.ALLOW_BYPASS;
        }

        ServerStatus st = getStatus(server);
        boolean isFull = st.isOnline() && st.onlinePlayers >= st.maxPlayers;

        if (!isFull) {
            return EnqueueDecision.ALLOW;
        }

        int priority = resolvePriority(player);
        ServerQueue queue = queues.get(server);

        Optional<String> existing = findQueueOf(player.getUniqueId());
        if (existing.isPresent()) {
            String from = existing.get();
            if (from.equals(server)) {
                int pos = queue.positionOf(player.getUniqueId()).orElse(0);
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("queue.join.already_in_queue")
                        .withPlaceholders(Map.of("server", server, "position", String.valueOf(pos + 1)))
                        .forAudience(player)
                        .build());
            } else {
                queues.get(from).remove(player.getUniqueId());
                queue.enqueue(player.getUniqueId(), priority);
                int posAfterMove = queue.positionOf(player.getUniqueId()).orElse(0);
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("queue.join.moved_between_queues")
                        .withPlaceholders(Map.of("server", server, "position", String.valueOf(posAfterMove + 1)))
                        .forAudience(player)
                        .build());
            }
            return EnqueueDecision.DENY_QUEUED;
        }

        // New entry; tell them they're queued (no position in chat anymore).
        queue.enqueue(player.getUniqueId(), priority);
        int position = queue.positionOf(player.getUniqueId()).orElse(0) + 1;
        player.sendMessage(feature.getLocalizationHandler()
                .getMessage("queue.join.denied.full")
                .withPlaceholders(Map.of("server", server, "position", String.valueOf(position)))
                .forAudience(player)
                .build());

        int grace = ((Number) feature.getConfigHandler().getSetting("grace-seconds")).intValue();
        player.sendMessage(feature.getLocalizationHandler()
                .getMessage("queue.grace.active")
                .withPlaceholders(Map.of("seconds", String.valueOf(grace)))
                .forAudience(player)
                .build());

        // Initialize actionbar rotation index (optional; first tick will advance anyway)
        actionbarCycle.putIfAbsent(player.getUniqueId(), -1);

        return EnqueueDecision.DENY_QUEUED;
    }

    public void onDisconnect(UUID playerId) {
        findQueueOf(playerId).ifPresent(server -> queues.get(server).startGrace(
                playerId,
                Duration.ofSeconds(((Number) feature.getConfigHandler().getSetting("grace-seconds")).intValue())
        ));
        advanceTickets.remove(playerId);
        actionbarCycle.remove(playerId);
    }

    public void onPostConnect(Player player, String newServer) {
        findQueueOf(player.getUniqueId()).ifPresent(server -> {
            if (server.equalsIgnoreCase(newServer)) {
                queues.get(server).remove(player.getUniqueId());
            }
        });
        advanceTickets.remove(player.getUniqueId());
        actionbarCycle.remove(player.getUniqueId());
    }

    public Optional<String> findQueueOf(UUID playerId) {
        for (Map.Entry<String, ServerQueue> e : queues.entrySet()) {
            if (e.getValue().contains(playerId)) return Optional.of(e.getKey());
        }
        return Optional.empty();
    }

    // -------- advance ticket helpers --------
    private void grantAdvanceTicket(UUID playerId, String server) {
        advanceTickets.put(playerId, server.toLowerCase(Locale.ROOT));
    }

    public boolean consumeAdvanceTicket(UUID playerId, String server) {
        String s = advanceTickets.get(playerId);
        if (s != null && s.equalsIgnoreCase(server)) {
            advanceTickets.remove(playerId);
            return true;
        }
        return false;
    }
}
