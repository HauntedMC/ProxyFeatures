package nl.hauntedmc.proxyfeatures.features.votifier.internal;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteMonthlyEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteMonthlyKey;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteStatsEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.model.Vote;

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

    public void recordVote(PlayerEntity player, Vote vote, VotifierConfig cfg) {
        if (player == null) return;

        long nowMillis = System.currentTimeMillis();
        int currentYm = toYearMonthInt(YearMonth.from(ZonedDateTime.now(cfg.statsZone())));

        orm.runInTransaction(session -> {
            Long playerId = player.getId();

            PlayerVoteStatsEntity stats = session.get(PlayerVoteStatsEntity.class, playerId);
            if (stats == null) {
                PlayerEntity managedPlayer = session.get(PlayerEntity.class, playerId);
                stats = new PlayerVoteStatsEntity(managedPlayer);
                stats.setMonthYearMonth(currentYm);
                session.persist(stats);
            }

            // Streak
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

            // Totals
            stats.setTotalVotes(stats.getTotalVotes() + 1);

            // Last vote data
            stats.setLastVoteAt(nowMillis);
            stats.setLastVoteService(sanitize(vote.serviceName(), 64));
            stats.setLastVoteAddress(sanitize(vote.address(), 64));

            // Monthly history table
            PlayerVoteMonthlyKey key = new PlayerVoteMonthlyKey(playerId, currentYm);
            PlayerVoteMonthlyEntity month = session.get(PlayerVoteMonthlyEntity.class, key);
            if (month == null) {
                month = new PlayerVoteMonthlyEntity(playerId, currentYm);
                session.persist(month);
            }
            int newMonthVotes = month.getMonthVotes() + 1;
            month.setMonthVotes(newMonthVotes);
            session.merge(month);

            // Current month table for fast reads
            stats.setMonthYearMonth(currentYm);
            stats.setMonthVotes(newMonthVotes);

            // Fix: always keep highest_month_votes up to date using the value stored in player_vote_stats
            int curMonthVotes = stats.getMonthVotes();
            if (curMonthVotes > stats.getHighestMonthVotes()) {
                stats.setHighestMonthVotes(curMonthVotes);
            }

            session.merge(stats);
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
                                        "order by s.monthVotes desc",
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
                                        "order by m.monthVotes desc",
                                VoteLeaderboardEntry.class
                        )
                        .setParameter("m", yyyymm)
                        .setMaxResults(lim)
                        .list()
        );
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
}