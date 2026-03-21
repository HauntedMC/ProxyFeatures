package nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider.proxycheck;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProxyCheckProviderTest {

    @Test
    void fromConfigReturnsNullWhenProviderDisabled() {
        AntiVPN feature = featureWithProviders(Map.of(
                "proxycheck", Map.of("enabled", false)
        ));
        assertNull(ProxyCheckProvider.fromConfig(feature));
    }

    @Test
    void fromConfigReturnsNullAndWarnsWhenApiKeyMissing() {
        AntiVPN feature = featureWithProviders(Map.of(
                "proxycheck", Map.of(
                        "enabled", true,
                        "api_key", " "
                )));

        assertNull(ProxyCheckProvider.fromConfig(feature));
        verify(feature.getLogger()).warn(contains("api_key is empty"));
    }

    @Test
    void fromConfigReturnsNullAndWarnsWhenBaseUrlInvalid() {
        AntiVPN feature = featureWithProviders(Map.of(
                "proxycheck", Map.of(
                        "enabled", true,
                        "api_key", "secret",
                        "base_url", "ftp://proxycheck.io/v3/"
                )));

        assertNull(ProxyCheckProvider.fromConfig(feature));
        verify(feature.getLogger()).warn(contains("base_url is invalid"));
    }

    @Test
    void fromConfigBuildsProviderAndClampsTimeoutMinimum() {
        AntiVPN feature = featureWithProviders(Map.of(
                "proxycheck", Map.of(
                        "enabled", true,
                        "api_key", "secret",
                        "base_url", "https://proxycheck.io/v3/",
                        "timeout_millis", 100L,
                        "strict", true,
                        "risk_threshold", 1,
                        "min_confidence", 0
                )));

        ProxyCheckProvider provider = ProxyCheckProvider.fromConfig(feature);
        assertNotNull(provider);
        assertEquals("proxycheck", provider.id());
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
