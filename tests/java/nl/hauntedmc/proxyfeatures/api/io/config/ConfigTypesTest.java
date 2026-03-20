package nl.hauntedmc.proxyfeatures.api.io.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTypesTest {

    private enum Mode {ALPHA, BETA}

    @Test
    void toPlainDeeplyNormalizesMapsAndLists() {
        Object normalized = ConfigTypes.toPlain(Map.of("k", List.of(Map.of("x", 1))));
        assertInstanceOf(Map.class, normalized);
        Object value = ((Map<?, ?>) normalized).get("k");
        assertInstanceOf(List.class, value);
        assertInstanceOf(Map.class, ((List<?>) value).getFirst());
    }

    @Test
    void convertHandlesScalarsEnumsMapsAndLists() {
        assertEquals("1", ConfigTypes.convert(1, String.class));
        assertEquals(true, ConfigTypes.convert("true", Boolean.class));
        assertEquals(true, ConfigTypes.convert(Boolean.TRUE, boolean.class));
        assertEquals(true, ConfigTypes.convert(Boolean.TRUE, Boolean.class));
        assertEquals(true, ConfigTypes.convert(1, Boolean.class));
        assertEquals(5, ConfigTypes.convert("5", Integer.class));
        assertEquals(5, ConfigTypes.convert(5L, Integer.class));
        assertEquals(6L, ConfigTypes.convert("6", Long.class));
        assertEquals(6L, ConfigTypes.convert(6, Long.class));
        assertEquals(2.5d, ConfigTypes.convert("2.5", Double.class));
        assertEquals(2.5d, ConfigTypes.convert(2.5f, Double.class));
        assertEquals(Mode.ALPHA, ConfigTypes.convert("alpha", Mode.class));

        Map<?, ?> map = ConfigTypes.convert(Map.of("x", 1), Map.class);
        List<?> list = ConfigTypes.convert(List.of(1, 2), List.class);
        assertEquals(1, map.get("x"));
        assertEquals(List.of(1, 2), list);
    }

    @Test
    void convertRejectsUnsupportedOrInvalidConversions() {
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert("abc", Integer.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert("abc", Long.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert("abc", Double.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert(new Object(), long.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert(new Object(), double.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert(new Object(), Boolean.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert("value", ConfigMap.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert("missing", Mode.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert(1, Mode.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert("x", Map.class));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert("x", List.class));
        IllegalArgumentException nullErr = assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convert(List.of(1), Integer.class));
        assertTrue(nullErr.getMessage().contains("Expected int"));
    }

    @Test
    void convertOrDefaultReturnsFallbackOnFailure() {
        assertEquals(9, ConfigTypes.convertOrDefault("x", Integer.class, 9));
        assertEquals(3, ConfigTypes.convertOrDefault(3, Integer.class, 9));
    }

    @Test
    void convertListHandlesRealListsAndSingleScalarShortcut() {
        assertEquals(List.of(1, 2), ConfigTypes.convertList(List.of("1", 2), Integer.class));
        assertEquals(List.of(7), ConfigTypes.convertList("7", Integer.class));
        IllegalArgumentException invalidElement =
                assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convertList(List.of("x"), Integer.class));
        assertTrue(invalidElement.getMessage().contains("Invalid element at index 0"));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convertList(Map.of("x", 1), Integer.class));
        assertNull(ConfigTypes.convertList(null, Integer.class));
    }

    @Test
    void convertMapValuesConvertsEachValueAndRejectsInvalidRawInput() {
        Map<String, Integer> converted = ConfigTypes.convertMapValues(Map.of("a", "1", "b", 2), Integer.class);
        assertEquals(1, converted.get("a"));
        assertEquals(2, converted.get("b"));
        assertThrows(IllegalArgumentException.class, () -> ConfigTypes.convertMapValues(List.of(1), Integer.class));
        assertNull(ConfigTypes.convertMapValues(null, Integer.class));
    }
}
