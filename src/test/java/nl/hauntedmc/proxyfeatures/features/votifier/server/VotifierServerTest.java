package nl.hauntedmc.proxyfeatures.features.votifier.server;

import nl.hauntedmc.proxyfeatures.features.votifier.model.Vote;
import nl.hauntedmc.proxyfeatures.features.votifier.util.IpAccessList;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.PrivateKey;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class VotifierServerTest {

    @Test
    void parseVoteMessageAcceptsValidPayloadAndFallbackTimestamp() {
        Vote valid = VotifierServer.parseVoteMessage("VOTE\nservice\nRemy\n1.2.3.4\n1234\n", 999L);
        assertNotNull(valid);
        assertEquals("service", valid.serviceName());
        assertEquals("Remy", valid.username());
        assertEquals("1.2.3.4", valid.address());
        assertEquals(1234L, valid.timestamp());

        Vote fallback = VotifierServer.parseVoteMessage("VOTE\nservice\nRemy\n1.2.3.4\nnot-a-number\n", 999L);
        assertNotNull(fallback);
        assertEquals(999L, fallback.timestamp());
    }

    @Test
    void parseVoteMessageRejectsInvalidPayloadAndSanitizesFields() {
        assertNull(VotifierServer.parseVoteMessage(null, 1L));
        assertNull(VotifierServer.parseVoteMessage("PING\nsvc\nuser\naddr\n1\n", 1L));
        assertNull(VotifierServer.parseVoteMessage("VOTE\nsvc\nuser\naddr\n", 1L));

        String longField = "x".repeat(180);
        Vote parsed = VotifierServer.parseVoteMessage("VOTE\n" + longField + "\n " + longField + " \n" + longField + "\n1\n", 1L);
        assertNotNull(parsed);
        assertEquals(128, parsed.serviceName().length());
        assertEquals(128, parsed.username().length());
        assertEquals(128, parsed.address().length());
    }

    @Test
    void readExactWithDeadlineReturnsExactBytesAndFailsOnShortOrOversized() throws Exception {
        VotifierServer server = server();

        byte[] exact = server.readExactWithDeadline(new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5, 6}), 4, 8, 1000);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, exact);

        assertThrows(EOFException.class,
                () -> server.readExactWithDeadline(new ByteArrayInputStream(new byte[]{1, 2}), 4, 8, 1000));

        IOException capExceeded = assertThrows(IOException.class,
                () -> server.readExactWithDeadline(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}), 9, 8, 1000));
        assertTrue(capExceeded.getMessage().contains("exceeds cap"));
    }

    private static VotifierServer server() {
        FeatureLogger logger = mock(FeatureLogger.class);
        PrivateKey key = mock(PrivateKey.class);
        IpAccessList access = IpAccessList.fromCsv("", "", logger);
        return new VotifierServer("127.0.0.1", 0, 5000, 50, 8192, key, access, vote -> {
        }, logger);
    }
}
