package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IPCheckResultTest {

    @Test
    void countryUpperNormalizesWhitespaceAndCase() {
        IPCheckResult result = new IPCheckResult(" nl ", true, "provider", 1L);
        assertEquals("NL", result.countryUpper());

        assertEquals("", new IPCheckResult(null, null, null, 1L).countryUpper());
    }

    @Test
    void ofFactorySetsCurrentTimestamp() {
        long before = System.currentTimeMillis();
        IPCheckResult result = IPCheckResult.of("NL", false, "provider");
        long after = System.currentTimeMillis();

        assertEquals("NL", result.countryCode());
        assertEquals(false, result.vpn());
        assertEquals("provider", result.providerId());
        assertTrue(result.timestamp() >= before);
        assertTrue(result.timestamp() <= after);
    }
}
