package nl.hauntedmc.proxyfeatures.features.queue.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QueueModelPrimitivesTest {

    @Test
    void enqueueDecisionConstantsExposeExpectedFlags() {
        assertEquals(new EnqueueDecision(true, false), EnqueueDecision.ALLOW);
        assertEquals(new EnqueueDecision(true, true), EnqueueDecision.ALLOW_BYPASS);
        assertEquals(new EnqueueDecision(false, false), EnqueueDecision.DENY_QUEUED);
    }

    @Test
    void serverStatusFactoriesProvideExpectedShape() {
        ServerStatus online = ServerStatus.online(12, 100);
        assertTrue(online.isOnline());
        assertEquals(12, online.onlinePlayers);
        assertEquals(100, online.maxPlayers);

        ServerStatus offline = ServerStatus.offline();
        assertFalse(offline.isOnline());
        assertEquals(-1, offline.onlinePlayers);
        assertEquals(-1, offline.maxPlayers);

        ServerStatus unknown = ServerStatus.unknown();
        assertFalse(unknown.isOnline());
        assertEquals(-1, unknown.onlinePlayers);
        assertEquals(-1, unknown.maxPlayers);
    }
}
