package nl.hauntedmc.proxyfeatures.features.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import com.velocitypowered.api.scheduler.ScheduledTask;
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
    // Key: player UUID, Value: lower-cased server name.
    private final Map<UUID, String> advanceTickets = new ConcurrentHashMap<>();

    private final PriorityResolver priorityResolver;

    // Task handles so we can cancel on shutdown
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
                sendGentleUpdates();
            } catch (Throwable t) {
                logger.error("Queue update tick failed", t);
            }
        }, updateEvery, updateEvery);
    }

    public void shutdown() {
        if (pollTask != null) feature.getLifecycleManager().getTaskManager().cancelTask(pollTask);
        if (updateTask != null) feature.getLifecycleManager().getTaskManager().cancelTask(updateTask);
        queues.clear();
        statusCache.clear();
        advanceTickets.clear();
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
                // Offline: grace expiry will remove later if needed
                continue;
            }

            // Grant a one-shot bypass so ServerPreConnectEvent won't deny this internal connect.
            grantAdvanceTicket(player.getUniqueId(), serverName);

            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.advance.now_connecting")
                    .withPlaceholders(Map.of("server", serverName))
                    .forAudience(player)
                    .build());

            player.createConnectionRequest(target).connect().thenAccept(res -> {
                if (!res.isSuccessful()) {
                    // Failed (race, server filled, or temp issue). Try again soon, keeping order.
                    // IMPORTANT: make requeue idempotent (ServerQueue removes duplicates).
                    queue.requeueFront(next);
                } else {
                    // Success: clear any reservation & drop the ticket if still present.
                    queue.clearReservation(next.playerId());
                    consumeAdvanceTicket(player.getUniqueId(), serverName);
                }
            });
        }
    }

    private void expireGraces() {
        queues.values().forEach(ServerQueue::expireGraces);
    }

    private void sendGentleUpdates() {
        for (ServerQueue q : queues.values()) {
            q.forEachIndexed((idx, entry) -> {
                Player p = proxy.getPlayer(entry.playerId()).orElse(null);
                if (p == null) return;
                p.sendMessage(feature.getLocalizationHandler()
                        .getMessage("queue.status.line")
                        .withPlaceholders(Map.of(
                                "server", q.serverName(),
                                "position", String.valueOf(idx + 1)
                        ))
                        .forAudience(p)
                        .build());
            });
        }
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

    /**
     * Decide whether to allow direct connect or place into the queue.
     * When auto-activate is true, queuing only happens if the target is currently full.
     */
    public EnqueueDecision handlePreConnect(Player player, String targetServer) {
        String server = targetServer.toLowerCase(Locale.ROOT);
        if (!isServerQueued(server)) {
            return EnqueueDecision.ALLOW; // not managed by queue
        }

        // If this player is being advanced by the queue for this server, allow once.
        if (consumeAdvanceTicket(player.getUniqueId(), server)) {
            return EnqueueDecision.ALLOW;
        }

        if (hasBypass(player)) {
            return EnqueueDecision.ALLOW_BYPASS;
        }

        boolean auto = Boolean.TRUE.equals(feature.getConfigHandler().getSetting("auto-activate"));
        ServerStatus st = getStatus(server);
        boolean isFull = st.isOnline() && st.onlinePlayers >= st.maxPlayers;

        if (auto && !isFull) {
            return EnqueueDecision.ALLOW;
        }

        // Enqueue
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

        QueueEntry entry = queue.enqueue(player.getUniqueId(), priority);
        int pos = queue.positionOf(entry.playerId()).orElse(0);
        String key = pos == 0 ? "queue.join.denied.full" : "queue.join.denied.full_withpos";
        player.sendMessage(feature.getLocalizationHandler()
                .getMessage(key)
                .withPlaceholders(Map.of("server", server, "position", String.valueOf(pos + 1)))
                .forAudience(player)
                .build());

        int grace = ((Number) feature.getConfigHandler().getSetting("grace-seconds")).intValue();
        player.sendMessage(feature.getLocalizationHandler()
                .getMessage("queue.grace.active")
                .withPlaceholders(Map.of("seconds", String.valueOf(grace)))
                .forAudience(player)
                .build());

        return EnqueueDecision.DENY_QUEUED;
    }

    public void onDisconnect(UUID playerId) {
        findQueueOf(playerId).ifPresent(server -> queues.get(server).startGrace(
                playerId,
                Duration.ofSeconds(((Number) feature.getConfigHandler().getSetting("grace-seconds")).intValue())
        ));
        // Clean up any stale ticket
        advanceTickets.remove(playerId);
    }

    public void onPostConnect(Player player, String newServer) {
        findQueueOf(player.getUniqueId()).ifPresent(server -> {
            if (server.equalsIgnoreCase(newServer)) {
                queues.get(server).remove(player.getUniqueId());
            }
        });
        advanceTickets.remove(player.getUniqueId());
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
