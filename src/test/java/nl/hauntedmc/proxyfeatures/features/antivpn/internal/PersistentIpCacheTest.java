package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import nl.hauntedmc.proxyfeatures.api.io.cache.CacheValue;
import nl.hauntedmc.proxyfeatures.api.io.cache.FileCacheStore;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersistentIpCacheTest {

    @Test
    void getIfPresentSupportsMemoryAndDiskSourcesAndTypeCoercions() {
        TestContext ctx = createContext(true, false);

        IPCheckResult computed = ctx.cache.getOrCompute("1.1.1.1",
                () -> CompletableFuture.completedFuture(IPCheckResult.of("NL", false, "provider"))).join();
        assertEquals("NL", computed.countryCode());

        PersistentIpCache.CacheHit memHit = ctx.cache.getIfPresent("1.1.1.1").orElseThrow();
        assertEquals(PersistentIpCache.Source.MEM, memHit.source());

        when(ctx.store.get("2.2.2.2")).thenReturn(CacheValue.of(
                Map.of("country", "US", "vpn", 1, "provider", "disk", "ts", 123L),
                System.currentTimeMillis() + 60_000
        ));
        PersistentIpCache.CacheHit diskNumBool = ctx.cache.getIfPresent("2.2.2.2").orElseThrow();
        assertEquals(PersistentIpCache.Source.DISK, diskNumBool.source());
        assertEquals("US", diskNumBool.result().countryCode());
        assertEquals(Boolean.TRUE, diskNumBool.result().vpn());

        when(ctx.store.get("3.3.3.3")).thenReturn(CacheValue.of(
                Map.of("vpn", false),
                System.currentTimeMillis() + 60_000
        ));
        IPCheckResult boolValue = ctx.cache.getIfPresent("3.3.3.3").orElseThrow().result();
        assertEquals("", boolValue.countryCode());
        assertEquals("", boolValue.providerId());
        assertEquals(Boolean.FALSE, boolValue.vpn());

        when(ctx.store.get("4.4.4.4")).thenReturn(CacheValue.of(
                Map.of("vpn", "yes", "ts", 0L),
                System.currentTimeMillis() + 60_000
        ));
        IPCheckResult strTrue = ctx.cache.getIfPresent("4.4.4.4").orElseThrow().result();
        assertEquals(Boolean.TRUE, strTrue.vpn());
        assertTrue(strTrue.timestamp() > 0);

        when(ctx.store.get("5.5.5.5")).thenReturn(CacheValue.of(
                Map.of("vpn", "no"),
                System.currentTimeMillis() + 60_000
        ));
        assertEquals(Boolean.FALSE, ctx.cache.getIfPresent("5.5.5.5").orElseThrow().result().vpn());

        when(ctx.store.get("6.6.6.6")).thenReturn(CacheValue.of(
                Map.of("country", "NO"),
                System.currentTimeMillis() + 60_000
        ));
        assertNull(ctx.cache.getIfPresent("6.6.6.6").orElseThrow().result().vpn());

        when(ctx.store.get("7.7.7.7")).thenReturn(CacheValue.of(
                Map.of("vpn", "unknown"),
                System.currentTimeMillis() + 60_000
        ));
        assertNull(ctx.cache.getIfPresent("7.7.7.7").orElseThrow().result().vpn());
    }

    @Test
    void getIfPresentHandlesStoreFailuresAndExpiredEntries() {
        TestContext ctx = createContext(true, false);

        when(ctx.store.get("fail")).thenThrow(new RuntimeException("disk down"));
        assertTrue(ctx.cache.getIfPresent("fail").isEmpty());
        verify(ctx.logger).warn(contains("disk get failed"));

        when(ctx.store.get("expired")).thenReturn(CacheValue.of(
                Map.of("country", "NL"),
                System.currentTimeMillis() - 1
        ));
        assertTrue(ctx.cache.getIfPresent("expired").isEmpty());
    }

    @Test
    void getOrComputeInflightDedupeFastPathAndFailureRecovery() {
        TestContext ctx = createContext(true, false);
        AtomicInteger supplierCalls = new AtomicInteger();
        CompletableFuture<IPCheckResult> pending = new CompletableFuture<>();

        CompletableFuture<IPCheckResult> first = ctx.cache.getOrCompute("8.8.8.8", () -> {
            supplierCalls.incrementAndGet();
            return pending;
        });
        CompletableFuture<IPCheckResult> second = ctx.cache.getOrCompute("8.8.8.8", () -> {
            supplierCalls.incrementAndGet();
            return CompletableFuture.completedFuture(IPCheckResult.of("DE", null, "other"));
        });

        assertSame(first, second);
        assertEquals(1, supplierCalls.get());
        pending.complete(IPCheckResult.of("DE", null, "provider"));
        assertEquals("DE", first.join().countryCode());
        assertEquals(0, ctx.cache.inflightCount());

        AtomicInteger fastPathCalls = new AtomicInteger();
        IPCheckResult cached = ctx.cache.getOrCompute("8.8.8.8", () -> {
            fastPathCalls.incrementAndGet();
            return CompletableFuture.completedFuture(IPCheckResult.of("FR", false, "should-not-run"));
        }).join();
        assertEquals("DE", cached.countryCode());
        assertEquals(0, fastPathCalls.get());

        CompletionException supplierThrow = assertThrows(CompletionException.class, () ->
                ctx.cache.getOrCompute("9.9.9.9", () -> {
                    throw new IllegalStateException("boom");
                }).join());
        assertEquals("boom", supplierThrow.getCause().getMessage());

        CompletionException nullFuture = assertThrows(CompletionException.class, () ->
                ctx.cache.getOrCompute("9.9.9.9", () -> null).join());
        assertTrue(nullFuture.getCause() instanceof NullPointerException);

        CompletableFuture<IPCheckResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalArgumentException("provider failed"));
        CompletionException failedFuture = assertThrows(CompletionException.class, () ->
                ctx.cache.getOrCompute("9.9.9.9", () -> failed).join());
        assertEquals("provider failed", failedFuture.getCause().getMessage());

        IPCheckResult retry = ctx.cache.getOrCompute("9.9.9.9",
                () -> CompletableFuture.completedFuture(IPCheckResult.of("ES", false, "retry"))).join();
        assertEquals("ES", retry.countryCode());
    }

    @Test
    void nullResultSkipPersistAndMaintenanceOperationsWork() {
        TestContext ctx = createContext(true, false);

        assertNull(ctx.cache.getOrCompute("10.10.10.10", () -> CompletableFuture.completedFuture(null)).join());
        verify(ctx.store, never()).put(eq("10.10.10.10"), any(CacheValue.class));

        doThrow(new RuntimeException("persist fail")).when(ctx.store).put(eq("11.11.11.11"), any(CacheValue.class));
        assertEquals("SE", ctx.cache.getOrCompute("11.11.11.11",
                () -> CompletableFuture.completedFuture(IPCheckResult.of("SE", true, "provider"))).join().countryCode());
        verify(ctx.logger).warn(contains("disk put failed"));

        ctx.cache.invalidate("12.12.12.12");
        assertTrue(ctx.cache.getIfPresent("12.12.12.12").isEmpty());
        verify(ctx.store, atLeast(2)).cleanupExpired();

        doThrow(new RuntimeException("invalidate fail")).when(ctx.store).put(eq("13.13.13.13"), any(CacheValue.class));
        ctx.cache.invalidate("13.13.13.13");
        ctx.cache.invalidate(null);
        verify(ctx.logger).debug(contains("invalidate failed"));

        when(ctx.store.listAll()).thenReturn(Map.of(
                "a", CacheValue.of(Map.of(), System.currentTimeMillis() + 10_000),
                "b", CacheValue.of(Map.of(), System.currentTimeMillis() + 10_000)
        ));
        assertEquals(2, ctx.cache.diskEntryCount());
        when(ctx.store.listAll()).thenThrow(new RuntimeException("disk fail"));
        assertEquals(-1, ctx.cache.diskEntryCount());
        assertTrue(ctx.cache.memEstimatedSize() >= 0);

        ctx.cache.clearAll();
        verify(ctx.store).delete();
        assertEquals(0, ctx.cache.inflightCount());
        assertEquals(0, ctx.cache.memEstimatedSize());

        doThrow(new RuntimeException("delete fail")).when(ctx.store).delete();
        ctx.cache.clearAll();
        assertEquals(0, ctx.cache.inflightCount());
        ctx.cache.close();
    }

    @Test
    void persistDisabledSkipsDiskWrites() {
        TestContext ctx = createContext(false, false);
        ctx.cache.getOrCompute("14.14.14.14",
                () -> CompletableFuture.completedFuture(IPCheckResult.of("FR", true, "p"))).join();
        verify(ctx.store, never()).put(anyString(), any(CacheValue.class));
    }

    @Test
    void constructorHandlesStartupCleanupFailure() {
        TestContext ctx = createContext(true, true);
        verify(ctx.logger).warn(contains("failed cleanupExpired"));
    }

    private static TestContext createContext(boolean persist, boolean startupCleanupThrows) {
        AntiVPN feature = mock(AntiVPN.class);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        FileCacheStore store = mock(FileCacheStore.class);
        ConfigNode cacheNode = ConfigNode.ofRaw(Map.of(
                "persist", persist,
                "ttl_millis", 60_000L,
                "max_entries", 128L
        ), "cache");

        when(feature.getConfigHandler()).thenReturn(config);
        when(feature.getLogger()).thenReturn(logger);
        when(config.node("cache")).thenReturn(cacheNode);
        if (startupCleanupThrows) {
            doThrow(new RuntimeException("startup cleanup fail")).when(store).cleanupExpired();
        } else {
            doNothing().when(store).cleanupExpired();
        }

        PersistentIpCache cache = new PersistentIpCache(feature, store, new MetricsCollector());
        return new TestContext(cache, store, logger);
    }

    private record TestContext(PersistentIpCache cache, FileCacheStore store, FeatureLogger logger) {}
}
