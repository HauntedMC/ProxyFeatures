package nl.hauntedmc.proxyfeatures.features.votifier.messaging;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;

class VoteMessageTest {

    @Test
    void publicConstructorStoresPayload() {
        VoteMessage msg = new VoteMessage("service", "user", "1.2.3.4", 123L);
        assertEquals("service", msg.getServiceName());
        assertEquals("user", msg.getUsername());
        assertEquals("1.2.3.4", msg.getAddress());
        assertEquals(123L, msg.getVoteTimestamp());
    }

    @Test
    void gsonConstructorProvidesExpectedDefaults() throws Exception {
        Constructor<VoteMessage> ctor = VoteMessage.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        VoteMessage msg = ctor.newInstance();

        assertNull(msg.getServiceName());
        assertNull(msg.getUsername());
        assertNull(msg.getAddress());
        assertEquals(0L, msg.getVoteTimestamp());
    }
}
