package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsCollectorTest {

    @Test
    void snapshotReflectsCounterIncrements() {
        MetricsCollector metrics = new MetricsCollector();
        metrics.incChecks();
        metrics.incAllowed();
        metrics.incDeniedRegion();
        metrics.incDeniedVpn();
        metrics.incErrors();
        metrics.incCacheMemHit();
        metrics.incCacheDiskHit();
        metrics.incProviderSuccess("proxycheck");
        metrics.incProviderFailure("ip2location");

        MetricsCollector.Snapshot snapshot = metrics.snapshot();
        assertEquals(1, snapshot.checks());
        assertEquals(1, snapshot.allowed());
        assertEquals(1, snapshot.deniedRegion());
        assertEquals(1, snapshot.deniedVpn());
        assertEquals(1, snapshot.errors());
        assertEquals(1, snapshot.cacheMemHits());
        assertEquals(1, snapshot.cacheDiskHits());
    }
}
