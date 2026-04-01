package nl.hauntedmc.proxyfeatures.features.commandhider.internal;

import nl.hauntedmc.proxyfeatures.features.commandhider.CommandHider;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HiderHandlerTest {

    @Test
    void normalizeCommandLiteralCleansInput() {
        assertEquals("", HiderHandler.normalizeCommandLiteral(null));
        assertEquals("", HiderHandler.normalizeCommandLiteral("   "));
        assertEquals("velocity", HiderHandler.normalizeCommandLiteral("/Velocity"));
        assertEquals("help me", HiderHandler.normalizeCommandLiteral(" /// HELP ME "));
    }

    @Test
    void normalizeToUniqueListPreservesOrderAndDropsInvalidEntries() {
        List<String> normalized = HiderHandler.normalizeToUniqueList(
                List.of("/velocity", "help", " /HELP ", "", "  ", "/velocity"));

        assertEquals(List.of("velocity", "help"), normalized);
        assertEquals(List.of(), HiderHandler.normalizeToUniqueList(null));
        assertEquals(List.of(), HiderHandler.normalizeToUniqueList(List.of(" ", "///")));
    }

    @Test
    void refreshFromConfigBuildsImmutableSnapshot() {
        List<String> raw = new ArrayList<>();
        raw.add("/velocity");
        raw.add("help");
        raw.add(" /HELP ");
        raw.add("");
        raw.add(null);

        HiderHandler handler = new HiderHandler(featureWithHiddenCommands(raw));

        handler.refreshFromConfig();

        assertEquals(List.of("velocity", "help"), handler.hiddenCommandsList());
        assertEquals(Set.of("velocity", "help"), handler.hiddenCommandsSet());
        assertThrows(UnsupportedOperationException.class, () -> handler.hiddenCommandsList().add("extra"));
        assertThrows(UnsupportedOperationException.class, () -> handler.hiddenCommandsSet().add("extra"));
    }

    @Test
    void refreshFromConfigHandlesEmptyAndNullLists() {
        HiderHandler emptyHandler = new HiderHandler(featureWithHiddenCommands(List.of()));
        emptyHandler.refreshFromConfig();
        assertTrue(emptyHandler.hiddenCommandsList().isEmpty());
        assertTrue(emptyHandler.hiddenCommandsSet().isEmpty());

        HiderHandler nullHandler = new HiderHandler(featureWithHiddenCommands(null));
        nullHandler.refreshFromConfig();
        assertTrue(nullHandler.hiddenCommandsList().isEmpty());
        assertTrue(nullHandler.hiddenCommandsSet().isEmpty());

        HiderHandler invalidOnly = new HiderHandler(featureWithHiddenCommands(List.of(" ", "///")));
        invalidOnly.refreshFromConfig();
        assertTrue(invalidOnly.hiddenCommandsList().isEmpty());
        assertTrue(invalidOnly.hiddenCommandsSet().isEmpty());
    }

    private static CommandHider featureWithHiddenCommands(List<String> values) {
        CommandHider feature = mock(CommandHider.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        when(feature.getConfigHandler()).thenReturn(cfg);
        when(cfg.getList("hidden-commands", String.class, List.of())).thenReturn(values);
        return feature;
    }
}
