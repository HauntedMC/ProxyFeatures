package nl.hauntedmc.proxyfeatures.api.io.cache;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CacheValueTest {

    @Test
    void builderAndFactoryCreateValuesAndEnforceValidation() {
        assertThrows(IllegalArgumentException.class, () -> CacheValue.builder(-1));
        assertThrows(NullPointerException.class, () -> CacheValue.of(null, 0));

        CacheValue built = CacheValue.builder(50)
                .with("name", "remy")
                .with("score", 7)
                .build();
        assertEquals("remy", built.getData().get("name"));
        assertEquals(7, built.getData().get("score"));
        assertThrows(UnsupportedOperationException.class, () -> built.getData().put("x", "y"));

        long now = System.currentTimeMillis();
        CacheValue expired = CacheValue.of(Map.of("a", 1), now - 1);
        CacheValue notExpired = CacheValue.of(Map.of("a", 1), now + 60_000);
        assertTrue(expired.isExpired());
        assertFalse(notExpired.isExpired());
    }

    @Test
    void builderRejectsNullKey() {
        assertThrows(NullPointerException.class, () -> CacheValue.builder(1).with(null, "x"));
    }
}
