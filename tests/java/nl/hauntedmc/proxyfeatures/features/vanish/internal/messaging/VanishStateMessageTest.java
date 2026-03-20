package nl.hauntedmc.proxyfeatures.features.vanish.internal.messaging;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class VanishStateMessageTest {

    @Test
    void publicConstructorStoresPayload() {
        VanishStateMessage msg = new VanishStateMessage("vanish_update", "uuid", "name", true, "hub");
        assertEquals("uuid", msg.getPlayerUuid());
        assertEquals("name", msg.getPlayerName());
        assertTrue(msg.isVanished());
        assertEquals("hub", msg.getServer());
    }

    @Test
    void gsonConstructorProvidesExpectedDefaults() throws Exception {
        Constructor<VanishStateMessage> ctor = VanishStateMessage.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        VanishStateMessage msg = ctor.newInstance();

        assertNull(msg.getPlayerUuid());
        assertNull(msg.getPlayerName());
        assertFalse(msg.isVanished());
        assertNull(msg.getServer());
    }
}
