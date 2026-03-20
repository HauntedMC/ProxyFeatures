package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import nl.hauntedmc.proxyfeatures.api.io.cache.CacheValue;
import nl.hauntedmc.proxyfeatures.api.io.cache.FileCacheStore;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fix #3 + #4 + #C:
 * - Bounded in-memory cache (Caffeine) with configurable TTL
 * - Persistent disk cache (JsonCacheFile via FileCacheStore)
 * - Inflight dedupe so concurrent lookups for same IP share one request
 */
public final class PersistentIpCache implements AutoCloseable {

    public enum Source { MEM, DISK, MISS }

    public record CacheHit(IPCheckResult result, Source source) {}

    private final AntiVPN feature;
    private final FileCacheStore store;
    private final MetricsCollector metrics;

    private final Cache<String, IPCheckResult> mem;
    private final boolean persist;
    private final long ttlMillis;

    private final ConcurrentHashMap<String, CompletableFuture<IPCheckResult>> inflight = new ConcurrentHashMap<>();

    public PersistentIpCache(AntiVPN feature, FileCacheStore store, MetricsCollector metrics) {
        this.feature = Objects.requireNonNull(feature);
        this.store = Objects.requireNonNull(store);
        this.metrics = Objects.requireNonNull(metrics);

        this.persist = feature.getConfigHandler().node("cache").get("persist").as(Boolean.class, true);
        this.ttlMillis = feature.getConfigHandler().node("cache").get("ttl_millis").as(Long.class, Duration.ofDays(30).toMillis());

        long max = feature.getConfigHandler().node("cache").get("max_entries").as(Long.class, 50_000L);
        Duration ttl = Duration.ofMillis(Math.max(1, ttlMillis));

        this.mem = Caffeine.newBuilder()
                .maximumSize(Math.max(1, max))
                .expireAfterWrite(ttl)
                .build();

        // Ensure disk file removes expired entries opportunistically
        try {
            store.cleanupExpired();
        } catch (Exception t) {
            feature.getLogger().warn("Cache: failed cleanupExpired() on startup: " + t.getMessage());
        }
    }

    public Optional<CacheHit> getIfPresent(String ip) {
        IPCheckResult inMem = mem.getIfPresent(ip);
        if (inMem != null) {
            metrics.incCacheMemHit();
            return Optional.of(new CacheHit(inMem, Source.MEM));
        }

        CacheValue cv = null;
        try {
            cv = store.get(ip);
        } catch (Exception t) {
            feature.getLogger().warn("Cache: disk get failed: " + t.getMessage());
        }
        if (cv == null) return Optional.empty();

        IPCheckResult fromDisk = fromCacheValue(cv);
        if (fromDisk != null) {
            mem.put(ip, fromDisk);
            metrics.incCacheDiskHit();
            return Optional.of(new CacheHit(fromDisk, Source.DISK));
        }

        return Optional.empty();
    }

    public CompletableFuture<IPCheckResult> getOrCompute(String ip, java.util.function.Supplier<CompletableFuture<IPCheckResult>> supplier) {
        // Fast path
        Optional<CacheHit> hit = getIfPresent(ip);
        if (hit.isPresent()) {
            return CompletableFuture.completedFuture(hit.get().result());
        }

        CompletableFuture<IPCheckResult> managed = new CompletableFuture<>();
        CompletableFuture<IPCheckResult> existing = inflight.putIfAbsent(ip, managed);
        if (existing != null) {
            return existing;
        }

        final CompletableFuture<IPCheckResult> supplied;
        try {
            supplied = Objects.requireNonNull(supplier.get(), "supplier returned null future");
        } catch (Exception ex) {
            inflight.remove(ip, managed);
            managed.completeExceptionally(ex);
            return managed;
        }

        supplied.whenComplete((res, ex) -> {
            inflight.remove(ip, managed);
            if (ex != null) {
                managed.completeExceptionally(ex);
                return;
            }

            managed.complete(res);
            if (res == null) return;

            mem.put(ip, res);
            if (persist) {
                try {
                    store.put(ip, toCacheValue(res));
                } catch (Exception t) {
                    feature.getLogger().warn("Cache: disk put failed: " + t.getMessage());
                }
            }
        });

        return managed;
    }

    public void invalidate(String ip) {
        if (ip == null) return;
        mem.invalidate(ip);
        if (persist) {
            // JsonCacheFile doesn't expose a direct delete-by-key; easiest is overwrite with 0 TTL and cleanup.
            try {
                store.put(ip, CacheValue.builder(0).with("deleted", true).build());
                store.cleanupExpired();
            } catch (Exception t) {
                feature.getLogger().debug("Cache: invalidate failed for " + ip + ": " + t.getMessage());
            }
        }
    }

    public void clearAll() {
        mem.invalidateAll();
        inflight.clear();
        try {
            store.delete();
        } catch (Exception t) {
            feature.getLogger().warn("Cache: clearAll delete failed: " + t.getMessage());
        }
    }

    public long memEstimatedSize() {
        return mem.estimatedSize();
    }

    public int diskEntryCount() {
        try {
            Map<String, CacheValue> all = store.listAll();
            return all.size();
        } catch (Exception t) {
            return -1;
        }
    }

    public int inflightCount() {
        return inflight.size();
    }

    private CacheValue toCacheValue(IPCheckResult r) {
        return CacheValue.builder(ttlMillis)
                .with("country", r.countryCode())
                .with("vpn", r.vpn())
                .with("provider", r.providerId())
                .with("ts", r.timestamp())
                .build();
    }

    private IPCheckResult fromCacheValue(CacheValue cv) {
        if (cv == null || cv.isExpired()) return null;

        Map<String, Object> d = cv.getData();
        String cc = asString(d.get("country"));
        Boolean vpn = asBoolean(d.get("vpn"));
        String provider = asString(d.get("provider"));

        long ts = 0L;
        Object tsObj = d.get("ts");
        if (tsObj instanceof Number n) ts = n.longValue();
        if (ts <= 0) ts = System.currentTimeMillis();

        return new IPCheckResult(cc, vpn, provider, ts);
    }

    private static String asString(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static Boolean asBoolean(Object o) {
        switch (o) {
            case null -> {
                return null;
            }
            case Boolean b -> {
                return b;
            }
            case Number n -> {
                return n.intValue() != 0;
            }
            default -> {
            }
        }
        String s = String.valueOf(o).trim().toLowerCase();
        if (s.equals("true") || s.equals("yes") || s.equals("1")) return true;
        if (s.equals("false") || s.equals("no") || s.equals("0")) return false;
        return null;
    }

    @Override
    public void close() {
        // Nothing to shut down.
    }
}
