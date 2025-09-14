package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class IPCheckCache {

    private final ConcurrentHashMap<String, IPCheckResult> cache = new ConcurrentHashMap<>();
    private final long ttlMillis = TimeUnit.DAYS.toMillis(30);

    /**
     * Retrieves a cached result if it exists and is not expired.
     */
    public IPCheckResult get(String ip) {
        IPCheckResult result = cache.get(ip);
        if (result != null && (System.currentTimeMillis() - result.timestamp()) < ttlMillis) {
            return result;
        } else {
            cache.remove(ip);
            return null;
        }
    }

    /**
     * Caches the result for the given IP.
     */
    public void put(String ip, IPCheckResult result) {
        cache.put(ip, result);
    }
}
