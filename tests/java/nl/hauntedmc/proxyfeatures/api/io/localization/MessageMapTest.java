package nl.hauntedmc.proxyfeatures.api.io.localization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageMapTest {

    @Test
    void addAndGetMessagesExposeMutableStore() {
        MessageMap map = new MessageMap();
        map.add("a.b", "value");

        assertEquals("value", map.getMessages().get("a.b"));
        assertEquals(1, map.getMessages().size());
    }
}
