package nl.hauntedmc.proxyfeatures.features.votifier.util;

import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class IpAccessList {

    private static final Pattern IPV4_WITH_PORT = Pattern.compile("^\\d{1,3}(?:\\.\\d{1,3}){3}:\\d{1,5}$");

    private final List<Entry> allow;
    private final List<Entry> deny;

    private IpAccessList(List<Entry> allow, List<Entry> deny) {
        this.allow = List.copyOf(allow);
        this.deny = List.copyOf(deny);
    }

    public static IpAccessList fromCsv(String allowCsv, String denyCsv, FeatureLogger logger) {
        List<Entry> allow = parseCsv(allowCsv, logger, "allowlist");
        List<Entry> deny = parseCsv(denyCsv, logger, "denylist");
        return new IpAccessList(allow, deny);
    }

    public boolean allows(InetAddress addr) {
        if (addr == null) return false;

        for (Entry e : deny) {
            if (e.matches(addr)) return false;
        }

        if (allow.isEmpty()) return true;

        for (Entry e : allow) {
            if (e.matches(addr)) return true;
        }

        return false;
    }

    private static List<Entry> parseCsv(String csv, FeatureLogger logger, String label) {
        if (csv == null || csv.isBlank()) return List.of();

        String[] parts = csv.split(",");
        List<Entry> out = new ArrayList<>();

        for (String raw : parts) {
            Optional<Entry> e = parseEntry(raw);
            if (e.isPresent()) {
                out.add(e.get());
            } else if (logger != null) {
                String s = raw == null ? "" : raw.trim();
                if (!s.isEmpty()) logger.warn("Invalid " + label + " entry ignored: " + s);
            }
        }

        return out;
    }

    private static Optional<Entry> parseEntry(String input) {
        if (input == null) return Optional.empty();
        String s = input.trim();
        if (s.isEmpty()) return Optional.empty();

        if (IPV4_WITH_PORT.matcher(s).matches()) {
            s = s.substring(0, s.indexOf(':'));
        }

        if (s.contains("/")) {
            String[] p = s.split("/", 2);
            InetAddress base = parseIp(p[0].trim());
            if (base == null) return Optional.empty();

            int maxBits = base.getAddress().length * 8;
            int prefix;
            try {
                prefix = Integer.parseInt(p[1].trim());
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            if (prefix < 0 || prefix > maxBits) return Optional.empty();

            return Optional.of(new CidrEntry(base.getAddress(), prefix));
        }

        InetAddress ip = parseIp(s);
        if (ip == null) return Optional.empty();
        return Optional.of(new IpEntry(ip.getAddress()));
    }

    private static InetAddress parseIp(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return InetAddress.getByName(s.trim());
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

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