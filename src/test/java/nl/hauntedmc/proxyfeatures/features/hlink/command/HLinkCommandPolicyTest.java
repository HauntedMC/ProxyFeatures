package nl.hauntedmc.proxyfeatures.features.hlink.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HLinkCommandPolicyTest {

    @Test
    void isValidSyncUsageRequiresExactlySyncAndTarget() {
        assertTrue(HLinkCommandPolicy.isValidSyncUsage(List.of("sync", "Remy")));
        assertTrue(HLinkCommandPolicy.isValidSyncUsage(List.of("SYNC", "Remy")));
        assertFalse(HLinkCommandPolicy.isValidSyncUsage(List.of("sync")));
        assertFalse(HLinkCommandPolicy.isValidSyncUsage(List.of("reload", "Remy")));
    }

    @Test
    void suggestionsProvideSyncHintForFirstArg() {
        List<String> out = HLinkCommandPolicy.suggestions(List.of(""), List.of("Remy"));
        assertEquals(List.of("sync"), out);
    }

    @Test
    void suggestionsFilterOnlinePlayersForSyncSecondArg() {
        List<String> out = HLinkCommandPolicy.suggestions(
                List.of("sync", "re"),
                List.of("Remy", "Alex", "Rex")
        );
        assertEquals(List.of("Remy", "Rex"), out);
    }

    @Test
    void suggestionsReturnEmptyForUnsupportedShapes() {
        assertEquals(List.of(), HLinkCommandPolicy.suggestions(List.of("sync", "re", "extra"), List.of("Remy")));
    }
}
