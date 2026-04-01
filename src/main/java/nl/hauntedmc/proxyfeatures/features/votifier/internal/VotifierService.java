package nl.hauntedmc.proxyfeatures.features.votifier.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class VotifierService {

    public enum RemindMode { ON, OFF, TOGGLE }

    private static final DateTimeFormatter DISPLAY_MONTH = DateTimeFormatter.ofPattern("MM-uuuu");

    private final Votifier feature;

    private final ExecutorService worker;
    private final AtomicReference<VotifierConfig> configRef;

    private final EventBusHandler bus;
    private final VoteStatsService stats;

    private final ConcurrentHashMap<UUID, ScheduledTask> remindTasks = new ConcurrentHashMap<>();

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

        if (cfg.statsEnabled() && stats != null) {
            worker.execute(() -> {
                try {
                    maybeRollover(cfg);
                } catch (Exception t) {
                    feature.getLogger().warn("Rollover bootstrap failed: " + safeMsg(t));
                }
            });
        }
    }

    public void shutdown() {
        cancelResetTask();
        stopServer();
        cancelAllReminders();

        worker.shutdownNow();
        try {
            worker.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            feature.getLogger().warn("Interrupted while shutting down votifier worker.");
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

    public boolean publishTestVote(String serviceName, String username) {
        VotifierConfig cfg = configRef.get();
        if (!cfg.redisEnabled() || bus == null) return false;

        String service = sanitize(serviceName, 64);
        String user = sanitize(username, 16);

        if (service.isBlank() || user.isBlank()) return false;

        VoteMessage msg = new VoteMessage(
                service,
                user,
                "test",
                System.currentTimeMillis()
        );

        if (cfg.publishLegacyChannel() && cfg.legacyChannel() != null && !cfg.legacyChannel().isBlank()) {
            bus.publishVote(msg, cfg.legacyChannel().trim());
        }
        if (cfg.redisChannel() != null && !cfg.redisChannel().isBlank()) {
            bus.publishVote(msg, cfg.redisChannel().trim());
        }

        return true;
    }

    public List<VoteLeaderboardEntry> topForMonth(YearMonth month, int limit) {
        if (stats == null) return List.of();

        VotifierConfig cfg = configRef.get();
        int requested = toYearMonthInt(month);
        int current = toYearMonthInt(YearMonth.now(cfg.statsZone()));

        if (requested != current) {
            maybeRollover(cfg);
            current = toYearMonthInt(YearMonth.now(cfg.statsZone()));
        }

        if (requested == current) {
            return stats.topForCurrentMonth(current, limit);
        }
        return stats.topForHistoricalMonth(requested, limit);
    }

    public List<VoteWinnersEntry> winnersLeaderboard(int limit) {
        if (stats == null) return List.of();
        int lim = Math.max(1, Math.min(1000, limit));
        return stats.winnersLeaderboard(lim);
    }

    public String dumpTopForMonth(YearMonth month) {
        VotifierConfig cfg = configRef.get();
        if (stats == null) throw new IllegalStateException("stats disabled");

        maybeRollover(cfg);

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
            return Optional.empty();
        }

        PlayerEntity p = playerOpt.get();
        long playerId = p.getId() == null ? 0L : p.getId();
        if (playerId <= 0) return Optional.empty();

        String displayName = (p.getUsername() == null || p.getUsername().isBlank()) ? username.trim() : p.getUsername();

        Optional<PlayerVoteStatsEntity> statsOpt = stats.findStats(playerId);
        if (statsOpt.isEmpty()) {
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

    public Optional<Boolean> getVoteRemindEnabled(Player player) {
        if (player == null) return Optional.empty();
        if (stats == null || !configRef.get().statsEnabled()) return Optional.empty();

        VotifierConfig cfg = configRef.get();
        int currentYm = toYearMonthInt(YearMonth.now(cfg.statsZone()));

        Optional<VoteReminderState> st = stats.getReminderStateByUsername(player.getUsername(), currentYm);
        return st.map(VoteReminderState::remindEnabled);
    }

    public Optional<Boolean> setVoteRemind(Player player, RemindMode mode) {
        if (player == null || mode == null) return Optional.empty();
        if (stats == null || !configRef.get().statsEnabled()) return Optional.empty();

        VotifierConfig cfg = configRef.get();
        int currentYm = toYearMonthInt(YearMonth.now(cfg.statsZone()));

        Optional<Boolean> updated = stats.setVoteRemindEnabledByUsername(
                player.getUsername(),
                currentYm,
                switch (mode) {
                    case ON -> VoteStatsService.RemindMode.ON;
                    case OFF -> VoteStatsService.RemindMode.OFF;
                    case TOGGLE -> VoteStatsService.RemindMode.TOGGLE;
                }
        );

        if (updated.isPresent()) {
            if (!updated.get()) {
                cancelReminder(player.getUniqueId());
            } else {
                scheduleReminderLoop(player);
            }
        }

        return updated;
    }

    public void onPlayerPostLogin(Player player) {
        if (player == null) return;

        // Existing month results pipeline
        if (stats != null && configRef.get().statsEnabled()) {
            VotifierConfig cfg = configRef.get();

            String username = player.getUsername();
            UUID uuid = player.getUniqueId();
            int currentYmInt = toYearMonthInt(YearMonth.now(cfg.statsZone()));

            worker.execute(() -> {
                try {
                    Optional<VoteMonthNotification> notifOpt = stats.claimPendingMonthNotificationByUsername(username, currentYmInt);
                    if (notifOpt.isEmpty()) return;

                    VoteMonthNotification notif = notifOpt.get();

                    feature.getLifecycleManager().getTaskManager().scheduleTask(() -> {
                        Optional<Player> live = ProxyFeatures.getProxyInstance().getPlayer(uuid);
                        if (live.isEmpty()) {
                            worker.execute(() -> stats.releaseProcessing(notif.playerId(), notif.monthYearMonth()));
                            return;
                        }

                        Player target = live.get();

                        boolean notifyOk = false;
                        boolean rewardOk = false;

                        try {
                            if (notif.needsNotify()) {
                                if (notif.rank() > 0 && notif.rank() <= 3) {
                                    sendWinnerCongrats(target, notif);
                                } else {
                                    sendMonthResult(target, notif);
                                }
                            }
                            notifyOk = true;
                        } catch (Exception t) {
                            feature.getLogger().warn("Vote month notify failed: " + safeMsg(t));
                        }

                        try {
                            if (notif.needsReward()) {
                                grantMonthlyWinnerReward(target, notif);
                            }
                            rewardOk = true;
                        } catch (Exception t) {
                            feature.getLogger().warn("Vote month reward failed: " + safeMsg(t));
                        }

                        boolean finalNotify = notif.needsNotify() && notifyOk;

                        boolean finalReward;
                        if (notif.rank() > 0 && notif.rank() <= 3) {
                            finalReward = notif.needsReward() && rewardOk;
                        } else {
                            finalReward = finalNotify;
                        }

                        worker.execute(() -> stats.completeProcessing(
                                notif.playerId(),
                                notif.monthYearMonth(),
                                notif.rank(),
                                finalNotify,
                                finalReward
                        ));
                    });

                } catch (Exception t) {
                    feature.getLogger().warn("Vote month handler failed: " + safeMsg(t));
                }
            });
        }

        // New reminder loop
        scheduleReminderLoop(player);
    }

    public void onPlayerDisconnect(UUID uuid) {
        if (uuid == null) return;
        cancelReminder(uuid);
    }

    private void scheduleReminderLoop(Player player) {
        if (player == null) return;

        VotifierConfig cfg = configRef.get();
        if (!cfg.remindEnabled()) return;
        if (stats == null || !cfg.statsEnabled()) return;

        UUID uuid = player.getUniqueId();

        cancelReminder(uuid);

        Duration initialDelay = Duration.ofMinutes(Math.max(0, cfg.remindInitialDelayMinutes()));
        Duration interval = Duration.ofMinutes(Math.max(1, cfg.remindIntervalMinutes()));

        ScheduledTask t = feature.getLifecycleManager().getTaskManager().scheduleRepeatingTask(() -> {
            worker.execute(() -> reminderTick(uuid));
        }, initialDelay, interval);

        remindTasks.put(uuid, t);
    }

    private void reminderTick(UUID uuid) {
        if (uuid == null) return;

        Optional<Player> liveOpt = ProxyFeatures.getProxyInstance().getPlayer(uuid);
        if (liveOpt.isEmpty()) {
            cancelReminder(uuid);
            return;
        }

        Player player = liveOpt.get();

        VotifierConfig cfg = configRef.get();
        if (!cfg.remindEnabled()) {
            cancelReminder(uuid);
            return;
        }
        if (stats == null || !cfg.statsEnabled()) {
            return;
        }

        String url = cfg.voteUrl();
        if (url == null || url.isBlank()) {
            return;
        }

        int currentYm = toYearMonthInt(YearMonth.now(cfg.statsZone()));
        Optional<VoteReminderState> stOpt = stats.getReminderStateByUsername(player.getUsername(), currentYm);
        if (stOpt.isEmpty()) {
            return;
        }

        VoteReminderState st = stOpt.get();
        if (!st.remindEnabled()) {
            cancelReminder(uuid);
            return;
        }

        long now = System.currentTimeMillis();
        long thresholdMillis = Duration.ofHours(Math.max(1, cfg.remindThresholdHours())).toMillis();
        long lastVoteAt = st.lastVoteAt();

        boolean neverVoted = lastVoteAt <= 0;
        boolean due = neverVoted || (now - lastVoteAt) >= thresholdMillis;
        if (!due) {
            return;
        }

        String cleanUrl = url.trim();

        if (neverVoted) {
            feature.getLifecycleManager().getTaskManager().scheduleTask(() -> {
                Optional<Player> liveAgain = ProxyFeatures.getProxyInstance().getPlayer(uuid);
                if (liveAgain.isEmpty()) return;

                Player p = liveAgain.get();

                Component urlComp = Component.text(cleanUrl, NamedTextColor.AQUA)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(cleanUrl))
                        .hoverEvent(HoverEvent.showText(Component.text("Open vote link", NamedTextColor.GRAY)));

                p.sendMessage(feature.getLocalizationHandler()
                        .getMessage("votifier.remind.never")
                        .forAudience(p)
                        .build());

                p.sendMessage(feature.getLocalizationHandler()
                        .getMessage("votifier.remind.line")
                        .with("url", urlComp)
                        .forAudience(p)
                        .build());
            });
            return;
        }

        long diffMillis = Math.max(0L, now - lastVoteAt);
        long hours = Math.max(0L, diffMillis / 3_600_000L);

        final String timeText;
        final String unitKey;

        if (hours >= 48L) {
            int days = (int) Math.max(2L, Math.round(hours / 24.0d));
            timeText = String.valueOf(days);
            unitKey = (days == 1) ? "votifier.remind.unit.day" : "votifier.remind.unit.days";
        } else {
            long showHours = Math.max(1L, hours);
            timeText = String.valueOf(showHours);
            unitKey = (showHours == 1L) ? "votifier.remind.unit.hour" : "votifier.remind.unit.hours";
        }

        feature.getLifecycleManager().getTaskManager().scheduleTask(() -> {
            Optional<Player> liveAgain = ProxyFeatures.getProxyInstance().getPlayer(uuid);
            if (liveAgain.isEmpty()) return;

            Player p = liveAgain.get();

            Component unitComp = feature.getLocalizationHandler()
                    .getMessage(unitKey)
                    .forAudience(p)
                    .build();

            Component urlComp = Component.text(cleanUrl, NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(cleanUrl))
                    .hoverEvent(HoverEvent.showText(Component.text("Open vote link", NamedTextColor.GRAY)));

            p.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.remind.header")
                    .with("time", timeText)
                    .with("unit", unitComp)
                    .forAudience(p)
                    .build());

            p.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.remind.line")
                    .with("url", urlComp)
                    .forAudience(p)
                    .build());
        });
    }

    private void cancelReminder(UUID uuid) {
        ScheduledTask t = remindTasks.remove(uuid);
        if (t != null) {
            feature.getLifecycleManager().getTaskManager().cancelTask(t);
        }
    }

    private void cancelAllReminders() {
        for (UUID uuid : remindTasks.keySet()) {
            cancelReminder(uuid);
        }
        remindTasks.clear();
    }

    private void sendWinnerCongrats(Player player, VoteMonthNotification notif) {
        String month = formatYearMonthInt(notif.monthYearMonth());
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask( () -> player.sendMessage(feature.getLocalizationHandler()
                .getMessage("votifier.winner.congrats")
                .with("rank", String.valueOf(notif.rank()))
                .with("month", month)
                .with("votes", String.valueOf(Math.max(0, notif.monthVotes())))
                .forAudience(player)
            .build()), Duration.ofSeconds(5));
    }

    private void sendMonthResult(Player player, VoteMonthNotification notif) {
        String month = formatYearMonthInt(notif.monthYearMonth());
        feature.getLifecycleManager().getTaskManager().scheduleDelayedTask( () -> player.sendMessage(feature.getLocalizationHandler()
            .getMessage("votifier.month.result")
            .with("rank", String.valueOf(Math.max(0, notif.rank())))
            .with("month", month)
            .with("votes", String.valueOf(Math.max(0, notif.monthVotes())))
            .forAudience(player)
            .build()), Duration.ofSeconds(5));
    }

    private void grantMonthlyWinnerReward(Player player, VoteMonthNotification notif) {
        // Intentionally empty.
        // Implement later.
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
        } catch (Exception t) {
            feature.getLogger().error("Unexpected error during start: " + safeMsg(t));
        }
    }

    private void stopServer() {
        VotifierServer s = this.server;
        this.server = null;
        if (s != null) {
            try {
                s.stop();
            } catch (Exception t) {
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
            maybeRollover(cfg);

            Optional<PlayerEntity> pOpt = stats.findPlayerByUsername(username);
            if (pOpt.isEmpty()) {
                feature.getLogger().warn("Discarded vote for unknown player: " + username);
                return;
            }
            player = pOpt.get();

            try {
                stats.recordVote(player, vote, cfg);
            } catch (Exception t) {
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
            worker.execute(() -> {
                try {
                    maybeRollover(cfg);
                } catch (Exception t) {
                    feature.getLogger().warn("Monthly rollover check failed: " + safeMsg(t));
                }
            });
        }, period);
    }

    private void maybeRollover(VotifierConfig cfg) {
        if (stats == null || cfg == null || !cfg.statsEnabled()) return;

        Optional<VoteStatsService.RolloverResult> res = stats.rolloverIfNeeded(cfg);
        if (res.isEmpty()) return;

        VoteStatsService.RolloverResult r = res.get();

        feature.getLogger().info("Monthly finalize completed. Month=" + formatYearMonthInt(r.finalizedMonthYearMonth())
                + " dump=" + r.dumpFile()
                + " medalsApplied=" + r.medalsApplied());
    }

    private void cancelResetTask() {
        ScheduledTask t = resetTask;
        resetTask = null;
        if (t != null) {
            feature.getLifecycleManager().getTaskManager().cancelTask(t);
        }
    }

    private static int toYearMonthInt(YearMonth ym) {
        if (ym == null) return 0;
        return ym.getYear() * 100 + ym.getMonthValue();
    }

    private static YearMonth fromYearMonthInt(int yyyymm) {
        if (yyyymm <= 0) return null;
        int year = yyyymm / 100;
        int month = yyyymm % 100;
        if (year < 1970 || month < 1 || month > 12) return null;
        try {
            return YearMonth.of(year, month);
        } catch (RuntimeException ex) {
            return null;
        }
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

    private static String formatYearMonthInt(int yyyymm) {
        YearMonth ym = fromYearMonthInt(yyyymm);
        if (ym == null) return "";
        return DISPLAY_MONTH.format(ym);
    }

    private static String sanitize(String s, int maxLen) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() > maxLen) t = t.substring(0, maxLen);
        return t;
    }

    private static String safeMsg(Throwable t) {
        return (t == null || t.getMessage() == null) ? "unknown" : t.getMessage();
    }
}
