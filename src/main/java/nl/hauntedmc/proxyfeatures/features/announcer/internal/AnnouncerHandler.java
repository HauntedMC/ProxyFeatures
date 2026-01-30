package nl.hauntedmc.proxyfeatures.features.announcer.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.announcer.Announcer;
import nl.hauntedmc.proxyfeatures.framework.lifecycle.FeatureTaskManager;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class AnnouncerHandler {

    private static final int MIN_INTERVAL_SECONDS = 1;
    private static final int MIN_DELAY_SECONDS = 0;

    private final Announcer feature;
    private final FeatureLogger logger;
    private final FeatureTaskManager taskManager;
    private final AnnouncerRegistry registry;

    private final Object lock = new Object();
    private volatile ScheduledTask scheduledTask;

    private final Settings settings;
    private int[] baseWeights = new int[0];
    private long baseWeightTotal = 0;

    private int seqIndex = 0;
    private int seqRepeatsLeft = 0;

    private int[] shuffleRemaining = null;
    private long shuffleRemainingTotal = 0;

    public AnnouncerHandler(Announcer feature) {
        this.feature = feature;
        this.logger = feature.getLogger(); // ✅ requested change
        this.taskManager = feature.getLifecycleManager().getTaskManager();
        this.registry = new AnnouncerRegistry(feature);
        this.settings = readSettings();
        rebuildWeightsAndResetState();
    }

    public void start() {
        synchronized (lock) {
            schedule(settings);
        }
    }

    public void stop() {
        synchronized (lock) {
            cancelTask();
        }
    }

    private void schedule(Settings s) {
        cancelTask();

        if (baseWeights.length == 0 || baseWeightTotal <= 0) {
            logger.warn("No valid announcements loaded; not scheduling task.");
            return;
        }

        scheduledTask = taskManager.scheduleRepeatingTask(
                this::announceOnce,
                s.initialDelay(),
                s.interval()
        );
    }

    private void cancelTask() {
        ScheduledTask t = this.scheduledTask;
        this.scheduledTask = null;
        if (t != null) {
            taskManager.cancelTask(t);
        }
    }

    private void announceOnce() {
        final String messageKey;
        final AudienceFilter audience;
        final Mode mode;

        synchronized (lock) {
            if (baseWeights.length == 0 || baseWeightTotal <= 0) return;

            mode = settings.mode();
            audience = settings.audience();
            messageKey = nextMessageKey(mode);
        }

        for (Player player : ProxyFeatures.getProxyInstance().getAllPlayers()) {
            if (!audience.allows(player)) continue;

            Component message = feature.getLocalizationHandler()
                    .getMessage(messageKey)
                    .forAudience(player)
                    .build();

            player.sendMessage(message);
        }
    }

    private String nextMessageKey(Mode mode) {
        List<AnnouncerRegistry.Announcement> list = registry.announcements();
        if (list.isEmpty()) return AnnouncerRegistry.FALLBACK_KEY;

        return switch (mode) {
            case SEQUENTIAL -> nextSequential(list);
            case SHUFFLE -> nextShuffle(list);
            case WEIGHTED_RANDOM -> nextWeightedRandom(list);
        };
    }

    private String nextSequential(List<AnnouncerRegistry.Announcement> list) {
        if (seqRepeatsLeft <= 0) {
            if (seqIndex >= list.size()) seqIndex = 0;
            seqRepeatsLeft = Math.max(1, list.get(seqIndex).weight());
        }

        AnnouncerRegistry.Announcement cur = list.get(seqIndex);
        seqRepeatsLeft--;

        if (seqRepeatsLeft == 0) {
            seqIndex++;
            if (seqIndex >= list.size()) seqIndex = 0;
        }

        return cur.key();
    }

    private String nextShuffle(List<AnnouncerRegistry.Announcement> list) {
        ensureShuffleBag(list);

        int idx = pickWeightedIndex(shuffleRemaining, shuffleRemainingTotal);
        if (idx < 0) {
            shuffleRemainingTotal = 0;
            return AnnouncerRegistry.FALLBACK_KEY;
        }

        shuffleRemaining[idx]--;
        shuffleRemainingTotal--;

        if (shuffleRemainingTotal <= 0) {
            shuffleRemainingTotal = 0; // reshuffle on next call
        }

        return list.get(idx).key();
    }

    private String nextWeightedRandom(List<AnnouncerRegistry.Announcement> list) {
        int idx = pickWeightedIndex(baseWeights, baseWeightTotal);
        if (idx < 0 || idx >= list.size()) return AnnouncerRegistry.FALLBACK_KEY;
        return list.get(idx).key();
    }

    private void ensureShuffleBag(List<AnnouncerRegistry.Announcement> list) {
        if (shuffleRemaining == null
                || shuffleRemaining.length != list.size()
                || shuffleRemainingTotal <= 0) {

            shuffleRemaining = new int[list.size()];
            long total = 0;
            for (int i = 0; i < list.size(); i++) {
                int w = Math.max(1, list.get(i).weight());
                shuffleRemaining[i] = w;
                total += w;
            }
            shuffleRemainingTotal = total;
        }
    }

    private int pickWeightedIndex(int[] weights, long totalWeight) {
        if (weights == null || weights.length == 0) return -1;
        if (totalWeight <= 0) return -1;

        long r = ThreadLocalRandom.current().nextLong(totalWeight);
        long cumulative = 0;

        for (int i = 0; i < weights.length; i++) {
            int w = weights[i];
            if (w <= 0) continue;
            cumulative += w;
            if (r < cumulative) return i;
        }

        for (int i = weights.length - 1; i >= 0; i--) {
            if (weights[i] > 0) return i;
        }
        return -1;
    }

    private void rebuildWeightsAndResetState() {
        List<AnnouncerRegistry.Announcement> list = registry.announcements();

        baseWeights = new int[list.size()];
        long total = 0;
        for (int i = 0; i < list.size(); i++) {
            int w = Math.max(1, list.get(i).weight());
            baseWeights[i] = w;
            total += w;
        }
        baseWeightTotal = total;

        seqIndex = 0;
        seqRepeatsLeft = 0;
        shuffleRemaining = null;
        shuffleRemainingTotal = 0;
    }

    private Settings readSettings() {
        int intervalSeconds = feature.getConfigHandler().get("message_interval", Integer.class, 200);
        if (intervalSeconds < MIN_INTERVAL_SECONDS) intervalSeconds = MIN_INTERVAL_SECONDS;

        int delaySeconds = feature.getConfigHandler().get("initial_delay", Integer.class, 30);
        if (delaySeconds < MIN_DELAY_SECONDS) delaySeconds = MIN_DELAY_SECONDS;

        String modeStr = feature.getConfigHandler().get("mode", String.class, "SHUFFLE");
        Mode mode = parseMode(modeStr);

        String perm = feature.getConfigHandler().get("audience.permission", String.class, "");
        List<String> allow = feature.getConfigHandler().getList("audience.servers", String.class, List.of());
        List<String> deny = feature.getConfigHandler().getList("audience.exclude_servers", String.class, List.of());

        AudienceFilter audience = new AudienceFilter(perm, allow, deny);

        return new Settings(
                Duration.ofSeconds(intervalSeconds),
                Duration.ofSeconds(delaySeconds),
                mode,
                audience
        );
    }

    private Mode parseMode(String raw) {
        if (raw == null || raw.isBlank()) return Mode.SHUFFLE;
        String s = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return Mode.valueOf(s);
        } catch (IllegalArgumentException ex) {
            logger.warn("Announcer: invalid mode '" + raw + "'. Using SHUFFLE.");
            return Mode.SHUFFLE;
        }
    }

    public enum Mode {
        SEQUENTIAL,
        SHUFFLE,
        WEIGHTED_RANDOM
    }

    public record Settings(Duration interval, Duration initialDelay, Mode mode, AudienceFilter audience) {
    }

    public static final class AudienceFilter {

        private final String permission;
        private final Set<String> allowServers;
        private final Set<String> denyServers;

        public AudienceFilter(String permission, List<String> allowServers, List<String> denyServers) {
            this.permission = normalizePermission(permission);
            this.allowServers = normalizeServerSet(allowServers);
            this.denyServers = normalizeServerSet(denyServers);
        }

        public boolean allows(Player player) {
            if (player == null) return false;

            if (!permission.isEmpty() && !player.hasPermission(permission)) {
                return false;
            }

            String server = currentServerName(player);
            if (!allowServers.isEmpty()) {
                if (server.isEmpty() || !allowServers.contains(server)) return false;
            }

            return server.isEmpty() || !denyServers.contains(server);
        }

        private static String currentServerName(Player player) {
            return player.getCurrentServer()
                    .map(ServerConnection::getServerInfo)
                    .map(info -> info.getName() == null ? "" : info.getName())
                    .map(AudienceFilter::normalizeServerName)
                    .orElse("");
        }

        private static String normalizePermission(String perm) {
            if (perm == null) return "";
            String p = perm.trim();
            return p.isEmpty() ? "" : p;
        }

        private static Set<String> normalizeServerSet(List<String> input) {
            if (input == null || input.isEmpty()) return Set.of();
            Set<String> out = new HashSet<>();
            for (String s : input) {
                String n = normalizeServerName(s);
                if (!n.isEmpty()) out.add(n);
            }
            return Set.copyOf(out);
        }

        private static String normalizeServerName(String name) {
            if (name == null) return "";
            String n = name.trim().toLowerCase(Locale.ROOT);
            return n.isEmpty() ? "" : n;
        }
    }
}
