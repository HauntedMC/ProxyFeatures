package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IpWhitelistTest {

    @Test
    void normalizeEntrySupportsPortsAndCidrAndRejectsInvalidValues() {
        assertEquals("1.2.3.4", IpWhitelist.normalizeEntry("1.2.3.4:25565").orElseThrow());
        assertEquals("10.0.0.0/8", IpWhitelist.normalizeEntry("10.0.0.0/8").orElseThrow());
        assertTrue(IpWhitelist.normalizeEntry("10.0.0.0/not-a-number").isEmpty());
        assertTrue(IpWhitelist.normalizeEntry("  ").isEmpty());
        assertTrue(IpWhitelist.normalizeEntry("not-an-ip").isEmpty());
        assertTrue(IpWhitelist.normalizeEntry("10.0.0.0/99").isEmpty());
        assertTrue(IpWhitelist.normalizeEntry("999.999.999.999").isEmpty());
    }

    @Test
    void fromConfigBuildsCanonicalListAndMatchesExactAndCidrEntries() {
        IpWhitelist whitelist = IpWhitelist.fromConfig(featureWithEntries(false,
                List.of("1.2.3.4:25565", "1.2.3.4", "192.168.0.0/16", "10.0.128.0/17", "bad")));

        assertEquals(List.of("1.2.3.4", "192.168.0.0/16", "10.0.128.0/17"), whitelist.entries());
        assertTrue(whitelist.isWhitelisted("1.2.3.4"));
        assertTrue(whitelist.isWhitelisted("192.168.1.50"));
        assertTrue(whitelist.isWhitelisted("10.0.200.1"));
        assertFalse(whitelist.isWhitelisted("10.0.10.1"));
        assertFalse(whitelist.isWhitelisted("8.8.8.8"));
    }

    @Test
    void allowPrivateRangeBypassWorksWhenEnabled() {
        IpWhitelist whitelist = IpWhitelist.fromConfig(featureWithEntries(true, List.of()));
        assertTrue(whitelist.isWhitelisted("127.0.0.1"));
        assertTrue(whitelist.isWhitelisted("169.254.10.1"));
        assertTrue(whitelist.isWhitelisted("0.0.0.0"));
        assertFalse(whitelist.isWhitelisted("not-ip"));
    }

    @Test
    void allowPrivateDisabledDoesNotBypassAndMissingEntriesDefaultsToEmpty() {
        AntiVPN feature = featureWithWhitelistData(Map.of("allow_private_ranges", false));
        IpWhitelist whitelist = IpWhitelist.fromConfig(feature);
        assertTrue(whitelist.entries().isEmpty());
        assertFalse(whitelist.isWhitelisted("127.0.0.1"));
    }

    private static AntiVPN featureWithEntries(boolean allowPrivate, List<String> entries) {
        return featureWithWhitelistData(Map.of(
                "allow_private_ranges", allowPrivate,
                "entries", entries
        ));
    }

    private static AntiVPN featureWithWhitelistData(Map<String, Object> whitelistData) {
        AntiVPN feature = mock(AntiVPN.class);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        ConfigNode whitelistNode = ConfigNode.ofRaw(new java.util.HashMap<>(whitelistData), "whitelist");
        when(feature.getConfigHandler()).thenReturn(config);
        when(config.node("whitelist")).thenReturn(whitelistNode);
        return feature;
    }
}
