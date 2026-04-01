package nl.hauntedmc.proxyfeatures.features.votifier.internal;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;
import nl.hauntedmc.proxyfeatures.features.votifier.messaging.EventBusHandler;
import nl.hauntedmc.proxyfeatures.features.votifier.messaging.VoteMessage;
import nl.hauntedmc.proxyfeatures.features.votifier.model.Vote;
import nl.hauntedmc.proxyfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.proxyfeatures.framework.lifecycle.FeatureTaskManager;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VotifierServiceTest {

    @Test
    void publishTestVotePublishesToConfiguredChannelsAndSanitizesValues() {
        EventBusHandler bus = mock(EventBusHandler.class);
        VotifierService service = new VotifierService(
                feature(),
                config(true, true, true, " proxy.votes ", " vote "),
                bus,
                null,
                new DirectExecutorService()
        );

        boolean ok = service.publishTestVote("  ServiceX  ", "  Remy_01  ");

        assertTrue(ok);
        verify(bus).publishVote(argThat(msg -> messageEquals(msg, "ServiceX", "Remy_01", "test")), eq("vote"));
        verify(bus).publishVote(argThat(msg -> messageEquals(msg, "ServiceX", "Remy_01", "test")), eq("proxy.votes"));
    }

    @Test
    void publishTestVoteReturnsFalseWhenInputInvalidOrRedisDisabled() {
        EventBusHandler bus = mock(EventBusHandler.class);

        VotifierService redisOff = new VotifierService(
                feature(),
                config(true, false, true, "proxy.votes", "vote"),
                bus,
                null,
                new DirectExecutorService()
        );
        assertFalse(redisOff.publishTestVote("svc", "Remy"));

        VotifierService blankService = new VotifierService(
                feature(),
                config(true, true, true, "proxy.votes", "vote"),
                bus,
                null,
                new DirectExecutorService()
        );
        assertFalse(blankService.publishTestVote("   ", "Remy"));
        assertFalse(blankService.publishTestVote("svc", "   "));

        verifyNoInteractions(bus);
    }

    @Test
    void processVoteRejectsInvalidUsernameBeforeStatsAndRedis() {
        EventBusHandler bus = mock(EventBusHandler.class);
        VoteStatsService stats = mock(VoteStatsService.class);
        VotifierService service = new VotifierService(
                feature(),
                config(true, true, true, "proxy.votes", "vote"),
                bus,
                stats,
                new DirectExecutorService()
        );

        service.processVote(new Vote("service", "bad name!", "1.2.3.4", 1L));

        verifyNoInteractions(stats);
        verifyNoInteractions(bus);
    }

    @Test
    void processVoteWithUnknownPlayerStopsBeforeRedisPublish() {
        EventBusHandler bus = mock(EventBusHandler.class);
        VoteStatsService stats = mock(VoteStatsService.class);
        when(stats.rolloverIfNeeded(any())).thenReturn(Optional.empty());
        when(stats.findPlayerByUsername("Remy")).thenReturn(Optional.empty());

        VotifierService service = new VotifierService(
                feature(),
                config(true, true, true, "proxy.votes", "vote"),
                bus,
                stats,
                new DirectExecutorService()
        );

        service.processVote(new Vote("service", "Remy", "1.2.3.4", 123L));

        verify(stats).rolloverIfNeeded(any(VotifierConfig.class));
        verify(stats).findPlayerByUsername("Remy");
        verify(stats, never()).recordVote(any(), any(), any());
        verifyNoInteractions(bus);
    }

    @Test
    void processVoteWithKnownPlayerRecordsStatsAndPublishesCanonicalUsername() {
        EventBusHandler bus = mock(EventBusHandler.class);
        VoteStatsService stats = mock(VoteStatsService.class);
        when(stats.rolloverIfNeeded(any())).thenReturn(Optional.empty());

        PlayerEntity player = new PlayerEntity();
        player.setId(99L);
        player.setUsername("CanonicalName");
        when(stats.findPlayerByUsername("Remy")).thenReturn(Optional.of(player));

        VotifierConfig cfg = config(true, true, true, "proxy.votes", "vote");
        VotifierService service = new VotifierService(
                feature(),
                cfg,
                bus,
                stats,
                new DirectExecutorService()
        );

        Vote raw = new Vote("PlanetMinecraft", "Remy", "5.6.7.8", 1712345678L);
        service.processVote(raw);

        verify(stats).recordVote(eq(player), eq(raw), eq(cfg));
        verify(bus).publishVote(argThat(msg -> messageEquals(msg, "PlanetMinecraft", "CanonicalName", "5.6.7.8")
                && msg.getVoteTimestamp() == 1712345678000L), eq("vote"));
        verify(bus).publishVote(argThat(msg -> messageEquals(msg, "PlanetMinecraft", "CanonicalName", "5.6.7.8")
                && msg.getVoteTimestamp() == 1712345678000L), eq("proxy.votes"));
    }

    @Test
    void processVoteWithoutStatsStillPublishesAndClampsFutureTimestamp() {
        EventBusHandler bus = mock(EventBusHandler.class);
        VoteStatsService stats = mock(VoteStatsService.class);
        VotifierService service = new VotifierService(
                feature(),
                config(false, true, true, "proxy.votes", "vote"),
                bus,
                stats,
                new DirectExecutorService()
        );

        long before = System.currentTimeMillis();
        long future = before + Duration.ofDays(30).toMillis();
        service.processVote(new Vote("site", "Remy", "1.2.3.4", future));
        long after = System.currentTimeMillis();

        verifyNoInteractions(stats);
        verify(bus).publishVote(argThat(msg -> messageEquals(msg, "site", "Remy", "1.2.3.4")
                && msg.getVoteTimestamp() >= before
                && msg.getVoteTimestamp() <= after), eq("vote"));
        verify(bus).publishVote(argThat(msg -> messageEquals(msg, "site", "Remy", "1.2.3.4")
                && msg.getVoteTimestamp() >= before
                && msg.getVoteTimestamp() <= after), eq("proxy.votes"));
    }

    @Test
    void topForMonthUsesCurrentOrHistoricalQueriesAsExpected() {
        VoteStatsService stats = mock(VoteStatsService.class);
        when(stats.rolloverIfNeeded(any())).thenReturn(Optional.empty());

        VotifierService service = new VotifierService(
                feature(),
                config(true, true, true, "proxy.votes", "vote"),
                null,
                stats,
                new DirectExecutorService()
        );

        YearMonth current = YearMonth.now(ZoneId.of("UTC"));
        YearMonth previous = current.minusMonths(1);
        int currentYyyymm = current.getYear() * 100 + current.getMonthValue();
        int previousYyyymm = previous.getYear() * 100 + previous.getMonthValue();

        when(stats.topForCurrentMonth(currentYyyymm, 10)).thenReturn(List.of());
        when(stats.topForHistoricalMonth(previousYyyymm, 10)).thenReturn(List.of());

        service.topForMonth(current, 10);
        service.topForMonth(previous, 10);

        verify(stats).topForCurrentMonth(currentYyyymm, 10);
        verify(stats).topForHistoricalMonth(previousYyyymm, 10);
        verify(stats).rolloverIfNeeded(any(VotifierConfig.class));
    }

    @Test
    void dumpTopForMonthDefaultsToPreviousMonthAndConfiguredLimitPrefix() {
        VoteStatsService stats = mock(VoteStatsService.class);
        when(stats.rolloverIfNeeded(any())).thenReturn(Optional.empty());
        when(stats.dumpTopForMonth(any(), anyInt(), eq(100), eq("votifier-top100")))
                .thenReturn(java.nio.file.Path.of("dump-file.txt"));

        VotifierService service = new VotifierService(
                feature(),
                config(true, true, true, "proxy.votes", "vote"),
                null,
                stats,
                new DirectExecutorService()
        );

        String out = service.dumpTopForMonth(null);
        assertEquals("dump-file.txt", out);
        verify(stats).dumpTopForMonth(any(YearMonth.class), anyInt(), eq(100), eq("votifier-top100"));
    }

    @Test
    void getPlayerStatsReturnsDefaultViewWhenStatsRowMissing() {
        VoteStatsService stats = mock(VoteStatsService.class);
        PlayerEntity player = new PlayerEntity();
        player.setId(4L);
        player.setUsername("Remy");
        when(stats.findPlayerByUsername("Remy")).thenReturn(Optional.of(player));
        when(stats.findStats(4L)).thenReturn(Optional.empty());

        VotifierService service = new VotifierService(
                feature(),
                config(true, true, true, "proxy.votes", "vote"),
                null,
                stats,
                new DirectExecutorService()
        );

        Optional<VotePlayerStatsView> out = service.getPlayerStats("Remy");
        assertTrue(out.isPresent());
        assertEquals("Remy", out.get().username());
        assertEquals(0, out.get().monthVotes());
        assertEquals(0L, out.get().totalVotes());
    }

    @Test
    void setVoteRemindSchedulesAndCancelsReminderTasks() {
        FeatureTaskManager tasks = mock(FeatureTaskManager.class);
        ScheduledTask task = mock(ScheduledTask.class);
        when(tasks.scheduleRepeatingTask(any(Runnable.class), any(Duration.class), any(Duration.class))).thenReturn(task);

        Votifier feature = feature(tasks);
        VoteStatsService stats = mock(VoteStatsService.class);
        when(stats.setVoteRemindEnabledByUsername(eq("Remy"), anyInt(), eq(VoteStatsService.RemindMode.ON)))
                .thenReturn(Optional.of(true));
        when(stats.setVoteRemindEnabledByUsername(eq("Remy"), anyInt(), eq(VoteStatsService.RemindMode.OFF)))
                .thenReturn(Optional.of(false));

        VotifierService service = new VotifierService(
                feature,
                config(true, true, true, "proxy.votes", "vote"),
                null,
                stats,
                new DirectExecutorService()
        );

        Player player = mock(Player.class);
        when(player.getUsername()).thenReturn("Remy");
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        Optional<Boolean> enabled = service.setVoteRemind(player, VotifierService.RemindMode.ON);
        Optional<Boolean> disabled = service.setVoteRemind(player, VotifierService.RemindMode.OFF);

        assertTrue(enabled.isPresent());
        assertTrue(enabled.get());
        assertTrue(disabled.isPresent());
        assertFalse(disabled.get());
        verify(tasks).scheduleRepeatingTask(any(Runnable.class), any(Duration.class), any(Duration.class));
        verify(tasks).cancelTask(task);
    }

    private static Votifier feature() {
        return feature(null);
    }

    private static Votifier feature(FeatureTaskManager taskManager) {
        Votifier feature = mock(Votifier.class);
        when(feature.getLogger()).thenReturn(mock(FeatureLogger.class));
        if (taskManager != null) {
            FeatureLifecycleManager lifecycle = mock(FeatureLifecycleManager.class);
            when(lifecycle.getTaskManager()).thenReturn(taskManager);
            when(feature.getLifecycleManager()).thenReturn(lifecycle);
        }
        return feature;
    }

    private static boolean messageEquals(VoteMessage msg, String service, String user, String address) {
        if (msg == null) return false;
        return service.equals(msg.getServiceName())
                && user.equals(msg.getUsername())
                && address.equals(msg.getAddress());
    }

    private static VotifierConfig config(
            boolean statsEnabled,
            boolean redisEnabled,
            boolean publishLegacy,
            String redisChannel,
            String legacyChannel
    ) {
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
                redisEnabled,
                redisChannel,
                publishLegacy,
                legacyChannel,
                statsEnabled,
                ZoneId.of("UTC"),
                5,
                36,
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

    private static final class DirectExecutorService extends AbstractExecutorService {
        private volatile boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            if (command != null) {
                command.run();
            }
        }
    }
}
