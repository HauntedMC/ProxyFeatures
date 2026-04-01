package nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.IPCheckResult;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider.ip2location.IP2LocationProvider;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider.proxycheck.ProxyCheckProvider;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

class ProviderRegistryTest {

    @Test
    void buildChainUsesConfiguredProvidersAndWarnsOnUnknownIds() {
        AntiVPN feature = featureWithProviders(Map.of(
                "order", List.of("proxycheck", "unknown", "ip2location", "disabled"),
                "proxycheck", Map.of("enabled", true),
                "ip2location", Map.of("enabled", true),
                "disabled", Map.of("enabled", false)
        ));
        FeatureLogger logger = feature.getLogger();

        ProxyCheckProvider proxycheck = mock(ProxyCheckProvider.class);
        IP2LocationProvider ip2 = mock(IP2LocationProvider.class);
        when(proxycheck.timeoutMillis()).thenReturn(3000L);
        when(proxycheck.id()).thenReturn("proxycheck");
        when(proxycheck.lookup(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(IPCheckResult.of("NL", true, "proxycheck")));

        when(ip2.timeoutMillis()).thenReturn(1000L);
        when(ip2.id()).thenReturn("ip2location");
        when(ip2.lookup(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(IPCheckResult.of("US", false, "ip2location")));

        try (MockedStatic<ProxyCheckProvider> proxyMock = mockStatic(ProxyCheckProvider.class);
             MockedStatic<IP2LocationProvider> ip2Mock = mockStatic(IP2LocationProvider.class)) {
            proxyMock.when(() -> ProxyCheckProvider.fromConfig(feature)).thenReturn(proxycheck);
            ip2Mock.when(() -> IP2LocationProvider.fromConfig(feature)).thenReturn(ip2);

            ProviderChain chain = ProviderRegistry.buildChain(feature);
            assertEquals(3000L, chain.maxTimeoutMillis());
            IPCheckResult result = chain.lookup("1.2.3.4", true, true).join();
            assertEquals("NL", result.countryCode());
            assertEquals(Boolean.TRUE, result.vpn());

            verify(logger).warn(contains("Unknown provider id"));
        }
    }

    @Test
    void buildChainWarnsWhenNoProviderCouldBeConstructed() {
        AntiVPN feature = featureWithProviders(Map.of(
                "order", List.of("proxycheck"),
                "proxycheck", Map.of("enabled", true)
        ));
        FeatureLogger logger = feature.getLogger();

        try (MockedStatic<ProxyCheckProvider> proxyMock = mockStatic(ProxyCheckProvider.class)) {
            proxyMock.when(() -> ProxyCheckProvider.fromConfig(feature)).thenReturn(null);

            ProviderChain chain = ProviderRegistry.buildChain(feature);
            verify(logger).warn(contains("No providers enabled"));
            assertThrows(CompletionException.class, () -> chain.lookup("1.1.1.1", true, true).join());
        }
    }

    @Test
    void buildChainFallsBackToDefaultOrderWhenMissing() {
        AntiVPN feature = featureWithProviders(Map.of(
                "proxycheck", Map.of("enabled", true),
                "ip2location", Map.of("enabled", true)
        ));

        ProxyCheckProvider proxycheck = mock(ProxyCheckProvider.class);
        IP2LocationProvider ip2 = mock(IP2LocationProvider.class);
        when(proxycheck.timeoutMillis()).thenReturn(2000L);
        when(ip2.timeoutMillis()).thenReturn(2500L);
        when(proxycheck.id()).thenReturn("proxycheck");
        when(ip2.id()).thenReturn("ip2location");
        when(proxycheck.lookup(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(IPCheckResult.of("", null, "proxycheck")));
        when(ip2.lookup(anyString(), anyBoolean(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(IPCheckResult.of("DE", false, "ip2location")));

        try (MockedStatic<ProxyCheckProvider> proxyMock = mockStatic(ProxyCheckProvider.class);
             MockedStatic<IP2LocationProvider> ip2Mock = mockStatic(IP2LocationProvider.class)) {
            proxyMock.when(() -> ProxyCheckProvider.fromConfig(feature)).thenReturn(proxycheck);
            ip2Mock.when(() -> IP2LocationProvider.fromConfig(feature)).thenReturn(ip2);

            ProviderChain chain = ProviderRegistry.buildChain(feature);
            IPCheckResult result = chain.lookup("2.2.2.2", true, true).join();
            assertEquals("DE", result.countryCode());
            assertEquals(Boolean.FALSE, result.vpn());
            assertEquals(2500L, chain.maxTimeoutMillis());
        }
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
