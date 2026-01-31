package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import nl.hauntedmc.proxyfeatures.features.antivpn.api.CountryAPI;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe storage of country codes.
 * Fix #6: username staging is TTL-based so it can't leak indefinitely when login is denied later in the pipeline.
 */
public final class CountryService implements CountryAPI {

    private final ConcurrentHashMap<UUID, String> byUuid = new ConcurrentHashMap<>();
    private final Cache<String, String> stagedByUsernameLower;

    public CountryService(Duration usernameTtl) {
        this.stagedByUsernameLower = Caffeine.newBuilder()
                .expireAfterWrite(usernameTtl)
                .maximumSize(50_000)
                .build();
    }

    @Override
    public Optional<String> getCountry(UUID uuid) {
        String v = byUuid.get(uuid);
        return (v == null || v.isBlank()) ? Optional.empty() : Optional.of(v);
    }

    public void stageForUsername(String username, String countryCode) {
        if (username == null || username.isBlank()) return;
        if (countryCode == null || countryCode.isBlank()) return;
        stagedByUsernameLower.put(username.toLowerCase(Locale.ROOT), normalize(countryCode));
    }

    public void promoteToUuid(String username, UUID uuid) {
        if (username == null || uuid == null) return;
        String key = username.toLowerCase(Locale.ROOT);
        String code = stagedByUsernameLower.getIfPresent(key);
        stagedByUsernameLower.invalidate(key);
        if (code != null && !code.isBlank()) {
            byUuid.put(uuid, code);
        }
    }

    public void clear(UUID uuid) {
        if (uuid != null) byUuid.remove(uuid);
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
