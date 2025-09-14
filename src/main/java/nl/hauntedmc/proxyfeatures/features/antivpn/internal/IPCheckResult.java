package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

/**
 * Holds the country code and VPN flag for an IP address, along with a timestamp.
 */
public record IPCheckResult(String countryCode, boolean vpn, long timestamp) {
}
