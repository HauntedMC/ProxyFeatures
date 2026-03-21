package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import java.util.Locale;

/**
 * Final resolved information for an IP.
 * timestamp is when we fetched it (useful for debugging/metrics).
 */
public record IPCheckResult(String countryCode, Boolean vpn, String providerId, long timestamp) {

    public static IPCheckResult of(String countryCode, Boolean vpn, String providerId) {
        return new IPCheckResult(countryCode, vpn, providerId, System.currentTimeMillis());
    }

    public String countryUpper() {
        return countryCode == null ? "" : countryCode.trim().toUpperCase(Locale.ROOT);
    }
}
