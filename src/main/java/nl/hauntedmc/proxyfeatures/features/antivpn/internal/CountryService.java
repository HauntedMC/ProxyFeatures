package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import nl.hauntedmc.proxyfeatures.features.antivpn.api.CountryAPI;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory storage of country codes.
 */
public class CountryService implements CountryAPI {

    // Final mapping by UUID -> ISO country code (uppercased)
    private final ConcurrentHashMap<UUID, String> byUuid = new ConcurrentHashMap<>();
    // Temporary mapping during login: username(lowercase) -> ISO code
    private final ConcurrentHashMap<String, String> byUsername = new ConcurrentHashMap<>();

    @Override
    public Optional<String> getCountry(UUID uuid) {
        String v = byUuid.get(uuid);
        return v == null || v.isBlank() ? Optional.empty() : Optional.of(v);
    }

    /**
     * Stage the detected country for a username during PreLogin.
     */
    public void stageForUsername(String username, String countryCode) {
        if (username == null || username.isBlank() || countryCode == null || countryCode.isBlank()) return;
        byUsername.put(username.toLowerCase(Locale.ROOT), normalize(countryCode));
    }

    /**
     * Promote staged username mapping to UUID after successful login.
     */
    public void promoteToUuid(String username, UUID uuid) {
        if (username == null || uuid == null) return;
        String code = byUsername.remove(username.toLowerCase(Locale.ROOT));
        if (code != null && !code.isBlank()) {
            byUuid.put(uuid, code);
        }
    }

    /**
     * Cleanup on disconnect (optional; keep if you don't want to persist across sessions).
     */
    public void clear(UUID uuid) {
        if (uuid != null) byUuid.remove(uuid);
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
