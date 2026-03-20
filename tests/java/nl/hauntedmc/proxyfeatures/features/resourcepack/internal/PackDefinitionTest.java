package nl.hauntedmc.proxyfeatures.features.resourcepack.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PackDefinitionTest {

    @Test
    void recordComponentsAndEqualityWork() {
        PackDefinition a = new PackDefinition("https://example/pack.zip", "abc123");
        PackDefinition b = new PackDefinition("https://example/pack.zip", "abc123");
        PackDefinition c = new PackDefinition("https://example/other.zip", "fff");

        assertEquals("https://example/pack.zip", a.url());
        assertEquals("abc123", a.hash());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
