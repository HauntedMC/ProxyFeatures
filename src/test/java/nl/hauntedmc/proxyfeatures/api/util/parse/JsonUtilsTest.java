package nl.hauntedmc.proxyfeatures.api.util.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonUtilsTest {

    @Test
    void escapeJsonEscapesBackslashQuotesAndNewlines() {
        String input = "a\\b\"c\nd";
        assertEquals("a\\\\b\\\"c\\nd", JsonUtils.escapeJson(input));
    }
}
