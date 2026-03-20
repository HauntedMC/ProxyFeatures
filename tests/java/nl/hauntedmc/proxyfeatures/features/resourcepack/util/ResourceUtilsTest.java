package nl.hauntedmc.proxyfeatures.features.resourcepack.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ResourceUtilsTest {

    @Test
    void constructorIsAccessible() {
        assertNotNull(new ResourceUtils());
    }

    @Test
    void hexToBytesConvertsHexPayload() {
        assertArrayEquals(new byte[]{(byte) 0x0A, (byte) 0xFF, (byte) 0x10},
                ResourceUtils.hexToBytes("0AFF10"));
    }

    @Test
    void getResourcePackNameStripsZipExtensionCaseInsensitive() {
        assertEquals("pack-v1", ResourceUtils.getResourcePackName("https://cdn.example/pack-v1.zip"));
        assertEquals("pack-v2", ResourceUtils.getResourcePackName("https://cdn.example/pack-v2.ZIP"));
        assertEquals("pack-v3.tar.gz", ResourceUtils.getResourcePackName("https://cdn.example/pack-v3.tar.gz"));
    }
}
