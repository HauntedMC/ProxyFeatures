package nl.hauntedmc.proxyfeatures.features.staffchat.internal.messaging;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class StaffChatMessageTest {

    @Test
    void publicConstructorStoresPayload() {
        StaffChatMessage msg = new StaffChatMessage("staffchat", "!", "hello", "Remy", "hub");
        assertEquals("!", msg.getPrefix());
        assertEquals("hello", msg.getMessage());
        assertEquals("Remy", msg.getSenderName());
        assertEquals("hub", msg.getSenderServer());
    }

    @Test
    void gsonConstructorProvidesNullDefaults() throws Exception {
        Constructor<StaffChatMessage> ctor = StaffChatMessage.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        StaffChatMessage msg = ctor.newInstance();

        assertNull(msg.getPrefix());
        assertNull(msg.getMessage());
        assertNull(msg.getSenderName());
        assertNull(msg.getSenderServer());
    }
}
