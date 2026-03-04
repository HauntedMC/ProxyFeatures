package nl.hauntedmc.proxyfeatures.features.votifier.internal;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteMonthlyEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteMonthlyKey;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteStatsEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.model.Vote;
import org.hibernate.LockMode;

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

    /**
     * Record a vote.
     *
     * Source of truth:
     * - player_vote_stats is authoritative for totals, streaks, current month marker, current month votes, highest month
     * - player_vote_monthly is a mirror for monthVotes per (player, yyyymm) and stores winner processing flags
     *
     * Concurrency:
     * - Locks PlayerEntity and PlayerVoteStatsEntity with PESSIMISTIC_WRITE to avoid lost updates per player.
     */
    public void recordVote(PlayerEntity player, Vote vote, VotifierConfig cfg) {
        if (player == null) return;

        long nowMillis = System.currentTimeMillis();
        int currentYm = toYearMonthInt(YearMonth.from(ZonedDateTime.now(cfg.statsZone())));

        orm.runInTransaction(session -> {
            Long playerIdObj = player.getId();
            long playerId = playerIdObj == null ? 0L : playerIdObj;
            if (playerId <= 0) return null;

            // Lock the player row first so first-time stats creation is safe under concurrency.
            PlayerEntity managedPlayer = session.get(PlayerEntity.class, playerId, LockMode.PESSIMISTIC_WRITE);
            if (managedPlayer == null) return null;

            // Lock stats row (authoritative)
            PlayerVoteStatsEntity stats = session.get(PlayerVoteStatsEntity.class, playerId, LockMode.PESSIMISTIC_WRITE);
            if (stats == null) {
                stats = new PlayerVoteStatsEntity(managedPlayer);

                // Bootstrap from monthly if it already has data (migration safety).
                // This prevents data loss if monthly exists but stats did not.
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

                // lastVoteAt, streaks remain 0 because we cannot reconstruct safely.
                session.persist(stats);
            }

            // Month marker and monthVotes are authoritative in stats.
            if (stats.getMonthYearMonth() != currentYm) {
                stats.setMonthYearMonth(currentYm);
                stats.setMonthVotes(0);
            }

            // Streak (authoritative)
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

            // Totals (authoritative)
            stats.setTotalVotes(stats.getTotalVotes() + 1);

            // Last vote data (authoritative)
            stats.setLastVoteAt(nowMillis);
            stats.setLastVoteService(sanitize(vote == null ? null : vote.serviceName(), 64));
            stats.setLastVoteAddress(sanitize(vote == null ? null : vote.address(), 64));

            // Current month votes (authoritative)
            int newMonthVotes = stats.getMonthVotes() + 1;
            stats.setMonthVotes(newMonthVotes);

            // Highest month votes (authoritative)
            if (newMonthVotes > stats.getHighestMonthVotes()) {
                stats.setHighestMonthVotes(newMonthVotes);
            }

            // Mirror into monthly table for (player, yyyymm)
            PlayerVoteMonthlyKey key = new PlayerVoteMonthlyKey(playerId, currentYm);

            // Lock monthly row too (keeps mirror consistent if any external process touches it)
            PlayerVoteMonthlyEntity month = session.get(PlayerVoteMonthlyEntity.class, key, LockMode.PESSIMISTIC_WRITE);
            if (month == null) {
                month = new PlayerVoteMonthlyEntity(playerId, currentYm);
                session.persist(month);
            }

            // Mirror: monthly must match authoritative stats monthVotes for this month.
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
     * Finalize winners for a finished month.
     *
     * Guarantees:
     * - winner_rank is set for the top 3 rows of that month
     * - medals are incremented in player_vote_stats exactly once, guarded by winner_medal_applied in player_vote_monthly
     * - safe to call multiple times, safe across multiple proxies
     */
    public int applyMonthlyWinners(int yyyymm) {
        if (yyyymm <= 0) return 0;

        return orm.runInTransaction(session -> {
            List<VoteLeaderboardEntry> top3 = session.createQuery(
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

                // Always set the winner rank, idempotent
                session.createQuery(
                                "update PlayerVoteMonthlyEntity m " +
                                        "set m.winnerRank = :r " +
                                        "where m.id.playerId = :pid and m.id.monthYearMonth = :mo")
                        .setParameter("r", rank)
                        .setParameter("pid", pid)
                        .setParameter("mo", yyyymm)
                        .executeUpdate();

                // Claim medal application exactly once
                int claimed = session.createQuery(
                                "update PlayerVoteMonthlyEntity m " +
                                        "set m.winnerMedalApplied = true " +
                                        "where m.id.playerId = :pid and m.id.monthYearMonth = :mo and m.winnerMedalApplied = false")
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
     * Claims exactly one pending winner notification for this username.
     * Uses player_vote_monthly as the single source of truth.
     *
     * Locking:
     * - winner_processing is a lightweight lock to avoid multiple proxies processing the same month-row at the same time
     */
    public Optional<VoteWinnerNotification> claimPendingWinnerByUsername(String username) {
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
            List<PlayerVoteMonthlyEntity> candidates = session.createQuery(
                            "from PlayerVoteMonthlyEntity m " +
                                    "where m.id.playerId = :pid " +
                                    "and m.winnerRank > 0 " +
                                    "and m.winnerProcessing = false " +
                                    "and (m.winnerCongratsSent = false or m.winnerRewardGranted = false) " +
                                    "order by m.id.monthYearMonth desc",
                            PlayerVoteMonthlyEntity.class
                    )
                    .setParameter("pid", playerId)
                    .setMaxResults(1)
                    .list();

            if (candidates.isEmpty()) return Optional.empty();

            PlayerVoteMonthlyEntity row = candidates.getFirst();
            int month = row.getMonthYearMonth();
            int rank = row.getWinnerRank();
            int votes = row.getMonthVotes();

            boolean needsCongrats = !row.isWinnerCongratsSent();
            boolean needsReward = !row.isWinnerRewardGranted();

            int locked = session.createQuery(
                            "update PlayerVoteMonthlyEntity m " +
                                    "set m.winnerProcessing = true " +
                                    "where m.id.playerId = :pid and m.id.monthYearMonth = :mo " +
                                    "and m.winnerProcessing = false " +
                                    "and m.winnerRank > 0 " +
                                    "and (m.winnerCongratsSent = false or m.winnerRewardGranted = false)")
                    .setParameter("pid", playerId)
                    .setParameter("mo", month)
                    .executeUpdate();

            if (locked != 1) return Optional.empty();

            return Optional.of(new VoteWinnerNotification(
                    playerId,
                    displayName,
                    month,
                    rank,
                    votes,
                    needsCongrats,
                    needsReward
            ));
        });
    }

    public void releaseWinnerProcessing(long playerId, int month) {
        if (playerId <= 0 || month <= 0) return;

        orm.runInTransaction(session -> {
            session.createQuery(
                            "update PlayerVoteMonthlyEntity m " +
                                    "set m.winnerProcessing = false " +
                                    "where m.id.playerId = :pid and m.id.monthYearMonth = :mo")
                    .setParameter("pid", playerId)
                    .setParameter("mo", month)
                    .executeUpdate();
            return null;
        });
    }

    public void completeWinnerProcessing(long playerId, int month, boolean congratsSentNow, boolean rewardGrantedNow) {
        if (playerId <= 0 || month <= 0) return;

        orm.runInTransaction(session -> {
            // Only ever move flags from false to true, never back to false
            session.createQuery(
                            "update PlayerVoteMonthlyEntity m " +
                                    "set m.winnerProcessing = false, " +
                                    "m.winnerCongratsSent = (case when m.winnerCongratsSent = true then true else :cs end), " +
                                    "m.winnerRewardGranted = (case when m.winnerRewardGranted = true then true else :rg end) " +
                                    "where m.id.playerId = :pid and m.id.monthYearMonth = :mo")
                    .setParameter("cs", congratsSentNow)
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