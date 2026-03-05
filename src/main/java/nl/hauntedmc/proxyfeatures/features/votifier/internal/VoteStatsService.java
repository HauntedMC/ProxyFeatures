package nl.hauntedmc.proxyfeatures.features.votifier.internal;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteMonthlyEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteMonthlyKey;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteStatsEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.model.Vote;
import org.hibernate.LockMode;
import org.hibernate.Session;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class VoteStatsService {

    private static final long DEFAULT_STALE_LOCK_MILLIS = Duration.ofMinutes(10).toMillis();

    private final Votifier feature;
    private final ORMContext orm;

    public VoteStatsService(Votifier feature, ORMContext orm) {
        this.feature = feature;
        this.orm = orm;
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

    public int monthVotesFromMonthly(long playerId, int yyyymm) {
        if (playerId <= 0 || yyyymm <= 0) return 0;
        return orm.runInTransaction(session -> {
            PlayerVoteMonthlyEntity row = session.get(PlayerVoteMonthlyEntity.class, new PlayerVoteMonthlyKey(playerId, yyyymm));
            return row == null ? 0 : row.getMonthVotes();
        });
    }

    public void recordVote(PlayerEntity player, Vote vote, VotifierConfig cfg) {
        if (player == null) return;

        long nowMillis = System.currentTimeMillis();
        int currentYm = toYearMonthInt(YearMonth.from(ZonedDateTime.now(cfg.statsZone())));

        orm.runInTransaction(session -> {
            Long playerIdObj = player.getId();
            long playerId = playerIdObj == null ? 0L : playerIdObj;
            if (playerId <= 0) return null;

            PlayerEntity managedPlayer = session.get(PlayerEntity.class, playerId, LockMode.PESSIMISTIC_WRITE);
            if (managedPlayer == null) return null;

            PlayerVoteStatsEntity stats = session.get(PlayerVoteStatsEntity.class, playerId, LockMode.PESSIMISTIC_WRITE);
            if (stats == null) {
                stats = new PlayerVoteStatsEntity(managedPlayer);

                long totalFromMonthly = safeLong(session.createQuery(
                                "select coalesce(sum(m.monthVotes), 0) from PlayerVoteMonthlyEntity m where m.id.playerId = :pid",
                                Long.class
                        )
                        .setParameter("pid", playerId)
                        .uniqueResult());

                int highestFromMonthly = safeInt(session.createQuery(
                                "select coalesce(max(m.monthVotes), 0) from PlayerVoteMonthlyEntity m where m.id.playerId = :pid",
                                Integer.class
                        )
                        .setParameter("pid", playerId)
                        .uniqueResult());

                Integer existingCurrentMonthVotes = session.createQuery(
                                "select m.monthVotes from PlayerVoteMonthlyEntity m where m.id.playerId = :pid and m.id.monthYearMonth = :mo",
                                Integer.class
                        )
                        .setParameter("pid", playerId)
                        .setParameter("mo", currentYm)
                        .setMaxResults(1)
                        .uniqueResultOptional()
                        .orElse(0);

                stats.setTotalVotes(Math.max(0L, totalFromMonthly));
                stats.setHighestMonthVotes(Math.max(0, highestFromMonthly));

                stats.setMonthYearMonth(currentYm);
                stats.setMonthVotes(Math.max(0, existingCurrentMonthVotes));

                session.persist(stats);
            }

            if (stats.getMonthYearMonth() != currentYm) {
                stats.setMonthYearMonth(currentYm);
                stats.setMonthVotes(0);
            }

            long lastVoteAt = stats.getLastVoteAt();
            long gapMillis = Duration.ofHours(Math.max(1, cfg.streakGapHours())).toMillis();

            int newStreak;
            if (lastVoteAt > 0 && (nowMillis - lastVoteAt) <= gapMillis) {
                newStreak = stats.getVoteStreak() + 1;
            } else {
                newStreak = 1;
            }
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

            PlayerVoteMonthlyKey key = new PlayerVoteMonthlyKey(playerId, currentYm);
            PlayerVoteMonthlyEntity month = session.get(PlayerVoteMonthlyEntity.class, key, LockMode.PESSIMISTIC_WRITE);
            if (month == null) {
                month = new PlayerVoteMonthlyEntity(playerId, currentYm);
                session.persist(month);
            }

            month.setMonthVotes(newMonthVotes);

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
                                        "order by s.monthVotes desc, s.playerId asc",
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
                                        "and s.playerId = m.id.playerId " +
                                        "and m.monthVotes > 0 " +
                                        "order by m.monthVotes desc, m.id.playerId asc",
                                VoteLeaderboardEntry.class
                        )
                        .setParameter("m", yyyymm)
                        .setMaxResults(lim)
                        .list()
        );
    }

    /**
     * Finalize top 3 for a finished month and apply lifetime medals exactly once.
     *
     * Guarantees:
     * - final_rank is set for the top 3 rows of that month
     * - medals are incremented in player_vote_stats exactly once, guarded by medal_applied in player_vote_monthly
     * - safe to call multiple times, safe across multiple proxies
     */
    public int applyMonthlyWinners(int yyyymm) {
        if (yyyymm <= 0) return 0;

        return orm.runInTransaction(session -> {
            List<VoteLeaderboardEntry> top3 = session.createQuery(
                            "select new nl.hauntedmc.proxyfeatures.features.votifier.internal.VoteLeaderboardEntry(" +
                                    "m.id.playerId, p.username, m.monthVotes, 0) " +
                                    "from PlayerVoteMonthlyEntity m join m.player p " +
                                    "where m.id.monthYearMonth = :m and m.monthVotes > 0 " +
                                    "order by m.monthVotes desc, m.id.playerId asc",
                            VoteLeaderboardEntry.class
                    )
                    .setParameter("m", yyyymm)
                    .setMaxResults(3)
                    .list();

            int medalsApplied = 0;

            int rank = 1;
            for (VoteLeaderboardEntry e : top3) {
                if (rank > 3) break;

                long pid = e.playerId();
                if (pid <= 0) {
                    rank++;
                    continue;
                }

                session.createQuery(
                                "update PlayerVoteMonthlyEntity m " +
                                        "set m.finalRank = :r " +
                                        "where m.id.playerId = :pid and m.id.monthYearMonth = :mo")
                        .setParameter("r", rank)
                        .setParameter("pid", pid)
                        .setParameter("mo", yyyymm)
                        .executeUpdate();

                int claimed = session.createQuery(
                                "update PlayerVoteMonthlyEntity m " +
                                        "set m.medalApplied = true " +
                                        "where m.id.playerId = :pid and m.id.monthYearMonth = :mo and m.medalApplied = false")
                        .setParameter("pid", pid)
                        .setParameter("mo", yyyymm)
                        .executeUpdate();

                if (claimed == 1) {
                    int updatedStats = switch (rank) {
                        case 1 -> session.createQuery(
                                        "update PlayerVoteStatsEntity s set s.firstPlaces = s.firstPlaces + 1 where s.playerId = :pid")
                                .setParameter("pid", pid)
                                .executeUpdate();
                        case 2 -> session.createQuery(
                                        "update PlayerVoteStatsEntity s set s.secondPlaces = s.secondPlaces + 1 where s.playerId = :pid")
                                .setParameter("pid", pid)
                                .executeUpdate();
                        case 3 -> session.createQuery(
                                        "update PlayerVoteStatsEntity s set s.thirdPlaces = s.thirdPlaces + 1 where s.playerId = :pid")
                                .setParameter("pid", pid)
                                .executeUpdate();
                        default -> 0;
                    };

                    if (updatedStats != 1) {
                        throw new IllegalStateException("Failed to increment medal stats for playerId=" + pid + " month=" + yyyymm + " rank=" + rank);
                    }

                    medalsApplied += 1;
                }

                rank++;
            }

            return medalsApplied;
        });
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

    /**
     * Claims exactly one pending month notification for this username.
     *
     * Pending means:
     * - notify not sent yet, or reward not granted yet (only relevant for top 3)
     *
     * Finalization safety:
     * - only months that already have at least one row with final_rank 1..3 are considered finalized
     *
     * Lock safety:
     * - processing_at allows recovering from stale locks after a crash
     */
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
                                    "and (m.processing = false or m.processingAt < :staleBefore) " +
                                    "and (m.notifySent = false or (m.finalRank > 0 and m.finalRank <= 3 and m.rewardGranted = false)) " +
                                    "and exists (select 1 from PlayerVoteMonthlyEntity w where w.id.monthYearMonth = m.id.monthYearMonth and w.finalRank > 0 and w.finalRank <= 3) " +
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
            if (rank <= 0) {
                rank = computeFinalRank(session, month, votes, playerId);
            }

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
                                    "and (m.notifySent = false or (m.finalRank > 0 and m.finalRank <= 3 and m.rewardGranted = false))")
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
                                    "where m.id.playerId = :pid and m.id.monthYearMonth = :mo")
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
                                    "where m.id.playerId = :pid and m.id.monthYearMonth = :mo")
                    .setParameter("r", safeRank)
                    .setParameter("ns", notifySentNow)
                    .setParameter("rg", rewardGrantedNow)
                    .setParameter("pid", playerId)
                    .setParameter("mo", month)
                    .executeUpdate();
            return null;
        });
    }

    private static int computeFinalRank(Session session, int monthYyyymm, int myVotes, long myPlayerId) {
        long higherVotes = safeLong(session.createQuery(
                        "select count(m) from PlayerVoteMonthlyEntity m " +
                                "where m.id.monthYearMonth = :mo and m.monthVotes > :v",
                        Long.class
                )
                .setParameter("mo", monthYyyymm)
                .setParameter("v", myVotes)
                .uniqueResult());

        long tieAhead = safeLong(session.createQuery(
                        "select count(m) from PlayerVoteMonthlyEntity m " +
                                "where m.id.monthYearMonth = :mo and m.monthVotes = :v and m.id.playerId < :pid",
                        Long.class
                )
                .setParameter("mo", monthYyyymm)
                .setParameter("v", myVotes)
                .setParameter("pid", myPlayerId)
                .uniqueResult());

        long rank = 1L + higherVotes + tieAhead;
        if (rank <= 0) return 0;
        if (rank > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) rank;
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

    private static int toYearMonthInt(YearMonth ym) {
        if (ym == null) return 0;
        return ym.getYear() * 100 + ym.getMonthValue();
    }

    private static String sanitize(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() > max) t = t.substring(0, max);
        return t;
    }

    private static long safeLong(Long v) {
        return v == null ? 0L : v;
    }

    private static int safeInt(Integer v) {
        return v == null ? 0 : v;
    }
}