package nl.hauntedmc.proxyfeatures.features.votifier.internal;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteMonthlyEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteStatsEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.VotifierRolloverStateEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.model.Vote;
import org.hibernate.LockMode;
import org.hibernate.Session;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VoteStatsService {

    private static final long DEFAULT_STALE_LOCK_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final int ROLLOVER_STATE_ID = 1;
    private static final int UPSERT_BATCH = 1000;

    private static final String UPSERT_MONTHLY_SQL =
            "insert into player_vote_monthly " +
                    "(player_id, month_year_month, month_votes, final_rank, medal_applied, notify_sent, reward_granted, processing, processing_at) " +
                    "values (?, ?, ?, ?, false, false, false, false, 0) " +
                    "on duplicate key update month_votes = values(month_votes), final_rank = values(final_rank)";

    private static final String REMIND_COLUMN = "vote_remind_enabled";
    private static final String REMIND_SCHEMA_GUARD_SQL =
            "alter table player_vote_stats add column vote_remind_enabled tinyint(1) not null default 1";

    private final Votifier feature;
    private final ORMContext orm;

    private final AtomicBoolean remindSchemaChecked = new AtomicBoolean(false);

    public VoteStatsService(Votifier feature, ORMContext orm) {
        this(feature, orm, true);
    }

    VoteStatsService(Votifier feature, ORMContext orm, boolean ensureSchemaGuard) {
        this.feature = feature;
        this.orm = orm;
        if (ensureSchemaGuard) {
            ensureRemindColumnBestEffort();
        }
    }

    public enum RemindMode { ON, OFF, TOGGLE }

    public record RolloverResult(
            int finalizedMonthYearMonth,
            int currentMonthYearMonth,
            int medalsApplied,
            String dumpFile
    ) {
    }

    public Optional<PlayerEntity> findPlayerByUsername(String username) {
        if (username == null || username.isBlank()) return Optional.empty();
        String u = username.trim().toLowerCase(Locale.ROOT);

        return orm.runInTransaction(session ->
                session.createQuery("FROM PlayerEntity p WHERE lower(p.username) = :u", PlayerEntity.class)
                        .setParameter("u", u)
                        .uniqueResultOptional()
        );
    }

    public Optional<PlayerVoteStatsEntity> findStats(long playerId) {
        if (playerId <= 0) return Optional.empty();
        return orm.runInTransaction(session -> Optional.ofNullable(session.get(PlayerVoteStatsEntity.class, playerId)));
    }

    public Optional<VoteReminderState> getReminderStateByUsername(String username, int currentYyyymm) {
        if (username == null || username.isBlank()) return Optional.empty();

        Optional<PlayerEntity> pOpt = findPlayerByUsername(username);
        if (pOpt.isEmpty()) return Optional.empty();

        PlayerEntity p = pOpt.get();
        long playerId = p.getId() == null ? 0L : p.getId();
        if (playerId <= 0) return Optional.empty();

        String displayName = (p.getUsername() == null || p.getUsername().isBlank())
                ? username.trim()
                : p.getUsername();

        return orm.runInTransaction(session -> {
            PlayerEntity managedPlayer = session.get(PlayerEntity.class, playerId, LockMode.PESSIMISTIC_READ);
            if (managedPlayer == null) return Optional.empty();

            PlayerVoteStatsEntity stats = session.get(PlayerVoteStatsEntity.class, playerId, LockMode.PESSIMISTIC_WRITE);
            if (stats == null) {
                stats = new PlayerVoteStatsEntity(managedPlayer);
                stats.setMonthYearMonth(Math.max(0, currentYyyymm));
                stats.setMonthVotes(0);
                stats.setVoteRemindEnabled(true);
                session.persist(stats);
            }

            return Optional.of(new VoteReminderState(
                    playerId,
                    displayName,
                    stats.isVoteRemindEnabled(),
                    stats.getLastVoteAt()
            ));
        });
    }

    public Optional<Boolean> setVoteRemindEnabledByUsername(String username, int currentYyyymm, RemindMode mode) {
        if (username == null || username.isBlank() || mode == null) return Optional.empty();

        Optional<PlayerEntity> pOpt = findPlayerByUsername(username);
        if (pOpt.isEmpty()) return Optional.empty();

        PlayerEntity p = pOpt.get();
        long playerId = p.getId() == null ? 0L : p.getId();
        if (playerId <= 0) return Optional.empty();

        return orm.runInTransaction(session -> {
            PlayerEntity managedPlayer = session.get(PlayerEntity.class, playerId, LockMode.PESSIMISTIC_READ);
            if (managedPlayer == null) return Optional.empty();

            PlayerVoteStatsEntity stats = session.get(PlayerVoteStatsEntity.class, playerId, LockMode.PESSIMISTIC_WRITE);
            if (stats == null) {
                stats = new PlayerVoteStatsEntity(managedPlayer);
                stats.setMonthYearMonth(Math.max(0, currentYyyymm));
                stats.setMonthVotes(0);
                stats.setVoteRemindEnabled(true);
                session.persist(stats);
            }

            boolean cur = stats.isVoteRemindEnabled();
            boolean next = switch (mode) {
                case ON -> true;
                case OFF -> false;
                case TOGGLE -> !cur;
            };

            stats.setVoteRemindEnabled(next);
            return Optional.of(next);
        });
    }

    public void recordVote(PlayerEntity player, Vote vote, VotifierConfig cfg) {
        if (player == null) return;

        long nowMillis = System.currentTimeMillis();
        int currentYm = toYearMonthInt(YearMonth.now(cfg.statsZone()));

        orm.runInTransaction(session -> {
            Long playerIdObj = player.getId();
            long playerId = playerIdObj == null ? 0L : playerIdObj;
            if (playerId <= 0) return null;

            PlayerEntity managedPlayer = session.get(PlayerEntity.class, playerId, LockMode.PESSIMISTIC_WRITE);
            if (managedPlayer == null) return null;

            PlayerVoteStatsEntity stats = session.get(PlayerVoteStatsEntity.class, playerId, LockMode.PESSIMISTIC_WRITE);
            if (stats == null) {
                stats = new PlayerVoteStatsEntity(managedPlayer);

                stats.setMonthYearMonth(currentYm);
                stats.setMonthVotes(0);
                stats.setVoteRemindEnabled(true);

                session.persist(stats);
            }

            if (stats.getMonthYearMonth() != currentYm) {
                feature.getLogger().warn("VoteStats out of sync for playerId=" + playerId
                        + " statsMonth=" + stats.getMonthYearMonth()
                        + " currentMonth=" + currentYm
                        + " forcing reset to currentMonth");
                stats.setMonthYearMonth(currentYm);
                stats.setMonthVotes(0);
            }

            long lastVoteAt = stats.getLastVoteAt();
            long gapMillis = Duration.ofHours(Math.max(1, cfg.streakGapHours())).toMillis();

            int newStreak = computeNextStreak(stats.getVoteStreak(), lastVoteAt, nowMillis, gapMillis);
            stats.setVoteStreak(newStreak);
            stats.setBestVoteStreak(Math.max(stats.getBestVoteStreak(), newStreak));

            stats.setTotalVotes(stats.getTotalVotes() + 1);

            stats.setLastVoteAt(nowMillis);
            stats.setLastVoteService(sanitize(vote == null ? null : vote.serviceName(), 64));
            stats.setLastVoteAddress(sanitize(vote == null ? null : vote.address(), 64));

            int newMonthVotes = stats.getMonthVotes() + 1;
            stats.setMonthVotes(newMonthVotes);

            if (newMonthVotes > stats.getHighestMonthVotes()) {
                stats.setHighestMonthVotes(newMonthVotes);
            }

            return null;
        });
    }

    public List<VoteLeaderboardEntry> topForCurrentMonth(int currentYyyymm, int limit) {
        int lim = Math.max(1, Math.min(1000, limit));
        return orm.runInTransaction(session ->
                session.createQuery(
                                "select new nl.hauntedmc.proxyfeatures.features.votifier.internal.VoteLeaderboardEntry(" +
                                        "s.playerId, p.username, s.monthVotes, s.totalVotes) " +
                                        "from PlayerVoteStatsEntity s join s.player p " +
                                        "where s.monthYearMonth = :m and s.monthVotes > 0 " +
                                        "order by s.monthVotes desc, s.lastVoteAt asc, s.playerId asc",
                                VoteLeaderboardEntry.class
                        )
                        .setParameter("m", currentYyyymm)
                        .setMaxResults(lim)
                        .list()
        );
    }

    public List<VoteLeaderboardEntry> topForHistoricalMonth(int yyyymm, int limit) {
        int lim = Math.max(1, Math.min(1000, limit));
        return orm.runInTransaction(session ->
                session.createQuery(
                                "select new nl.hauntedmc.proxyfeatures.features.votifier.internal.VoteLeaderboardEntry(" +
                                        "m.id.playerId, p.username, m.monthVotes, s.totalVotes) " +
                                        "from PlayerVoteMonthlyEntity m, PlayerVoteStatsEntity s " +
                                        "join m.player p " +
                                        "where m.id.monthYearMonth = :m " +
                                        "and m.finalRank > 0 " +
                                        "and m.monthVotes > 0 " +
                                        "and s.playerId = m.id.playerId " +
                                        "order by m.finalRank asc, m.id.playerId asc",
                                VoteLeaderboardEntry.class
                        )
                        .setParameter("m", yyyymm)
                        .setMaxResults(lim)
                        .list()
        );
    }

    public List<VoteWinnersEntry> winnersLeaderboard(int limit) {
        int lim = Math.max(1, Math.min(1000, limit));
        return orm.runInTransaction(session ->
                session.createQuery(
                                "select new nl.hauntedmc.proxyfeatures.features.votifier.internal.VoteWinnersEntry(" +
                                        "s.playerId, p.username, s.firstPlaces, s.secondPlaces, s.thirdPlaces) " +
                                        "from PlayerVoteStatsEntity s join s.player p " +
                                        "where (s.firstPlaces + s.secondPlaces + s.thirdPlaces) > 0 " +
                                        "order by s.firstPlaces desc, s.secondPlaces desc, s.thirdPlaces desc, p.username asc, s.playerId asc",
                                VoteWinnersEntry.class
                        )
                        .setMaxResults(lim)
                        .list()
        );
    }

    public Optional<RolloverResult> rolloverIfNeeded(VotifierConfig cfg) {
        if (cfg == null) return Optional.empty();

        int currentYm = toYearMonthInt(YearMonth.now(cfg.statsZone()));

        Integer fastLast = orm.runInTransaction(session -> {
            VotifierRolloverStateEntity st = session.get(VotifierRolloverStateEntity.class, ROLLOVER_STATE_ID);
            return st == null ? 0 : st.getLastResetYearMonth();
        });

        if (fastLast != null && fastLast == currentYm) {
            return Optional.empty();
        }

        record TxResult(int finalized, int medalsApplied) {
        }

        TxResult tx = orm.runInTransaction(session -> {
            VotifierRolloverStateEntity st = session.get(
                    VotifierRolloverStateEntity.class,
                    ROLLOVER_STATE_ID,
                    LockMode.PESSIMISTIC_WRITE
            );
            if (st == null) {
                st = new VotifierRolloverStateEntity(ROLLOVER_STATE_ID, 0);
                session.persist(st);
            }

            int last = st.getLastResetYearMonth();
            if (last == currentYm) return null;

            if (last > currentYm) {
                st.setLastResetYearMonth(currentYm);
                return null;
            }

            int tracked = last;
            if (tracked <= 0) {
                Integer detected = session.createQuery(
                                "select max(s.monthYearMonth) from PlayerVoteStatsEntity s where s.monthYearMonth > 0",
                                Integer.class
                        )
                        .setMaxResults(1)
                        .uniqueResult();
                tracked = detected == null ? 0 : detected;
            }

            if (tracked <= 0 || tracked == currentYm) {
                st.setLastResetYearMonth(currentYm);
                return null;
            }

            int medalsApplied = finalizeMonthAndReset(session, tracked, currentYm);

            st.setLastResetYearMonth(currentYm);

            return new TxResult(tracked, medalsApplied);
        });

        if (tx == null) return Optional.empty();

        YearMonth finalizedYm = fromYearMonthInt(tx.finalized());
        if (finalizedYm == null) return Optional.empty();

        Path dump = dumpTopForMonth(finalizedYm, currentYm, cfg.dumpTopN(), cfg.dumpFilePrefix());

        return Optional.of(new RolloverResult(
                tx.finalized(),
                currentYm,
                tx.medalsApplied(),
                dump.toString()
        ));
    }

    private int finalizeMonthAndReset(Session session, int finishedYyyymm, int newCurrentYyyymm) {
        List<VoteFinalizeEntry> ranked = session.createQuery(
                        "select new nl.hauntedmc.proxyfeatures.features.votifier.internal.VoteFinalizeEntry(" +
                                "s.playerId, s.monthVotes, s.lastVoteAt) " +
                                "from PlayerVoteStatsEntity s " +
                                "where s.monthYearMonth = :m and s.monthVotes > 0 " +
                                "order by s.monthVotes desc, s.lastVoteAt asc, s.playerId asc",
                        VoteFinalizeEntry.class
                )
                .setParameter("m", finishedYyyymm)
                .list();

        upsertMonthlySnapshot(session, finishedYyyymm, ranked);

        int medalsApplied = applyTop3Medals(session, finishedYyyymm, ranked);

        session.createQuery(
                        "update PlayerVoteStatsEntity s " +
                                "set s.monthYearMonth = :newMonth, s.monthVotes = 0 " +
                                "where s.monthYearMonth = :oldMonth"
                )
                .setParameter("newMonth", newCurrentYyyymm)
                .setParameter("oldMonth", finishedYyyymm)
                .executeUpdate();

        return medalsApplied;
    }

    private void upsertMonthlySnapshot(Session session, int finishedYyyymm, List<VoteFinalizeEntry> ranked) {
        session.doWork(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(UPSERT_MONTHLY_SQL)) {
                int batch = 0;
                int rank = 1;

                for (VoteFinalizeEntry e : ranked) {
                    long pid = e.playerId();
                    if (pid <= 0) {
                        rank++;
                        continue;
                    }

                    ps.setLong(1, pid);
                    ps.setInt(2, finishedYyyymm);
                    ps.setInt(3, Math.max(0, e.monthVotes()));
                    ps.setInt(4, rank);

                    ps.addBatch();
                    batch++;

                    if (batch >= UPSERT_BATCH) {
                        ps.executeBatch();
                        batch = 0;
                    }

                    rank++;
                }

                if (batch > 0) {
                    ps.executeBatch();
                }

            } catch (SQLException sql) {
                throw new RuntimeException("Monthly snapshot upsert failed: " + sql.getMessage(), sql);
            }
        });
    }

    private int applyTop3Medals(Session session, int finishedYyyymm, List<VoteFinalizeEntry> ranked) {
        int medalsApplied = 0;

        int max = Math.min(3, ranked.size());
        for (int i = 0; i < max; i++) {
            VoteFinalizeEntry e = ranked.get(i);
            long pid = e.playerId();
            if (pid <= 0) continue;

            int rank = i + 1;

            int claimed = session.createQuery(
                            "update PlayerVoteMonthlyEntity m " +
                                    "set m.medalApplied = true " +
                                    "where m.id.playerId = :pid and m.id.monthYearMonth = :mo and m.medalApplied = false"
                    )
                    .setParameter("pid", pid)
                    .setParameter("mo", finishedYyyymm)
                    .executeUpdate();

            if (claimed != 1) {
                continue;
            }

            int updated = switch (rank) {
                case 1 -> session.createQuery(
                                "update PlayerVoteStatsEntity s set s.firstPlaces = s.firstPlaces + 1 where s.playerId = :pid"
                        )
                        .setParameter("pid", pid)
                        .executeUpdate();
                case 2 -> session.createQuery(
                                "update PlayerVoteStatsEntity s set s.secondPlaces = s.secondPlaces + 1 where s.playerId = :pid"
                        )
                        .setParameter("pid", pid)
                        .executeUpdate();
                case 3 -> session.createQuery(
                                "update PlayerVoteStatsEntity s set s.thirdPlaces = s.thirdPlaces + 1 where s.playerId = :pid"
                        )
                        .setParameter("pid", pid)
                        .executeUpdate();
                default -> 0;
            };

            if (updated != 1) {
                throw new IllegalStateException("Failed to increment medal stats for playerId=" + pid + " month=" + finishedYyyymm + " rank=" + rank);
            }

            medalsApplied += 1;
        }

        return medalsApplied;
    }

    public Optional<VoteMonthNotification> claimPendingMonthNotificationByUsername(String username, int currentYyyymm) {
        if (username == null || username.isBlank()) return Optional.empty();
        if (currentYyyymm <= 0) return Optional.empty();

        Optional<PlayerEntity> pOpt = findPlayerByUsername(username);
        if (pOpt.isEmpty()) return Optional.empty();

        PlayerEntity p = pOpt.get();
        long playerId = p.getId() == null ? 0L : p.getId();
        if (playerId <= 0) return Optional.empty();

        String displayName = (p.getUsername() == null || p.getUsername().isBlank())
                ? username.trim()
                : p.getUsername();

        long now = System.currentTimeMillis();
        long staleBefore = now - DEFAULT_STALE_LOCK_MILLIS;

        return orm.runInTransaction(session -> {
            List<PlayerVoteMonthlyEntity> candidates = session.createQuery(
                            "from PlayerVoteMonthlyEntity m " +
                                    "where m.id.playerId = :pid " +
                                    "and m.id.monthYearMonth < :cur " +
                                    "and m.monthVotes > 0 " +
                                    "and m.finalRank > 0 " +
                                    "and (m.processing = false or m.processingAt < :staleBefore) " +
                                    "and (m.notifySent = false or (m.finalRank <= 3 and m.rewardGranted = false)) " +
                                    "and exists (select 1 from PlayerVoteMonthlyEntity w where w.id.monthYearMonth = m.id.monthYearMonth and w.finalRank = 1) " +
                                    "order by m.id.monthYearMonth desc",
                            PlayerVoteMonthlyEntity.class
                    )
                    .setParameter("pid", playerId)
                    .setParameter("cur", currentYyyymm)
                    .setParameter("staleBefore", staleBefore)
                    .setMaxResults(1)
                    .list();

            if (candidates.isEmpty()) return Optional.empty();

            PlayerVoteMonthlyEntity row = candidates.getFirst();
            int month = row.getMonthYearMonth();
            int votes = row.getMonthVotes();
            int rank = row.getFinalRank();

            boolean needsNotify = !row.isNotifySent();
            boolean needsReward = (rank > 0 && rank <= 3) && !row.isRewardGranted();

            if (!needsNotify && !needsReward) {
                return Optional.empty();
            }

            int locked = session.createQuery(
                            "update PlayerVoteMonthlyEntity m " +
                                    "set m.processing = true, m.processingAt = :now " +
                                    "where m.id.playerId = :pid and m.id.monthYearMonth = :mo " +
                                    "and (m.processing = false or m.processingAt < :staleBefore) " +
                                    "and (m.notifySent = false or (m.finalRank <= 3 and m.rewardGranted = false))"
                    )
                    .setParameter("now", now)
                    .setParameter("pid", playerId)
                    .setParameter("mo", month)
                    .setParameter("staleBefore", staleBefore)
                    .executeUpdate();

            if (locked != 1) return Optional.empty();

            return Optional.of(new VoteMonthNotification(
                    playerId,
                    displayName,
                    month,
                    rank,
                    votes,
                    needsNotify,
                    needsReward
            ));
        });
    }

    public void releaseProcessing(long playerId, int month) {
        if (playerId <= 0 || month <= 0) return;

        orm.runInTransaction(session -> {
            session.createQuery(
                            "update PlayerVoteMonthlyEntity m " +
                                    "set m.processing = false, m.processingAt = 0 " +
                                    "where m.id.playerId = :pid and m.id.monthYearMonth = :mo"
                    )
                    .setParameter("pid", playerId)
                    .setParameter("mo", month)
                    .executeUpdate();
            return null;
        });
    }

    public void completeProcessing(long playerId, int month, int computedRank, boolean notifySentNow, boolean rewardGrantedNow) {
        if (playerId <= 0 || month <= 0) return;

        int safeRank = Math.max(0, computedRank);

        orm.runInTransaction(session -> {
            session.createQuery(
                            "update PlayerVoteMonthlyEntity m " +
                                    "set m.processing = false, m.processingAt = 0, " +
                                    "m.finalRank = (case when m.finalRank > 0 then m.finalRank else :r end), " +
                                    "m.notifySent = (case when m.notifySent = true then true else :ns end), " +
                                    "m.rewardGranted = (case when m.rewardGranted = true then true else :rg end) " +
                                    "where m.id.playerId = :pid and m.id.monthYearMonth = :mo"
                    )
                    .setParameter("r", safeRank)
                    .setParameter("ns", notifySentNow)
                    .setParameter("rg", rewardGrantedNow)
                    .setParameter("pid", playerId)
                    .setParameter("mo", month)
                    .executeUpdate();
            return null;
        });
    }

    public Path dumpTopForMonth(YearMonth month, int currentYyyymm, int topN, String prefix) {
        YearMonth ym = month == null ? YearMonth.now() : month;
        int yyyymm = toYearMonthInt(ym);
        int lim = Math.max(1, Math.min(1000, topN));

        List<VoteLeaderboardEntry> top = (yyyymm == currentYyyymm)
                ? topForCurrentMonth(currentYyyymm, lim)
                : topForHistoricalMonth(yyyymm, lim);

        Path baseDir = feature.getPlugin().getDataDirectory().resolve("local");
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String fileName = prefix + "-" + ym + ".txt";
        Path out = baseDir.resolve(fileName);

        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("month=" + ym);
            w.newLine();
            w.write("generatedAt=" + Instant.now());
            w.newLine();
            w.write("entries=" + top.size());
            w.newLine();
            w.newLine();

            int rank = 1;
            for (VoteLeaderboardEntry e : top) {
                w.write(rank + ". " + e.username() + " playerId=" + e.playerId()
                        + " monthVotes=" + e.monthVotes()
                        + " totalVotes=" + e.totalVotes());
                w.newLine();
                rank++;
            }
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        return out;
    }

    private void ensureRemindColumnBestEffort() {
        if (!remindSchemaChecked.compareAndSet(false, true)) return;

        try {
            orm.runInTransaction(session -> {
                session.doWork(conn -> {
                    try {
                        DatabaseMetaData md = conn.getMetaData();
                        String catalog = conn.getCatalog();

                        boolean exists = false;
                        try (ResultSet rs = md.getColumns(catalog, null, "player_vote_stats", REMIND_COLUMN)) {
                            if (rs != null && rs.next()) exists = true;
                        }

                        if (!exists) {
                            try (Statement st = conn.createStatement()) {
                                st.executeUpdate(REMIND_SCHEMA_GUARD_SQL);
                            }
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                });
                return null;
            });
        } catch (Exception t) {
            feature.getLogger().warn("Vote remind schema guard failed: " + (t.getMessage() == null ? "unknown" : t.getMessage()));
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

    private static String sanitize(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() > max) t = t.substring(0, max);
        return t;
    }

    static int computeNextStreak(int currentStreak, long lastVoteAt, long nowMillis, long gapMillis) {
        if (lastVoteAt > 0 && (nowMillis - lastVoteAt) <= gapMillis) {
            return Math.max(0, currentStreak) + 1;
        }
        return 1;
    }
}
