package nl.hauntedmc.proxyfeatures.api.io.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigNodeTest {

    @Test
    void nodeTraversalTypedReadsAndChildViewsWork() {
        ConfigNode root = ConfigNode.ofRaw(Map.of(
                "global", Map.of("name", "proxy"),
                "list", List.of("1", "2"),
                "weights", Map.of("a", "1")
        ), "root");

        assertFalse(root.isNull());
        assertEquals("proxy", root.getAt("global.name").asRequired(String.class));
        assertEquals("proxy", root.get("global").get("name").as(String.class, "fallback"));
        assertEquals(List.of("1", "2"), root.get("list").listOf(String.class));
        assertEquals(Map.of("a", 1), root.get("weights").mapValues(Integer.class));
        assertTrue(root.keys().contains("global"));
        assertTrue(root.children().containsKey("global"));
        assertEquals("root.global", root.get("global").path());
        assertTrue(root.toString().contains("root"));
    }

    @Test
    void requiredAndMissingNodesBehaveAsExpected() {
        ConfigNode missing = ConfigNode.ofRaw(null, "x");
        assertTrue(missing.isNull());
        assertNull(missing.raw());
        assertEquals("fallback", missing.as(String.class, "fallback"));
        assertThrows(IllegalStateException.class, () -> missing.asRequired(String.class));
        assertTrue(missing.keys().isEmpty());
        assertTrue(missing.children().isEmpty());
    }

    @Test
    void nullRootPathBlankTraversalAndNonMapChildrenAreHandled() {
        ConfigNode root = ConfigNode.ofRaw(Map.of("a", 1), null);
        assertEquals("", root.path());
        assertSame(root, root.getAt("  "));
        assertEquals("a", root.get("a").path());

        ConfigNode nonMap = ConfigNode.ofRaw(1, "n");
        assertTrue(nonMap.get("child").isNull());
        assertEquals("n.child", nonMap.get("child").path());
    }
}
