package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class MetricsCollector {

    private final LongAdder checks = new LongAdder();
    private final LongAdder allowed = new LongAdder();
    private final LongAdder deniedRegion = new LongAdder();
    private final LongAdder deniedVpn = new LongAdder();
    private final LongAdder errors = new LongAdder();

    private final LongAdder cacheMemHits = new LongAdder();
    private final LongAdder cacheDiskHits = new LongAdder();

    private final ConcurrentHashMap<String, LongAdder> providerSuccess = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> providerFailure = new ConcurrentHashMap<>();

    public void incChecks() { checks.increment(); }
    public void incAllowed() { allowed.increment(); }
    public void incDeniedRegion() { deniedRegion.increment(); }
    public void incDeniedVpn() { deniedVpn.increment(); }
    public void incErrors() { errors.increment(); }

    public void incCacheMemHit() { cacheMemHits.increment(); }
    public void incCacheDiskHit() { cacheDiskHits.increment(); }

    public void incProviderSuccess(String id) {
        providerSuccess.computeIfAbsent(id, k -> new LongAdder()).increment();
    }

    public void incProviderFailure(String id) {
        providerFailure.computeIfAbsent(id, k -> new LongAdder()).increment();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                checks.sum(),
                allowed.sum(),
                deniedRegion.sum(),
                deniedVpn.sum(),
                errors.sum(),
                cacheMemHits.sum(),
                cacheDiskHits.sum()
        );
    }

    public record Snapshot(
            long checks,
            long allowed,
            long deniedRegion,
            long deniedVpn,
            long errors,
            long cacheMemHits,
            long cacheDiskHits
    ) {}
}
