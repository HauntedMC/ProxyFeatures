package nl.hauntedmc.proxyfeatures.api.io.config;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMapTest {

    @Test
    void putGetTypedGetAndCollectionViewsWork() {
        ConfigMap map = new ConfigMap()
                .put("name", "queue")
                .put("enabled", true)
                .put("count", 3);

        assertEquals("queue", map.get("name"));
        assertEquals("queue", map.get("name", String.class));
        assertNull(map.get("missing", String.class));
        assertTrue(map.contains("enabled"));
        assertTrue(map.keySet().contains("count"));
        assertTrue(map.entrySet().stream().anyMatch(e -> e.getKey().equals("name")));

        Map<String, Object> copy = map.toMap();
        copy.put("other", 1);
        assertFalse(map.contains("other"));

        AtomicInteger seen = new AtomicInteger();
        map.forEach((k, v) -> seen.incrementAndGet());
        assertEquals(3, seen.get());
        assertTrue(map.toString().contains("queue"));
    }

    @Test
    void typedGetThrowsOnTypeMismatch() {
        ConfigMap map = new ConfigMap().put("count", 3);
        assertThrows(ClassCastException.class, () -> map.get("count", String.class));
    }
}
