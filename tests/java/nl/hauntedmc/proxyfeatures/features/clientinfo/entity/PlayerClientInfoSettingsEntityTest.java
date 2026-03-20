package nl.hauntedmc.proxyfeatures.features.clientinfo.entity;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerClientInfoSettingsEntityTest {

    @Test
    void defaultAndMutatedNotifyFlagBehaveAsExpected() {
        PlayerClientInfoSettingsEntity settings = new PlayerClientInfoSettingsEntity();
        assertTrue(settings.isNotifyEnabled());

        settings.setNotifyEnabled(false);
        assertFalse(settings.isNotifyEnabled());
    }

    @Test
    void constructorBindsPlayerIdentityFields() throws Exception {
        PlayerEntity player = mock(PlayerEntity.class);
        when(player.getId()).thenReturn(321L);

        PlayerClientInfoSettingsEntity settings = new PlayerClientInfoSettingsEntity(player);
        assertTrue(settings.isNotifyEnabled());

        assertEquals(321L, getPrivateField(settings, "playerId"));
        assertSame(player, getPrivateField(settings, "player"));
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
