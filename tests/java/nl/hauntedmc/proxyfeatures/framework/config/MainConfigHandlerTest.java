package nl.hauntedmc.proxyfeatures.framework.config;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MainConfigHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void initializesGlobalDefaultsAndFeatureEnabledHelpers() {
        MainConfigHandler handler = createHandler();

        assertEquals("proxy", handler.getGlobalSetting("server_name", String.class));
        assertEquals("", handler.getGlobalSetting("dataprovider_token", String.class));

        handler.registerFeature("Queue");
        assertFalse(handler.isFeatureEnabled("Queue"));

        handler.setFeatureEnabled("Queue", true);
        assertTrue(handler.isFeatureEnabled("Queue"));

        handler.registerFeature("Queue");
        assertTrue(handler.isFeatureEnabled("Queue"));
    }

    @Test
    void injectFeatureDefaultsPrunesUnknownAndReconcilesTypeChanges() {
        MainConfigHandler handler = createHandler();

        handler.put("features.Queue.unknown", "remove-me");
        handler.put("features.Queue.threshold", "wrong-type");
        handler.put("features.Queue.settings", "wrong-type");
        handler.put("features.Queue.custom", UUID.randomUUID());
        handler.put("features.Queue.enabled", true);

        ConfigMap defaults = new ConfigMap()
                .put("enabled", false)
                .put("threshold", 5)
                .put("names", List.of("a", "b"))
                .put("title", "Queue")
                .put("settings.mode", "normal")
                .put("custom", "value");

        handler.injectFeatureDefaults("Queue", defaults);

        assertNull(handler.get("features.Queue.unknown"));
        assertEquals(5, handler.get("features.Queue.threshold", Integer.class));
        assertEquals(List.of("a", "b"), handler.getList("features.Queue.names", String.class));
        assertEquals("Queue", handler.get("features.Queue.title", String.class));
        assertEquals("normal", handler.get("features.Queue.settings.mode", String.class));
        assertEquals("value", handler.get("features.Queue.custom", String.class));
    }

    @Test
    void cleanupUnusedFeaturesRemovesOnlyUnregisteredSections() {
        MainConfigHandler handler = createHandler();
        handler.put("features.keep.enabled", true);
        handler.put("features.remove.enabled", true);

        handler.cleanupUnusedFeatures(Set.of("keep"));

        assertNotNull(handler.get("features.keep.enabled"));
        assertNull(handler.get("features.remove.enabled"));
    }

    @Test
    void cleanupUnusedFeaturesIsNoOpWhenFeaturesSectionMissing() {
        MainConfigHandler handler = createHandler();
        assertDoesNotThrow(() -> handler.cleanupUnusedFeatures(Set.of("keep")));
    }

    @Test
    void injectFeatureDefaultsWithNullExpectedTypeDoesNotDeleteExistingValue() {
        MainConfigHandler handler = createHandler();
        handler.put("features.Queue.nullable", "keep-me");

        ConfigMap defaults = new ConfigMap()
                .put("nullable", null)
                .put("enabled", true);

        handler.injectFeatureDefaults("Queue", defaults);

        assertEquals("keep-me", handler.get("features.Queue.nullable", String.class));
        assertTrue(handler.isFeatureEnabled("Queue"));
    }

    @Test
    void globalAccessorsReturnTypedValuesAndNodes() {
        MainConfigHandler handler = createHandler();
        handler.put("global.answer", 42);

        assertEquals(42, handler.getGlobalSetting("answer", Integer.class));
        assertEquals(42, handler.getGlobalSetting("answer"));
        assertEquals(42, handler.getGlobalSetting("answer", Integer.class, -1));
        assertEquals(42, handler.globalNode("answer").asRequired(Integer.class));
    }

    @Test
    void reloadConfigCanBeCalled() {
        MainConfigHandler handler = createHandler();
        assertDoesNotThrow(handler::reloadConfig);
    }

    @Test
    void primaryConstructorAndExpectedKindFallbackBranchAreCovered() throws Exception {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        when(plugin.getDataDirectory()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(ComponentLogger.logger("MainConfigHandlerTest"));

        MainConfigHandler handler = new MainConfigHandler(plugin);
        assertEquals("proxy", handler.getGlobalSetting("server_name", String.class));

        Method m = MainConfigHandler.class.getDeclaredMethod("expectedKindForTopKey", String.class, ConfigMap.class);
        m.setAccessible(true);
        Object result = m.invoke(handler, "missing", new ConfigMap().put("known", true));
        assertEquals(null, result);
    }

    private MainConfigHandler createHandler() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        when(plugin.getLogger()).thenReturn(ComponentLogger.logger("MainConfigHandlerTest"));
        ConfigService service = new ConfigService(tempDir, plugin.getLogger(), getClass().getClassLoader());
        return new MainConfigHandler(plugin, service);
    }
}
