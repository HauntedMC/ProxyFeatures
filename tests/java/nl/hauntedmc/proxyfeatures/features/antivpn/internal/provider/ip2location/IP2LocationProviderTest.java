package nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider.ip2location;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IP2LocationProviderTest {

    @Test
    void fromConfigReturnsNullAndWarnsWhenApiKeyMissing() {
        AntiVPN feature = featureWithProviders(Map.of(
                "ip2location", Map.of(
                        "api_key", " ",
                        "timeout_millis", 2500L
                )));

        IP2LocationProvider provider = IP2LocationProvider.fromConfig(feature);

        assertNull(provider);
        verify(feature.getLogger()).warn(contains("api_key is empty"));
    }

    @Test
    void fromConfigBuildsProviderAndClampsTimeoutMinimum() {
        AntiVPN feature = featureWithProviders(Map.of(
                "ip2location", Map.of(
                        "api_key", "secret",
                        "timeout_millis", 1L
                )));

        IP2LocationProvider provider = IP2LocationProvider.fromConfig(feature);

        assertNotNull(provider);
        assertEquals("ip2location", provider.id());
        assertEquals(500L, provider.timeoutMillis());
    }

    private static AntiVPN featureWithProviders(Map<String, Object> providersData) {
        AntiVPN feature = mock(AntiVPN.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        when(feature.getConfigHandler()).thenReturn(cfg);
        when(feature.getLogger()).thenReturn(logger);
        when(cfg.node("providers")).thenReturn(ConfigNode.ofRaw(providersData, "providers"));
        return feature;
    }
}
