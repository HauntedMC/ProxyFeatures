package nl.hauntedmc.proxyfeatures.features.antivpn.listener;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiVPNPreLoginPolicyTest {

    @Test
    void resolveIpReturnsNullForNullAddress() {
        assertNull(AntiVPNPreLoginPolicy.resolveIp(null));
    }

    @Test
    void resolveIpUsesHostAddressForResolvedSockets() {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 25565);
        assertEquals("127.0.0.1", AntiVPNPreLoginPolicy.resolveIp(address));
    }

    @Test
    void resolveIpUsesHostStringForUnresolvedSockets() {
        InetSocketAddress unresolved = InetSocketAddress.createUnresolved("example.org", 25565);
        assertEquals("example.org", AntiVPNPreLoginPolicy.resolveIp(unresolved));
    }

    @Test
    void shouldStageCountryRequiresNonBlankCountryAndName() {
        assertTrue(AntiVPNPreLoginPolicy.shouldStageCountry("nl", "Remy"));
        assertFalse(AntiVPNPreLoginPolicy.shouldStageCountry("", "Remy"));
        assertFalse(AntiVPNPreLoginPolicy.shouldStageCountry("NL", " "));
        assertFalse(AntiVPNPreLoginPolicy.shouldStageCountry(null, "Remy"));
        assertFalse(AntiVPNPreLoginPolicy.shouldStageCountry("NL", null));
    }

    @Test
    void normalizeCountryUppercasesUsingRootLocale() {
        assertEquals("NL", AntiVPNPreLoginPolicy.normalizeCountry("nl"));
    }
}
