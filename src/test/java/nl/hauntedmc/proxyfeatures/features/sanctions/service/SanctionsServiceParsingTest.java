package nl.hauntedmc.proxyfeatures.features.sanctions.service;

import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SanctionsServiceParsingTest {

    @Test
    void parseLengthSupportsPermanentAndCompoundDurations() {
        SanctionsService service = new SanctionsService(mock(Sanctions.class));

        assertNull(service.parseLengthToExpiry("p"));
        assertNull(service.parseLengthToExpiry("perm"));
        assertNull(service.parseLengthToExpiry("permanent"));

        Instant before = Instant.now();
        Instant expiry = service.parseLengthToExpiry("1d2h30m10s");
        long delta = expiry.getEpochSecond() - before.getEpochSecond();

        long expected = 86_400 + 7_200 + 1_800 + 10;
        assertTrue(delta >= expected - 1 && delta <= expected + 1);
    }

    @Test
    void parseLengthRejectsMalformedTokensAndJunkInput() {
        SanctionsService service = new SanctionsService(mock(Sanctions.class));

        assertThrows(IllegalArgumentException.class, () -> service.parseLengthToExpiry(null));
        assertThrows(IllegalArgumentException.class, () -> service.parseLengthToExpiry(""));
        assertThrows(IllegalArgumentException.class, () -> service.parseLengthToExpiry("abc"));
        assertThrows(IllegalArgumentException.class, () -> service.parseLengthToExpiry("1x"));
        assertThrows(IllegalArgumentException.class, () -> service.parseLengthToExpiry("1d garbage"));
        assertThrows(IllegalArgumentException.class, () -> service.parseLengthToExpiry("garbage 1d"));
        assertThrows(IllegalArgumentException.class, () -> service.parseLengthToExpiry("1dfoo"));
    }

    @Test
    void sanitizeReasonAndHumanDurationHandleBoundaries() {
        SanctionsService service = new SanctionsService(mock(Sanctions.class));

        assertEquals("-", service.sanitizeReason(null));
        assertEquals("-", service.sanitizeReason("   "));
        assertEquals("reason", service.sanitizeReason("  reason  "));

        String longReason = "x".repeat(1024);
        assertEquals(512, service.sanitizeReason(longReason).length());

        Instant from = Instant.ofEpochSecond(0);
        assertEquals("permanent", service.humanDuration(from, null));
        assertEquals("0s", service.humanDuration(from, from));
        assertEquals("45s", service.humanDuration(from, from.plusSeconds(45)));
        assertEquals("1d 1h 1m", service.humanDuration(from, from.plusSeconds(90_061)));
    }
}
