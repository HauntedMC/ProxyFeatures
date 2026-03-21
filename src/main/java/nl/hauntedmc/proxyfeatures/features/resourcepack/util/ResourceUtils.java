package nl.hauntedmc.proxyfeatures.features.resourcepack.util;

import java.util.Locale;

public class ResourceUtils {

    public static byte[] hexToBytes(String hex) {
        if (hex == null) return new byte[0];
        String normalized = hex.trim();
        if ((normalized.length() & 1) != 0) return new byte[0];

        int len = normalized.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(normalized.charAt(i), 16);
            int lo = Character.digit(normalized.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                return new byte[0];
            }
            data[i / 2] = (byte) ((hi << 4) + lo);
        }
        return data;
    }

    public static String getResourcePackName(String url) {
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        if (fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return fileName.substring(0, fileName.length() - 4);
        }
        return fileName;
    }
}
