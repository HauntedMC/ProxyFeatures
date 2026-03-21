package nl.hauntedmc.proxyfeatures.features.commandlogger.internal;

import nl.hauntedmc.proxyfeatures.features.commandlogger.CommandLogger;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class LogHandlerTest {

    @Test
    void logProxyCommandFormatsMessageAsExpected() {
        CommandLogger feature = mock(CommandLogger.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        when(feature.getLogger()).thenReturn(logger);

        LogHandler handler = new LogHandler(feature);
        handler.logProxyCommand("Console", "velocity info");

        verify(logger).info("Console executed command: /velocity info");
    }

    @Test
    void logProxyCommandDoesNotDuplicateLeadingSlash() {
        CommandLogger feature = mock(CommandLogger.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        when(feature.getLogger()).thenReturn(logger);

        LogHandler handler = new LogHandler(feature);
        handler.logProxyCommand("Console", "/velocity info");

        verify(logger).info("Console executed command: /velocity info");
    }
}
