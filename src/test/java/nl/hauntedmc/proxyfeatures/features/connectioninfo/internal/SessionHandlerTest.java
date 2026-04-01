package nl.hauntedmc.proxyfeatures.features.connectioninfo.internal;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionHandlerTest {

    @Test
    void recordLookupAndClearJoinTimes() {
        SessionHandler handler = new SessionHandler();
        UUID id = UUID.randomUUID();

        assertTrue(handler.getJoinTime(id).isEmpty());

        handler.recordJoin(id);
        Instant first = handler.getJoinTime(id).orElseThrow();
        assertNotNull(first);

        handler.recordJoin(id);
        Instant second = handler.getJoinTime(id).orElseThrow();
        assertTrue(!second.isBefore(first));

        handler.clearJoin(id);
        assertTrue(handler.getJoinTime(id).isEmpty());
    }
}
