package nl.hauntedmc.proxyfeatures.features.antivpn.listener;

import java.net.InetSocketAddress;
import java.util.Locale;

final class AntiVPNPreLoginPolicy {

    private AntiVPNPreLoginPolicy() {
    }

    static String resolveIp(InetSocketAddress address) {
        if (address == null) {
            return null;
        }

        String ip = address.getAddress() != null
                ? address.getAddress().getHostAddress()
                : address.getHostString();

        if (ip == null || ip.isBlank()) {
            return null;
        }
        return ip;
    }

    static boolean shouldStageCountry(String country, String playerName) {
        return country != null
                && !country.isBlank()
                && playerName != null
                && !playerName.isBlank();
    }

    static String normalizeCountry(String country) {
        return country.toUpperCase(Locale.ROOT);
    }
}
