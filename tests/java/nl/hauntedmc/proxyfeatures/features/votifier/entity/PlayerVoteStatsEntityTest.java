package nl.hauntedmc.proxyfeatures.features.votifier.entity;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerVoteStatsEntityTest {

    @Test
    void constructorInitializesExpectedDefaults() {
        PlayerEntity player = mock(PlayerEntity.class);
        when(player.getId()).thenReturn(15L);

        PlayerVoteStatsEntity stats = new PlayerVoteStatsEntity(player);

        assertEquals(15L, stats.getPlayerId());
        assertSame(player, stats.getPlayer());
        assertEquals(0, stats.getMonthYearMonth());
        assertEquals(0, stats.getMonthVotes());
        assertEquals(0L, stats.getTotalVotes());
        assertEquals(0, stats.getHighestMonthVotes());
        assertEquals(0L, stats.getLastVoteAt());
        assertEquals("", stats.getLastVoteService());
        assertEquals("", stats.getLastVoteAddress());
        assertEquals(0, stats.getVoteStreak());
        assertEquals(0, stats.getBestVoteStreak());
        assertTrue(stats.isVoteRemindEnabled());
        assertEquals(0, stats.getFirstPlaces());
        assertEquals(0, stats.getSecondPlaces());
        assertEquals(0, stats.getThirdPlaces());
    }

    @Test
    void mutableFieldsSupportNullSafeAndRegularUpdates() {
        PlayerVoteStatsEntity stats = new PlayerVoteStatsEntity();

        stats.setMonthYearMonth(202603);
        stats.setMonthVotes(12);
        stats.setTotalVotes(99L);
        stats.setHighestMonthVotes(20);
        stats.setLastVoteAt(1_234_567L);
        stats.setLastVoteService(null);
        stats.setLastVoteAddress(null);
        stats.setVoteStreak(5);
        stats.setBestVoteStreak(11);
        stats.setVoteRemindEnabled(false);
        stats.setFirstPlaces(1);
        stats.setSecondPlaces(2);
        stats.setThirdPlaces(3);

        assertEquals(202603, stats.getMonthYearMonth());
        assertEquals(12, stats.getMonthVotes());
        assertEquals(99L, stats.getTotalVotes());
        assertEquals(20, stats.getHighestMonthVotes());
        assertEquals(1_234_567L, stats.getLastVoteAt());
        assertEquals("", stats.getLastVoteService());
        assertEquals("", stats.getLastVoteAddress());
        assertEquals(5, stats.getVoteStreak());
        assertEquals(11, stats.getBestVoteStreak());
        assertFalse(stats.isVoteRemindEnabled());
        assertEquals(1, stats.getFirstPlaces());
        assertEquals(2, stats.getSecondPlaces());
        assertEquals(3, stats.getThirdPlaces());

        stats.setLastVoteService("PlanetMinecraft");
        stats.setLastVoteAddress("192.168.0.10");
        assertEquals("PlanetMinecraft", stats.getLastVoteService());
        assertEquals("192.168.0.10", stats.getLastVoteAddress());
    }
}
