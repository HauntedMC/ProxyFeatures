package nl.hauntedmc.proxyfeatures.api.util.text.placeholder;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessagePlaceholdersTest {

    @Test
    void builderAndFactoriesSupportAllValueKinds() {
        MessagePlaceholders base = MessagePlaceholders.of("a", "1");
        MessagePlaceholders built = MessagePlaceholders.builder()
                .addAll(base)
                .addString("name", null)
                .addString("name2", "Remy")
                .addNumber("count", null)
                .addNumber("count2", 7)
                .addComponent("component", null)
                .addComponent("component2", Component.text("TXT"))
                .add("null", null)
                .add("n2", 42)
                .add("componentViaAdd", Component.text("C"))
                .add("stringViaAdd", "S")
                .build();

        assertEquals("1", built.get("a"));
        assertEquals("", built.get("name"));
        assertEquals("Remy", built.get("name2"));
        assertEquals("0", built.get("count"));
        assertEquals("7", built.get("count2"));
        assertEquals("", built.get("component"));
        assertTrue(built.get("component2").contains("TXT"));
        assertEquals("", built.get("null"));
        assertEquals("42", built.get("n2"));
        assertTrue(built.get("componentViaAdd").contains("C"));
        assertEquals("S", built.get("stringViaAdd"));
        assertTrue(built.toString().contains("name"));

        MessagePlaceholders fromMap = MessagePlaceholders.of(Map.of("x", "y"));
        assertEquals("y", fromMap.get("x"));
        assertNull(MessagePlaceholders.empty().get("missing"));
        assertNull(MessagePlaceholders.builder().build().get("missing"));
    }

    @Test
    void applyPlaceholdersReplacesLongestKeysFirstAndHandlesEmptyInputs() {
        MessagePlaceholders placeholders = MessagePlaceholders.builder()
                .addString("a", "1")
                .addString("ab", "2")
                .build();

        assertEquals("2-1", MessagePlaceholders.applyPlaceholders("{ab}-{a}", placeholders));
        assertNull(MessagePlaceholders.applyPlaceholders(null, placeholders));
        assertEquals("x", MessagePlaceholders.applyPlaceholders("x", null));
        assertEquals("x", MessagePlaceholders.applyPlaceholders("x", MessagePlaceholders.empty()));
    }
}
