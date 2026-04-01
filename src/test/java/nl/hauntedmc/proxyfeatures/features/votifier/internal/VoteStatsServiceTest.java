package nl.hauntedmc.proxyfeatures.features.votifier.internal;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;
import nl.hauntedmc.proxyfeatures.features.votifier.entity.PlayerVoteStatsEntity;
import nl.hauntedmc.proxyfeatures.features.votifier.model.Vote;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class VoteStatsServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void recordVoteCreatesMissingStatsAndInitializesCounters() {
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });

        PlayerEntity player = new PlayerEntity();
        player.setId(7L);
        player.setUsername("Remy");

        when(session.get(PlayerEntity.class, 7L, LockMode.PESSIMISTIC_WRITE)).thenReturn(player);
        when(session.get(PlayerVoteStatsEntity.class, 7L, LockMode.PESSIMISTIC_WRITE)).thenReturn(null);

        VoteStatsService service = new VoteStatsService(feature(tempDir), orm, false);
        VotifierConfig cfg = config(ZoneId.of("UTC"), 36);

        service.recordVote(player, new Vote("PlanetMinecraft", "Remy", "1.2.3.4", 123L), cfg);

        var captor = org.mockito.ArgumentCaptor.forClass(PlayerVoteStatsEntity.class);
        verify(session).persist(captor.capture());

        PlayerVoteStatsEntity persisted = captor.getValue();
        assertEquals(7L, persisted.getPlayerId());
        assertEquals(1, persisted.getMonthVotes());
        assertEquals(1L, persisted.getTotalVotes());
        assertEquals(1, persisted.getVoteStreak());
        assertEquals(1, persisted.getBestVoteStreak());
        assertEquals("PlanetMinecraft", persisted.getLastVoteService());
        assertEquals("1.2.3.4", persisted.getLastVoteAddress());
        assertTrue(persisted.getLastVoteAt() > 0);
        assertTrue(persisted.isVoteRemindEnabled());
    }

    @Test
    void recordVoteResetsOutOfSyncMonthAndContinuesStreakWithinGap() {
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });

        PlayerEntity player = new PlayerEntity();
        player.setId(11L);
        player.setUsername("Remy");
        when(session.get(PlayerEntity.class, 11L, LockMode.PESSIMISTIC_WRITE)).thenReturn(player);

        PlayerVoteStatsEntity stats = new PlayerVoteStatsEntity(player);
        stats.setMonthYearMonth(202001);
        stats.setMonthVotes(5);
        stats.setTotalVotes(10L);
        stats.setHighestMonthVotes(7);
        stats.setLastVoteAt(System.currentTimeMillis() - 3_600_000L);
        stats.setVoteStreak(2);
        stats.setBestVoteStreak(2);
        when(session.get(PlayerVoteStatsEntity.class, 11L, LockMode.PESSIMISTIC_WRITE)).thenReturn(stats);

        VoteStatsService service = new VoteStatsService(feature(tempDir, logger), orm, false);
        service.recordVote(player, new Vote("svc", "Remy", "5.6.7.8", 1L), config(ZoneId.of("UTC"), 36));

        int expectedYm = YearMonth.now(ZoneId.of("UTC")).getYear() * 100 + YearMonth.now(ZoneId.of("UTC")).getMonthValue();
        assertEquals(expectedYm, stats.getMonthYearMonth());
        assertEquals(1, stats.getMonthVotes());
        assertEquals(11L, stats.getTotalVotes());
        assertEquals(3, stats.getVoteStreak());
        assertEquals(3, stats.getBestVoteStreak());
        assertEquals(7, stats.getHighestMonthVotes());
        verify(logger).warn(contains("VoteStats out of sync"));
    }

    @Test
    void reminderToggleCreatesMissingStatsAndAppliesMode() {
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });

        PlayerEntity player = new PlayerEntity();
        player.setId(15L);
        player.setUsername("Remy");

        VoteStatsService service = spy(new VoteStatsService(feature(tempDir), orm, false));
        doReturn(Optional.of(player)).when(service).findPlayerByUsername("Remy");

        when(session.get(PlayerEntity.class, 15L, LockMode.PESSIMISTIC_READ)).thenReturn(player);
        when(session.get(PlayerVoteStatsEntity.class, 15L, LockMode.PESSIMISTIC_WRITE)).thenReturn(null);

        Optional<Boolean> out = service.setVoteRemindEnabledByUsername("Remy", 202604, VoteStatsService.RemindMode.TOGGLE);

        assertTrue(out.isPresent());
        assertFalse(out.get());
        var captor = org.mockito.ArgumentCaptor.forClass(PlayerVoteStatsEntity.class);
        verify(session).persist(captor.capture());
        assertEquals(202604, captor.getValue().getMonthYearMonth());
        assertFalse(captor.getValue().isVoteRemindEnabled());
    }

    @Test
    void getReminderStateUsesInputNameFallbackWhenStoredUsernameBlank() {
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });

        PlayerEntity found = new PlayerEntity();
        found.setId(22L);
        found.setUsername(" ");

        PlayerEntity managed = new PlayerEntity();
        managed.setId(22L);
        managed.setUsername(" ");

        PlayerVoteStatsEntity stats = new PlayerVoteStatsEntity(managed);
        stats.setVoteRemindEnabled(false);
        stats.setLastVoteAt(999L);

        VoteStatsService service = spy(new VoteStatsService(feature(tempDir), orm, false));
        doReturn(Optional.of(found)).when(service).findPlayerByUsername("Remy");

        when(session.get(PlayerEntity.class, 22L, LockMode.PESSIMISTIC_READ)).thenReturn(managed);
        when(session.get(PlayerVoteStatsEntity.class, 22L, LockMode.PESSIMISTIC_WRITE)).thenReturn(stats);

        Optional<VoteReminderState> out = service.getReminderStateByUsername("Remy", 202604);

        assertTrue(out.isPresent());
        assertEquals("Remy", out.get().username());
        assertFalse(out.get().remindEnabled());
        assertEquals(999L, out.get().lastVoteAt());
    }

    @Test
    void topForCurrentMonthClampsLimit() {
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<VoteLeaderboardEntry> query = mock(Query.class);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });
        when(session.createQuery(anyString(), eq(VoteLeaderboardEntry.class))).thenReturn(query);
        when(query.setParameter(anyString(), anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.list()).thenReturn(List.of());

        VoteStatsService service = new VoteStatsService(feature(tempDir), orm, false);
        service.topForCurrentMonth(202604, 0);
        service.topForCurrentMonth(202604, 5000);

        verify(query).setMaxResults(1);
        verify(query).setMaxResults(1000);
    }

    @Test
    void dumpTopForMonthWritesExpectedFile() throws Exception {
        VoteStatsService service = spy(new VoteStatsService(feature(tempDir), mock(ORMContext.class), false));

        YearMonth month = YearMonth.of(2026, 3);
        doReturn(List.of(new VoteLeaderboardEntry(1L, "Remy", 9, 42L)))
                .when(service).topForCurrentMonth(202603, 5);

        Path file = service.dumpTopForMonth(month, 202603, 5, "votes");

        assertTrue(Files.exists(file));
        String body = Files.readString(file);
        assertTrue(body.contains("month=2026-03"));
        assertTrue(body.contains("entries=1"));
        assertTrue(body.contains("1. Remy playerId=1 monthVotes=9 totalVotes=42"));
    }

    @Test
    void computeNextStreakResetsOrIncrementsAsExpected() {
        assertEquals(3, VoteStatsService.computeNextStreak(2, 1000L, 2000L, 2000L));
        assertEquals(1, VoteStatsService.computeNextStreak(8, 1000L, 5000L, 1000L));
        assertEquals(1, VoteStatsService.computeNextStreak(8, 0L, 5000L, 1000L));
    }

    private static Votifier feature(Path dataDir) {
        return feature(dataDir, mock(FeatureLogger.class));
    }

    private static Votifier feature(Path dataDir, FeatureLogger logger) {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        when(plugin.getDataDirectory()).thenReturn(dataDir);

        Votifier feature = mock(Votifier.class);
        when(feature.getPlugin()).thenReturn(plugin);
        when(feature.getLogger()).thenReturn(logger);
        return feature;
    }

    private static VotifierConfig config(ZoneId zone, int streakGapHours) {
        return new VotifierConfig(
                "0.0.0.0",
                8199,
                5000,
                50,
                8192,
                true,
                2048,
                "public.key",
                "private.key",
                "",
                "",
                true,
                "proxy.votifier.vote",
                true,
                "vote",
                true,
                zone,
                5,
                streakGapHours,
                100,
                "votifier-top100",
                true,
                true,
                24,
                5,
                60,
                "https://hauntedmc.nl/vote"
        );
    }
}
