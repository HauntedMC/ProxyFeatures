package nl.hauntedmc.proxyfeatures.framework.config;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureConfigHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void featureScopedReadsWritesAndGlobalAccessorsWork() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        when(plugin.getDataDirectory()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(ComponentLogger.logger("FeatureConfigHandlerTest"));

        FeatureConfigHandler handler = new FeatureConfigHandler(plugin, "Queue");

        handler.put("enabled", true);
        handler.put("weight", 10);
        handler.globals().put("server_name", "haunted");
        handler.globals().put("token", "abc");

        assertEquals("Queue", handler.featureName());
        assertEquals(true, handler.get("enabled", Boolean.class));
        assertEquals(10, handler.get("weight", Integer.class));
        assertEquals("haunted", handler.getGlobalSetting("server_name", String.class));
        assertEquals("abc", handler.getGlobalSetting("token"));
        assertEquals("abc", handler.getGlobalSetting("token", String.class, ""));
        assertEquals("abc", handler.globalNode("token").asRequired(String.class));
    }

    @Test
    void reloadConfigPreservesPersistedValues() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        when(plugin.getDataDirectory()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(ComponentLogger.logger("FeatureConfigHandlerTest"));

        FeatureConfigHandler handler = new FeatureConfigHandler(plugin, "Queue");
        handler.put("enabled", true);

        handler.reloadConfig();

        assertEquals(true, handler.get("enabled", Boolean.class));
        assertEquals("global", handler.globals().node().path());
    }
}
