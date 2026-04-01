package nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider.proxycheck;

import com.google.gson.JsonObject;
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

    @Test
    void buildUrlEncodesIpAndOptionalParams() throws Exception {
        AntiVPN feature = featureWithProviders(Map.of(
                "proxycheck", Map.of(
                        "enabled", true,
                        "api_key", "secret key",
                        "base_url", "https://proxycheck.io/v3",
                        "api_version", "20-November-2025",
                        "days", 7
                )));

        ProxyCheckProvider provider = ProxyCheckProvider.fromConfig(feature);
        assertNotNull(provider);
        String url = provider.buildUrl("2001:db8::1");

        assertTrue(url.startsWith("https://proxycheck.io/v3/2001%3Adb8%3A%3A1?key=secret+key"));
        assertTrue(url.contains("&ver=20-November-2025"));
        assertTrue(url.contains("&days=7"));
    }

    @Test
    void computeVpnAppliesConfidenceStrictAndRiskRules() throws Exception {
        ProxyCheckProvider strictProvider = ProxyCheckProvider.fromConfig(featureWithProviders(Map.of(
                "proxycheck", Map.of(
                        "enabled", true,
                        "api_key", "secret",
                        "strict", true,
                        "risk_threshold", 1,
                        "min_confidence", 90
                ))));
        assertNotNull(strictProvider);

        JsonObject lowConfidence = json(
                "confidence", 40,
                "proxy", true
        );
        assertNull(strictProvider.computeVpn(lowConfidence));

        JsonObject strictHosting = json(
                "confidence", 95,
                "hosting", true
        );
        assertEquals(Boolean.TRUE, strictProvider.computeVpn(strictHosting));

        JsonObject riskTriggered = json(
                "confidence", 95,
                "risk", 1
        );
        assertEquals(Boolean.TRUE, strictProvider.computeVpn(riskTriggered));

        ProxyCheckProvider nonStrictProvider = ProxyCheckProvider.fromConfig(featureWithProviders(Map.of(
                "proxycheck", Map.of(
                        "enabled", true,
                        "api_key", "secret",
                        "strict", false,
                        "risk_threshold", 0,
                        "min_confidence", 0
                ))));
        assertNotNull(nonStrictProvider);
        assertEquals(Boolean.FALSE, nonStrictProvider.computeVpn(strictHosting));
    }

    @Test
    void pickIpObjectFallsBackToFirstIpLikeKey() {
        JsonObject root = new JsonObject();
        root.addProperty("status", "ok");
        JsonObject ipObj = json("proxy", true);
        root.add("198.51.100.9", ipObj);

        JsonObject picked = ProxyCheckProvider.pickIpObject(root, "203.0.113.1");
        assertNotNull(picked);
        assertEquals(ipObj, picked);
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

    private static JsonObject json(Object... kv) {
        JsonObject out = new JsonObject();
        for (int i = 0; i < kv.length; i += 2) {
            String key = String.valueOf(kv[i]);
            Object val = kv[i + 1];
            if (val instanceof Boolean b) out.addProperty(key, b);
            else if (val instanceof Number n) out.addProperty(key, n);
            else out.addProperty(key, String.valueOf(val));
        }
        return out;
    }
}
