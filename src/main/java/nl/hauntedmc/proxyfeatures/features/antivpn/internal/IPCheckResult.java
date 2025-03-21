package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

/**
 * Holds the country code and VPN flag for an IP address, along with a timestamp.
 */
public class IPCheckResult {
    private final String countryCode;
    private final boolean vpn;
    private final long timestamp;

    public IPCheckResult(String countryCode, boolean vpn, long timestamp) {
        this.countryCode = countryCode;
        this.vpn = vpn;
        this.timestamp = timestamp;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public boolean isVpn() {
        return vpn;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
