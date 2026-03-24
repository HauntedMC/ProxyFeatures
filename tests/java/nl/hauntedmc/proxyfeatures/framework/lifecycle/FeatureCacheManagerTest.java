package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.cache.CacheDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureCacheManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void createsBaseFolderAndReturnsCacheDirectories() throws Exception {
        resetInitialized(false);
        ProxyFeatures plugin = mockPlugin(tempDir);

        FeatureCacheManager manager = new FeatureCacheManager(plugin);
        CacheDirectory directory = manager.getCacheDirectory("Queue", "default");

        assertTrue(tempDir.resolve("cache").toFile().exists());
        assertTrue(directory.getDirectory().exists());
        manager.cleanupAll();
    }

    @Test
    void skipsInitializationBlockWhenAlreadyInitialized() throws Exception {
        resetInitialized(true);
        Path isolated = tempDir.resolve("isolated");
        ProxyFeatures plugin = mockPlugin(isolated);

        FeatureCacheManager manager = new FeatureCacheManager(plugin);
        assertFalse(isolated.resolve("cache").toFile().exists());

        CacheDirectory directory = manager.getCacheDirectory("Queue", "default");
        assertTrue(directory.getDirectory().exists());
    }

    private static ProxyFeatures mockPlugin(Path dir) {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        when(plugin.getDataDirectory()).thenReturn(dir);
        when(plugin.getLogger()).thenReturn(ComponentLogger.logger("FeatureCacheManagerTest"));
        return plugin;
    }

    private static void resetInitialized(boolean value) throws Exception {
        Field f = FeatureCacheManager.class.getDeclaredField("initialized");
        f.setAccessible(true);
        f.setBoolean(null, value);
    }
}
