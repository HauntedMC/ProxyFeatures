package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Fix #9:
 * - Supports exact IPs (v4/v6)
 * - Supports CIDR ranges (v4/v6)
 * - Optional bypass for private ranges
 */
public final class IpWhitelist {

    private static final Pattern IPV4_WITH_PORT = Pattern.compile("^\\d{1,3}(?:\\.\\d{1,3}){3}:\\d{1,5}$");

    private final boolean allowPrivate;
    private final List<Entry> entries;
    private final List<String> rawEntries; // canonical strings for list command / config

    private IpWhitelist(boolean allowPrivate, List<Entry> entries, List<String> rawEntries) {
        this.allowPrivate = allowPrivate;
        this.entries = List.copyOf(entries);
        this.rawEntries = List.copyOf(rawEntries);
    }

    public static IpWhitelist fromConfig(AntiVPN feature) {
        boolean allowPrivate = feature.getConfigHandler().node("whitelist").get("allow_private_ranges").as(Boolean.class, true);

        // New key
        List<String> list = feature.getConfigHandler().node("whitelist").get("entries").listOf(String.class);
        if (list == null) list = new ArrayList<>();


        List<Entry> parsed = new ArrayList<>();
        List<String> canonical = new ArrayList<>();

        for (String s : list) {
            Optional<String> norm = normalizeEntry(s);
            if (norm.isEmpty()) continue;

            Optional<Entry> e = parse(norm.get());
            if (e.isEmpty()) continue;

            parsed.add(e.get());
            canonical.add(norm.get());
        }

        // De-dup canonical strings (preserve order)
        LinkedHashSet<String> dedup = new LinkedHashSet<>(canonical);
        canonical = new ArrayList<>(dedup);

        return new IpWhitelist(allowPrivate, parsed, canonical);
    }

    public boolean isWhitelisted(String ip) {
        if (ip == null || ip.isBlank()) return false;

        InetAddress addr = parseIpLoose(ip);
        if (addr == null) return false;

        if (allowPrivate && isPrivateOrLocal(addr)) return true;

        for (Entry e : entries) {
            if (e.matches(addr)) return true;
        }
        return false;
    }

    public List<String> entries() {
        return rawEntries;
    }

    public static Optional<String> normalizeEntry(String input) {
        if (input == null) return Optional.empty();
        String s = input.trim();
        if (s.isEmpty()) return Optional.empty();

        // Strip IPv4 port forms like "1.2.3.4:25565"
        if (IPV4_WITH_PORT.matcher(s).matches()) {
            s = s.substring(0, s.indexOf(':'));
        }

        // CIDR normalization
        if (s.contains("/")) {
            String[] parts = s.split("/", 2);
            if (parts.length != 2) return Optional.empty();

            InetAddress base = parseIpLoose(parts[0].trim());
            if (base == null) return Optional.empty();

            int maxBits = base.getAddress().length * 8;
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (prefix < 0 || prefix > maxBits) return Optional.empty();

            return Optional.of(base.getHostAddress() + "/" + prefix);
        }

        // Exact IP normalization
        InetAddress addr = parseIpLoose(s);
        if (addr == null) return Optional.empty();
        return Optional.of(addr.getHostAddress());
    }

    private static Optional<Entry> parse(String normalized) {
        if (normalized.contains("/")) {
            String[] parts = normalized.split("/", 2);
            InetAddress base = parseIpLoose(parts[0]);
            if (base == null) return Optional.empty();
            int prefix = Integer.parseInt(parts[1]);
            return Optional.of(new CidrEntry(base.getAddress(), prefix));
        }

        InetAddress exact = parseIpLoose(normalized);
        if (exact == null) return Optional.empty();
        return Optional.of(new IpEntry(exact.getAddress()));
    }

    private static InetAddress parseIpLoose(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return InetAddress.getByName(s.trim());
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    private static boolean isPrivateOrLocal(InetAddress a) {
        // Covers RFC1918 + loopback + link-local + unique local (IPv6) as "private-ish"
        return a.isSiteLocalAddress()
                || a.isLoopbackAddress()
                || a.isLinkLocalAddress()
                || a.isAnyLocalAddress();
    }

    /* ============================ Entries ============================ */

    private sealed interface Entry permits IpEntry, CidrEntry {
        boolean matches(InetAddress addr);
    }

    private static final class IpEntry implements Entry {
        private final byte[] ip;

        private IpEntry(byte[] ip) {
            this.ip = ip;
        }

        @Override
        public boolean matches(InetAddress addr) {
            return Arrays.equals(ip, addr.getAddress());
        }
    }

    private static final class CidrEntry implements Entry {
        private final byte[] network;
        private final int prefixBits;

        private CidrEntry(byte[] network, int prefixBits) {
            this.network = network;
            this.prefixBits = prefixBits;
        }

        @Override
        public boolean matches(InetAddress addr) {
            byte[] a = addr.getAddress();
            if (a.length != network.length) return false;
            return prefixMatch(a, network, prefixBits);
        }

        private static boolean prefixMatch(byte[] addr, byte[] net, int prefixBits) {
            if (prefixBits <= 0) return true;

            int fullBytes = prefixBits / 8;
            int remBits = prefixBits % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (addr[i] != net[i]) return false;
            }

            if (remBits == 0) return true;

            int mask = 0xFF << (8 - remBits);
            return (addr[fullBytes] & mask) == (net[fullBytes] & mask);
        }
    }
}
