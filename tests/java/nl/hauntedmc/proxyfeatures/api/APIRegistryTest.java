package nl.hauntedmc.proxyfeatures.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class APIRegistryTest {

    @AfterEach
    void clearRegistry() {
        APIRegistry.clear();
    }

    @Test
    void registerGetUnregisterAndClearWork() {
        APIRegistry.register(String.class, "value");
        assertEquals("value", APIRegistry.get(String.class).orElseThrow());

        APIRegistry.unregister(String.class);
        assertTrue(APIRegistry.get(String.class).isEmpty());

        APIRegistry.register(Integer.class, 42);
        APIRegistry.clear();
        assertTrue(APIRegistry.get(Integer.class).isEmpty());
    }

    @Test
    void registerRejectsNullArguments() {
        assertThrows(IllegalArgumentException.class, () -> APIRegistry.register(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> APIRegistry.register(String.class, null));
    }
}
