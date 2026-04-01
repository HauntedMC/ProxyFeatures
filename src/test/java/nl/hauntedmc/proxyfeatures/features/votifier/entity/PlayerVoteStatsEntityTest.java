package nl.hauntedmc.proxyfeatures.features.votifier.entity;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerVoteStatsEntityTest {

    @Test
    void constructorMapsPlayerAndInitializesSafeDefaults() {
        PlayerEntity player = mock(PlayerEntity.class);
        when(player.getId()).thenReturn(15L);

        PlayerVoteStatsEntity stats = new PlayerVoteStatsEntity(player);

        assertEquals(15L, stats.getPlayerId());
        assertSame(player, stats.getPlayer());
        assertTrue(stats.isVoteRemindEnabled());
        assertEquals("", stats.getLastVoteService());
        assertEquals("", stats.getLastVoteAddress());
    }

    @Test
    void voteServiceAndAddressSettersNormalizeNullToEmptyString() {
        PlayerVoteStatsEntity stats = new PlayerVoteStatsEntity();

        stats.setLastVoteService(null);
        stats.setLastVoteAddress(null);
        assertEquals("", stats.getLastVoteService());
        assertEquals("", stats.getLastVoteAddress());

        stats.setLastVoteService("PlanetMinecraft");
        stats.setLastVoteAddress("192.168.0.10");
        assertEquals("PlanetMinecraft", stats.getLastVoteService());
        assertEquals("192.168.0.10", stats.getLastVoteAddress());
    }

    @Test
    void voteReminderFlagCanBeToggled() {
        PlayerVoteStatsEntity stats = new PlayerVoteStatsEntity();
        assertTrue(stats.isVoteRemindEnabled());

        stats.setVoteRemindEnabled(false);
        assertFalse(stats.isVoteRemindEnabled());
    }
}
