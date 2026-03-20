package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.PersistentIpCache.CacheHit;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.PersistentIpCache.Source;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.provider.ProviderChain;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AntiVPNServiceTest {

    @Test
    void gettersExposeCacheAndWhitelistAndRefreshRebuildsWhitelist() {
        Map<String, Object> cfg = baseConfig();
        cfg.put("whitelist", Map.of(
                "allow_private_ranges", false,
                "entries", List.of("5.5.5.5")
        ));

        PersistentIpCache cache = mock(PersistentIpCache.class);
        ProviderChain providers = mock(ProviderChain.class);
        NotificationService notifications = mock(NotificationService.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        AntiVPNService service = new AntiVPNService(featureWithConfig(cfg), cache, providers, notifications, metrics);
        assertSame(cache, service.getCache());
        assertTrue(service.getWhitelist().isWhitelisted("5.5.5.5"));

        service.refreshWhitelistFromConfig();
        assertTrue(service.getWhitelist().isWhitelisted("5.5.5.5"));
    }

    @Test
    void evaluateBypassesWhitelistedIps() {
        Map<String, Object> cfg = baseConfig();
        cfg.put("whitelist", Map.of("allow_private_ranges", false, "entries", List.of("1.2.3.4")));

        PersistentIpCache cache = mock(PersistentIpCache.class);
        ProviderChain providers = mock(ProviderChain.class);
        NotificationService notifications = mock(NotificationService.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        AntiVPNService service = new AntiVPNService(featureWithConfig(cfg), cache, providers, notifications, metrics);
        AntiVPNService.Evaluation evaluation = service.evaluate("1.2.3.4", "Remy").join();

        assertTrue(evaluation.allowed());
        assertEquals("whitelist", evaluation.providerId());
        assertEquals("WHITELIST", evaluation.cacheSource());
        verify(metrics).incChecks();
        verify(metrics).incAllowed();
        verifyNoInteractions(cache, providers, notifications);
    }

    @Test
    void evaluateDeniesWhenCountryIsNotAllowed() {
        Map<String, Object> cfg = baseConfig();
        cfg.put("use_vpn_check", false);
        cfg.put("allowed_countries", List.of("NL"));

        PersistentIpCache cache = mock(PersistentIpCache.class);
        ProviderChain providers = mock(ProviderChain.class);
        NotificationService notifications = mock(NotificationService.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        IPCheckResult lookup = IPCheckResult.of("US", null, "proxycheck");
        when(cache.getOrCompute(eq("8.8.8.8"), any())).thenReturn(CompletableFuture.completedFuture(lookup));
        when(cache.getIfPresent("8.8.8.8")).thenReturn(Optional.of(new CacheHit(lookup, Source.MEM)));
        when(providers.maxTimeoutMillis()).thenReturn(1500L);

        AntiVPNService service = new AntiVPNService(featureWithConfig(cfg), cache, providers, notifications, metrics);
        AntiVPNService.Evaluation evaluation = service.evaluate("8.8.8.8", "Remy").join();

        assertFalse(evaluation.allowed());
        assertEquals("antivpn.blocked_region", evaluation.denyMessageKey());
        assertEquals("US", evaluation.countryUpper());
        assertEquals("MEM", evaluation.cacheSource());
        verify(notifications).notifyRegionBlocked("Remy", "US");
        verify(metrics).incDeniedRegion();
    }

    @Test
    void evaluateDeniesWhenVpnStateIsUnknownAndPolicyRequiresDeny() {
        Map<String, Object> cfg = baseConfig();
        cfg.put("use_region_check", false);
        cfg.put("use_vpn_check", true);
        cfg.put("policy", Map.of(
                "on_api_error", "ALLOW",
                "region_unknown", "ALLOW",
                "vpn_unknown", "DENY"
        ));

        PersistentIpCache cache = mock(PersistentIpCache.class);
        ProviderChain providers = mock(ProviderChain.class);
        NotificationService notifications = mock(NotificationService.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        IPCheckResult lookup = IPCheckResult.of("NL", null, "proxycheck");
        when(cache.getOrCompute(eq("9.9.9.9"), any())).thenReturn(CompletableFuture.completedFuture(lookup));
        when(cache.getIfPresent("9.9.9.9")).thenReturn(Optional.of(new CacheHit(lookup, Source.DISK)));
        when(providers.maxTimeoutMillis()).thenReturn(1500L);

        AntiVPNService service = new AntiVPNService(featureWithConfig(cfg), cache, providers, notifications, metrics);
        AntiVPNService.Evaluation evaluation = service.evaluate("9.9.9.9", "Remy").join();

        assertFalse(evaluation.allowed());
        assertEquals("antivpn.blocked_vpn", evaluation.denyMessageKey());
        assertNull(evaluation.vpn());
        assertEquals("DISK", evaluation.cacheSource());
        verify(notifications).notifyVpnBlocked("Remy");
        verify(metrics).incDeniedVpn();
    }

    @Test
    void evaluateAllowsWhenRegionAndVpnChecksAreDisabled() {
        Map<String, Object> cfg = baseConfig();
        cfg.put("use_region_check", false);
        cfg.put("use_vpn_check", false);

        PersistentIpCache cache = mock(PersistentIpCache.class);
        ProviderChain providers = mock(ProviderChain.class);
        NotificationService notifications = mock(NotificationService.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        AntiVPNService service = new AntiVPNService(featureWithConfig(cfg), cache, providers, notifications, metrics);
        AntiVPNService.Evaluation evaluation = service.evaluate("8.8.4.4", "Remy").join();

        assertTrue(evaluation.allowed());
        assertEquals("disabled", evaluation.providerId());
        assertEquals("NONE", evaluation.cacheSource());
        verify(metrics).incAllowed();
        verifyNoInteractions(cache, providers, notifications);
    }

    @Test
    void evaluateDeniesWhenRegionIsUnknownAndPolicyIsDeny() {
        Map<String, Object> cfg = baseConfig();
        cfg.put("use_region_check", true);
        cfg.put("use_vpn_check", false);
        cfg.put("policy", Map.of(
                "on_api_error", "ALLOW",
                "region_unknown", "DENY",
                "vpn_unknown", "ALLOW"
        ));

        PersistentIpCache cache = mock(PersistentIpCache.class);
        ProviderChain providers = mock(ProviderChain.class);
        NotificationService notifications = mock(NotificationService.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        IPCheckResult lookup = IPCheckResult.of("", false, "provider");
        when(cache.getOrCompute(eq("10.10.10.10"), any())).thenReturn(CompletableFuture.completedFuture(lookup));
        when(cache.getIfPresent("10.10.10.10")).thenReturn(Optional.empty());
        when(providers.maxTimeoutMillis()).thenReturn(1000L);

        AntiVPNService service = new AntiVPNService(featureWithConfig(cfg), cache, providers, notifications, metrics);
        AntiVPNService.Evaluation evaluation = service.evaluate("10.10.10.10", "Remy").join();

        assertFalse(evaluation.allowed());
        assertEquals("antivpn.blocked_region_unknown", evaluation.denyMessageKey());
        verify(metrics).incDeniedRegion();
        verify(notifications).notifyRegionUnknownBlocked("Remy");
    }

    @Test
    void evaluateDeniesWhenVpnFlagIsTrue() {
        Map<String, Object> cfg = baseConfig();
        cfg.put("use_region_check", false);
        cfg.put("use_vpn_check", true);

        PersistentIpCache cache = mock(PersistentIpCache.class);
        ProviderChain providers = mock(ProviderChain.class);
        NotificationService notifications = mock(NotificationService.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        IPCheckResult lookup = IPCheckResult.of("NL", true, "provider");
        when(cache.getOrCompute(eq("11.11.11.11"), any())).thenReturn(CompletableFuture.completedFuture(lookup));
        when(cache.getIfPresent("11.11.11.11")).thenReturn(Optional.empty());
        when(providers.maxTimeoutMillis()).thenReturn(1000L);

        AntiVPNService service = new AntiVPNService(featureWithConfig(cfg), cache, providers, notifications, metrics);
        AntiVPNService.Evaluation evaluation = service.evaluate("11.11.11.11", "Remy").join();

        assertFalse(evaluation.allowed());
        assertEquals("antivpn.blocked_vpn", evaluation.denyMessageKey());
        assertEquals(Boolean.TRUE, evaluation.vpn());
        verify(metrics).incDeniedVpn();
        verify(notifications).notifyVpnBlocked("Remy");
    }

    @Test
    void evaluateAllowsWhenLookupPassesAndSupplierCallsProvider() {
        Map<String, Object> cfg = baseConfig();
        cfg.put("use_region_check", true);
        cfg.put("use_vpn_check", true);
        cfg.put("allowed_countries", List.of("NL"));

        PersistentIpCache cache = mock(PersistentIpCache.class);
        ProviderChain providers = mock(ProviderChain.class);
        NotificationService notifications = mock(NotificationService.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        when(cache.getIfPresent("12.12.12.12")).thenReturn(Optional.empty());
        when(cache.getOrCompute(eq("12.12.12.12"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<CompletableFuture<IPCheckResult>> supplier =
                    invocation.getArgument(1, Supplier.class);
            return supplier.get();
        });
        when(providers.lookup("12.12.12.12", true, true))
                .thenReturn(CompletableFuture.completedFuture(IPCheckResult.of("NL", false, "provider")));
        when(providers.maxTimeoutMillis()).thenReturn(1000L);

        AntiVPNService service = new AntiVPNService(featureWithConfig(cfg), cache, providers, notifications, metrics);
        AntiVPNService.Evaluation evaluation = service.evaluate("12.12.12.12", "Remy").join();

        assertTrue(evaluation.allowed());
        assertEquals("NL", evaluation.countryUpper());
        assertEquals(Boolean.FALSE, evaluation.vpn());
        verify(providers).lookup("12.12.12.12", true, true);
        verify(metrics).incAllowed();
    }

    @Test
    void evaluateApiErrorRespectsOnApiErrorPolicy() {
        PersistentIpCache cache = mock(PersistentIpCache.class);
        ProviderChain providers = mock(ProviderChain.class);
        NotificationService notifications = mock(NotificationService.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        when(cache.getOrCompute(eq("4.4.4.4"), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("timeout")));
        when(providers.maxTimeoutMillis()).thenReturn(1200L);

        AntiVPNService allowService = new AntiVPNService(featureWithConfig(baseConfig()),
                cache, providers, notifications, metrics);
        AntiVPNService.Evaluation allow = allowService.evaluate("4.4.4.4", "Remy").join();
        assertTrue(allow.allowed());
        assertTrue(allow.error().contains("timeout"));

        Map<String, Object> denyCfg = baseConfig();
        denyCfg.put("policy", Map.of(
                "on_api_error", "DENY",
                "region_unknown", "DENY",
                "vpn_unknown", "ALLOW"
        ));
        AntiVPNService denyService = new AntiVPNService(featureWithConfig(denyCfg),
                cache, providers, notifications, metrics);
        AntiVPNService.Evaluation deny = denyService.evaluate("4.4.4.4", "Remy").join();
        assertFalse(deny.allowed());
        assertEquals("antivpn.error", deny.denyMessageKey());
        assertTrue(deny.error().contains("timeout"));

        verify(metrics, times(2)).incErrors();
    }

    @Test
    void debugLookupUsesCacheHitSourceOrMiss() {
        PersistentIpCache cache = mock(PersistentIpCache.class);
        ProviderChain providers = mock(ProviderChain.class);
        NotificationService notifications = mock(NotificationService.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        IPCheckResult memResult = IPCheckResult.of("NL", true, "p1");
        IPCheckResult missResult = IPCheckResult.of("DE", false, "p2");

        when(cache.getIfPresent("1.1.1.1")).thenReturn(Optional.of(new CacheHit(memResult, Source.MEM)));
        when(cache.getIfPresent("2.2.2.2")).thenReturn(Optional.empty());
        when(cache.getOrCompute(eq("2.2.2.2"), any())).thenReturn(CompletableFuture.completedFuture(missResult));

        AntiVPNService service = new AntiVPNService(featureWithConfig(baseConfig()),
                cache, providers, notifications, metrics);

        AntiVPNService.LookupDebug fromCache = service.debugLookup("1.1.1.1", true, true).join();
        assertEquals("MEM", fromCache.cacheSource());
        assertEquals("NL", fromCache.result().countryCode());

        AntiVPNService.LookupDebug fromMiss = service.debugLookup("2.2.2.2", true, true).join();
        assertEquals("MISS", fromMiss.cacheSource());
        assertEquals("DE", fromMiss.result().countryCode());
    }

    @Test
    void debugLookupMissCanUseSupplierToInvokeProviderChain() {
        PersistentIpCache cache = mock(PersistentIpCache.class);
        ProviderChain providers = mock(ProviderChain.class);
        NotificationService notifications = mock(NotificationService.class);
        MetricsCollector metrics = mock(MetricsCollector.class);

        when(cache.getIfPresent("3.3.3.3")).thenReturn(Optional.empty());
        when(cache.getOrCompute(eq("3.3.3.3"), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<CompletableFuture<IPCheckResult>> supplier =
                    invocation.getArgument(1, Supplier.class);
            return supplier.get();
        });
        when(providers.lookup("3.3.3.3", true, false))
                .thenReturn(CompletableFuture.completedFuture(IPCheckResult.of("FR", null, "provider")));

        AntiVPNService service = new AntiVPNService(featureWithConfig(baseConfig()),
                cache, providers, notifications, metrics);

        AntiVPNService.LookupDebug debug = service.debugLookup("3.3.3.3", true, false).join();
        assertEquals("MISS", debug.cacheSource());
        assertEquals("FR", debug.result().countryCode());
        verify(providers).lookup("3.3.3.3", true, false);
    }

    private static AntiVPN featureWithConfig(Map<String, Object> rootData) {
        AntiVPN feature = mock(AntiVPN.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        ConfigNode root = ConfigNode.ofRaw(rootData, "<root>");

        when(feature.getConfigHandler()).thenReturn(cfg);
        when(feature.getLogger()).thenReturn(logger);

        when(cfg.node(anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0, String.class);
            if (path == null || path.isBlank()) return root;
            return root.getAt(path);
        });

        when(cfg.get(anyString(), eq(Boolean.class), any())).thenAnswer(invocation -> {
            String path = invocation.getArgument(0, String.class);
            Boolean def = invocation.getArgument(2, Boolean.class);
            return root.getAt(path).as(Boolean.class, def);
        });

        return feature;
    }

    private static Map<String, Object> baseConfig() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("whitelist", Map.of(
                "allow_private_ranges", false,
                "entries", List.of()
        ));
        cfg.put("use_region_check", true);
        cfg.put("use_vpn_check", true);
        cfg.put("allowed_countries", List.of("NL"));
        cfg.put("policy", Map.of(
                "on_api_error", "ALLOW",
                "region_unknown", "DENY",
                "vpn_unknown", "ALLOW"
        ));
        return cfg;
    }
}
