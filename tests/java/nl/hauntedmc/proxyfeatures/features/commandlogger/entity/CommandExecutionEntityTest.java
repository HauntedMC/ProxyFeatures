package nl.hauntedmc.proxyfeatures.features.commandlogger.entity;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CommandExecutionEntityTest {

    @Test
    void gettersAndSettersStoreValues() throws Exception {
        CommandExecutionEntity entity = new CommandExecutionEntity();
        PlayerEntity player = mock(PlayerEntity.class);

        entity.setServer("proxy");
        entity.setPlayer(player);
        entity.setSource("console");
        entity.setCommand("velocity info");
        entity.setTimestamp(12345L);

        setPrivateField(entity, "id", 77L);
        assertEquals(77L, entity.getId());
        assertEquals("proxy", entity.getServer());
        assertSame(player, entity.getPlayer());
        assertEquals("console", entity.getSource());
        assertEquals("velocity info", entity.getCommand());
        assertEquals(12345L, entity.getTimestamp());
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
