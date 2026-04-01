package nl.hauntedmc.proxyfeatures.features.connectioninfo.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PingCommandPolicyTest {

    @Test
    void colorCodeForPingRespectsThresholds() {
        assertEquals("&a", PingCommandPolicy.colorCodeForPing(100, 150, 250));
        assertEquals("&e", PingCommandPolicy.colorCodeForPing(200, 150, 250));
        assertEquals("&c", PingCommandPolicy.colorCodeForPing(300, 150, 250));
    }

    @Test
    void suggestionsReturnAllPlayersForEmptyInput() {
        List<String> out = PingCommandPolicy.suggestions(new String[0], List.of("Remy", "Alex"));
        assertEquals(List.of("Remy", "Alex"), out);
    }

    @Test
    void suggestionsFilterPlayersCaseInsensitively() {
        List<String> out = PingCommandPolicy.suggestions(new String[]{"re"}, List.of("Remy", "Alex", "Rex"));
        assertEquals(List.of("Remy", "Rex"), out);
    }
}
