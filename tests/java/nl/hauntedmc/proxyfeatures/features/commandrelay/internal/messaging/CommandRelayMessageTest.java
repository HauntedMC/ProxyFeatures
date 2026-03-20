package nl.hauntedmc.proxyfeatures.features.commandrelay.internal.messaging;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class CommandRelayMessageTest {

    @Test
    void publicConstructorStoresPayload() {
        CommandRelayMessage msg = new CommandRelayMessage("/say hello", "hub-1");
        assertEquals("/say hello", msg.getCommand());
        assertEquals("hub-1", msg.getOriginServer());
    }

    @Test
    void gsonConstructorProvidesNullDefaults() throws Exception {
        Constructor<CommandRelayMessage> ctor = CommandRelayMessage.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        CommandRelayMessage msg = ctor.newInstance();

        assertNull(msg.getCommand());
        assertNull(msg.getOriginServer());
    }
}
