package nl.hauntedmc.proxyfeatures.features.votifier.internal;

import com.velocitypowered.api.scheduler.ScheduledTask;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteStatsEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.messaging.EventBusHandler;
import nl.hauntedmc.proxyfeatures.features.votifier.messaging.VoteMessage;
import nl.hauntedmc.proxyfeatures.features.votifier.model.Vote;
import nl.hauntedmc.proxyfeatures.features.votifier.server.VotifierServer;
import nl.hauntedmc.proxyfeatures.features.votifier.util.IpAccessList;
import nl.hauntedmc.proxyfeatures.features.votifier.util.RSAUtil;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class VotifierService {

    private final Votifier feature;

    private final ExecutorService worker;
    private final AtomicReference<VotifierConfig> configRef;

    private final EventBusHandler bus;
    private final VoteStatsService stats;
    private final VotifierLocalState localState;

    private volatile VotifierServer server;
    private volatile ScheduledTask resetTask;

    public VotifierService(Votifier feature, MessagingDataAccess redisBus, ORMContext orm) {
        this.feature = feature;

        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "votifier-worker");
            t.setDaemon(true);
            return t;
        });
        this.configRef = new AtomicReference<>(VotifierConfig.load(feature));

        this.bus = redisBus == null ? null : new EventBusHandler(feature, redisBus);
        this.stats = orm == null ? null : new VoteStatsService(feature, orm);
        this.localState = new VotifierLocalState(feature);
    }

    public void start() {
        reloadInternal();
    }

    private void reloadInternal() {
        VotifierConfig cfg = VotifierConfig.load(feature);
        configRef.set(cfg);

        stopServer();
        startServer(cfg);

        scheduleMonthlyReset(cfg);
    }

    public void shutdown() {
        cancelResetTask();
        stopServer();

        worker.shutdownNow();
        try {
            worker.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }

    public boolean isRunning() {
        VotifierServer s = server;
        return s != null && s.isRunning();
    }

    public String currentHost() {
        VotifierServer s = server;
        return s != null ? s.getHost() : configRef.get().host();
    }

    public int currentPort() {
        VotifierServer s = server;
        return s != null ? s.getPort() : configRef.get().port();
    }

    public boolean isRedisEnabled() {
        VotifierConfig cfg = configRef.get();
        return cfg.redisEnabled() && bus != null;
    }

    public boolean isStatsEnabled() {
        return configRef.get().statsEnabled() && stats != null;
    }

    public YearMonth currentYearMonth() {
        return YearMonth.now(configRef.get().statsZone());
    }

    public List<VoteLeaderboardEntry> topForMonth(YearMonth month, int limit) {
        if (stats == null) return List.of();

        VotifierConfig cfg = configRef.get();
        int requested = toYearMonthInt(month);
        int current = toYearMonthInt(YearMonth.now(cfg.statsZone()));

        if (requested == current) {
            return stats.topForCurrentMonth(current, limit);
        }
        return stats.topForHistoricalMonth(requested, limit);
    }

    public String dumpTopForMonth(YearMonth month) {
        VotifierConfig cfg = configRef.get();
        if (stats == null) throw new IllegalStateException("stats disabled");

        YearMonth ym = (month == null) ? YearMonth.now(cfg.statsZone()).minusMonths(1) : month;
        int current = toYearMonthInt(YearMonth.now(cfg.statsZone()));

        Path f = stats.dumpTopForMonth(ym, current, cfg.dumpTopN(), cfg.dumpFilePrefix());
        return f.toString();
    }

    public Optional<VotePlayerStatsView> getPlayerStats(String username) {
        if (stats == null || username == null || username.isBlank()) return Optional.empty();

        VotifierConfig cfg = configRef.get();
        int currentYm = toYearMonthInt(YearMonth.now(cfg.statsZone()));

        Optional<PlayerEntity> playerOpt = stats.findPlayerByUsername(username);
        if (playerOpt.isEmpty()) {
            // Player truly does not exist in player_entity
            return Optional.empty();
        }

        PlayerEntity p = playerOpt.get();
        long playerId = p.getId() == null ? 0L : p.getId();
        if (playerId <= 0) return Optional.empty();

        String displayName = (p.getUsername() == null || p.getUsername().isBlank()) ? username.trim() : p.getUsername();

        Optional<PlayerVoteStatsEntity> statsOpt = stats.findStats(playerId);
        if (statsOpt.isEmpty()) {
            // Player exists but has never voted. Return all zeros.
            return Optional.of(new VotePlayerStatsView(
                    playerId,
                    displayName,
                    0,
                    0,
                    0L,
                    0,
                    0
            ));
        }

        PlayerVoteStatsEntity s = statsOpt.get();
        int monthVotes = (s.getMonthYearMonth() == currentYm) ? s.getMonthVotes() : 0;

        return Optional.of(new VotePlayerStatsView(
                playerId,
                displayName,
                monthVotes,
                s.getHighestMonthVotes(),
                s.getTotalVotes(),
                s.getVoteStreak(),
                s.getBestVoteStreak()
        ));
    }

    private void startServer(VotifierConfig cfg) {
        try {
            try {
                InetAddress.getByName(cfg.host());
            } catch (UnknownHostException uhe) {
                feature.getLogger().error("Invalid host '" + cfg.host() + "'");
                return;
            }

            Path baseDir = feature.getPlugin().getDataDirectory().resolve("local");
            if (!Files.exists(baseDir)) Files.createDirectories(baseDir);

            Path pubPath = baseDir.resolve(cfg.publicKeyFile());
            Path privPath = baseDir.resolve(cfg.privateKeyFile());

            if (cfg.generateKeys() && (!Files.exists(pubPath) || !Files.exists(privPath))) {
                RSAUtil.generateAndSaveKeyPair(pubPath, privPath, cfg.keyBits());
                feature.getLogger().info("Generated RSA keypair in " + baseDir.toAbsolutePath());
            }

            PrivateKey privateKey = RSAUtil.readPrivateKeyPem(privPath)
                    .orElseThrow(() -> new GeneralSecurityException("Missing or invalid private key: " + privPath));

            IpAccessList access = IpAccessList.fromCsv(cfg.allowlistCsv(), cfg.denylistCsv(), feature.getLogger());

            VotifierServer s = new VotifierServer(
                    cfg.host(),
                    cfg.port(),
                    cfg.readTimeoutMillis(),
                    cfg.backlog(),
                    cfg.maxPacketBytes(),
                    privateKey,
                    access,
                    this::onVoteFromNetwork,
                    feature.getLogger()
            );

            s.start();
            this.server = s;

        } catch (FileSystemException fse) {
            feature.getLogger().error("File access error: " + safeMsg(fse));
        } catch (GeneralSecurityException gse) {
            feature.getLogger().error("Key error: " + safeMsg(gse));
        } catch (IOException ioe) {
            feature.getLogger().error("Bind or start error: " + safeMsg(ioe));
        } catch (Throwable t) {
            feature.getLogger().error("Unexpected error during start: " + safeMsg(t));
        }
    }

    private void stopServer() {
        VotifierServer s = this.server;
        this.server = null;
        if (s != null) {
            try {
                s.stop();
            } catch (Throwable t) {
                feature.getLogger().warn("Error while stopping server: " + safeMsg(t));
            }
        }
    }

    private void onVoteFromNetwork(Vote vote) {
        if (vote == null) return;

        VotifierConfig cfg = configRef.get();
        if (cfg.logVotes()) {
            feature.getLogger().info("Vote received user=" + vote.username() + " service=" + vote.serviceName());
        }

        worker.execute(() -> processVote(vote));
    }

    private void processVote(Vote vote) {
        VotifierConfig cfg = configRef.get();

        String rawName = vote.username();
        String username = sanitizeUsername(rawName);
        if (username.isEmpty()) {
            feature.getLogger().warn("Discarded vote with invalid username: " + rawName);
            return;
        }

        PlayerEntity player = null;
        if (cfg.statsEnabled() && stats != null) {
            Optional<PlayerEntity> pOpt = stats.findPlayerByUsername(username);
            if (pOpt.isEmpty()) {
                feature.getLogger().warn("Discarded vote for unknown player: " + username);
                return;
            }
            player = pOpt.get();

            try {
                stats.recordVote(player, vote, cfg);
            } catch (Throwable t) {
                feature.getLogger().error("Failed to record vote stats for " + username + ": " + safeMsg(t));
            }
        } else {
            feature.getLogger().warn("Vote stats disabled, skipping unknown player discard for " + username);
        }

        if (cfg.redisEnabled() && bus != null) {
            String publishName = (player != null && player.getUsername() != null && !player.getUsername().isBlank())
                    ? player.getUsername()
                    : username;

            long ts = normalizeTimestampMillis(vote.timestamp());
            VoteMessage msg = new VoteMessage(
                    vote.serviceName(),
                    publishName,
                    vote.address(),
                    ts
            );

            if (cfg.publishLegacyChannel() && cfg.legacyChannel() != null && !cfg.legacyChannel().isBlank()) {
                bus.publishVote(msg, cfg.legacyChannel().trim());
            }
            if (cfg.redisChannel() != null && !cfg.redisChannel().isBlank()) {
                bus.publishVote(msg, cfg.redisChannel().trim());
            }
        }
    }

    private void scheduleMonthlyReset(VotifierConfig cfg) {
        cancelResetTask();

        if (!cfg.statsEnabled() || stats == null) return;

        int minutes = Math.max(1, cfg.resetCheckMinutes());
        Duration period = Duration.ofMinutes(minutes);

        this.resetTask = feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
            try {
                maybeRunMonthlyReset();
            } catch (Throwable t) {
                feature.getLogger().warn("Monthly reset check failed: " + safeMsg(t));
            }
        }, period);
    }

    private void cancelResetTask() {
        ScheduledTask t = resetTask;
        resetTask = null;
        if (t != null) {
            feature.getLifecycleManager().getTaskManager().cancelTask(t);
        }
    }

    private void maybeRunMonthlyReset() {
        VotifierConfig cfg = configRef.get();
        if (stats == null || !cfg.statsEnabled()) return;

        YearMonth nowYm = YearMonth.now(cfg.statsZone());
        int current = toYearMonthInt(nowYm);

        int lastReset = localState.lastResetYearMonth();
        if (lastReset == current) return;

        worker.execute(() -> {
            try {
                YearMonth prev = YearMonth.now(cfg.statsZone()).minusMonths(1);
                int cur = toYearMonthInt(YearMonth.now(cfg.statsZone()));
                Path dumpFile = stats.dumpTopForMonth(prev, cur, cfg.dumpTopN(), cfg.dumpFilePrefix());
                localState.setLastResetYearMonth(cur);
                feature.getLogger().info("Monthly vote dump completed. Dump=" + dumpFile);
            } catch (Throwable t) {
                feature.getLogger().error("Monthly vote dump failed: " + safeMsg(t));
            }
        });
    }

    private static int toYearMonthInt(YearMonth ym) {
        if (ym == null) return 0;
        return ym.getYear() * 100 + ym.getMonthValue();
    }

    private static String sanitizeUsername(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() > 16) t = t.substring(0, 16);
        if (!t.matches("[A-Za-z0-9_]{3,16}")) return "";
        return t;
    }

    private static long normalizeTimestampMillis(long raw) {
        long now = System.currentTimeMillis();
        if (raw <= 0) return now;

        if (raw < 100_000_000_000L) raw *= 1000L;

        if (raw > now + Duration.ofDays(7).toMillis()) return now;

        return raw;
    }

    private static String safeMsg(Throwable t) {
        return (t == null || t.getMessage() == null) ? "unknown" : t.getMessage();
    }
}