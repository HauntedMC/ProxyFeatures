package nl.hauntedmc.proxyfeatures.api.io.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CacheTypeTest {

    @Test
    void enumValuesAreStable() {
        assertEquals(CacheType.JSON, CacheType.valueOf("JSON"));
        assertEquals(CacheType.SQLITE, CacheType.valueOf("SQLITE"));
    }
}
