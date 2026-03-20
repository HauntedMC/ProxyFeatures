package nl.hauntedmc.proxyfeatures.features.messager.entity;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerMessageSettingsEntityTest {

    @Test
    void constructorInitializesDefaultsAndPlayerMapping() {
        PlayerEntity player = mock(PlayerEntity.class);
        when(player.getId()).thenReturn(42L);

        PlayerMessageSettingsEntity settings = new PlayerMessageSettingsEntity(player);

        assertTrue(settings.isMsgToggle());
        assertFalse(settings.isMsgSpy());
        assertTrue(settings.getBlockedPlayers().isEmpty());
    }

    @Test
    void blockAndUnblockFlowUpdatesRelationshipSet() {
        PlayerEntity owner = mock(PlayerEntity.class);
        when(owner.getId()).thenReturn(1L);
        PlayerEntity target = mock(PlayerEntity.class);

        PlayerMessageSettingsEntity settings = new PlayerMessageSettingsEntity(owner);
        settings.block(target);
        settings.block(target);

        assertTrue(settings.isBlocking(target));
        assertEquals(1, settings.getBlockedPlayers().size());

        settings.unblock(target);
        assertFalse(settings.isBlocking(target));
        assertTrue(settings.getBlockedPlayers().isEmpty());
    }

    @Test
    void toggleAndSpyFlagsCanBeUpdated() {
        PlayerEntity owner = mock(PlayerEntity.class);
        when(owner.getId()).thenReturn(5L);
        PlayerMessageSettingsEntity settings = new PlayerMessageSettingsEntity(owner);

        settings.setMsgToggle(false);
        settings.setMsgSpy(true);

        assertFalse(settings.isMsgToggle());
        assertTrue(settings.isMsgSpy());
    }

    @Test
    void noArgConstructorSupportsDefaultState() {
        PlayerMessageSettingsEntity settings = new PlayerMessageSettingsEntity();
        assertTrue(settings.isMsgToggle());
        assertFalse(settings.isMsgSpy());
        assertTrue(settings.getBlockedPlayers().isEmpty());
    }
}
