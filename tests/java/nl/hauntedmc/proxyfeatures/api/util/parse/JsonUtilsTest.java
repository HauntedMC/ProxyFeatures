package nl.hauntedmc.proxyfeatures.api.util.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JsonUtilsTest {

    @Test
    void escapeJsonEscapesBackslashQuotesAndNewlines() {
        assertNotNull(new JsonUtils());
        String input = "a\\b\"c\nd";
        assertEquals("a\\\\b\\\"c\\nd", JsonUtils.escapeJson(input));
    }
}
