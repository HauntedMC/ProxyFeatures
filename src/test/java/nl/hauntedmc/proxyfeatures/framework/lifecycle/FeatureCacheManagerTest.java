package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.cache.CacheDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureCacheManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void getCacheDirectoryCreatesFeatureDirectory() {
        ProxyFeatures plugin = mockPlugin(tempDir);

        FeatureCacheManager manager = new FeatureCacheManager(plugin);
        CacheDirectory directory = manager.getCacheDirectory("Queue", "default");

        assertEquals(
                tempDir.resolve("cache").toFile().getAbsoluteFile(),
                directory.getDirectory().getParentFile().getAbsoluteFile()
        );
        assertTrue(directory.getDirectory().exists());
        manager.cleanupAll();
    }

    private static ProxyFeatures mockPlugin(Path dir) {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        when(plugin.getDataDirectory()).thenReturn(dir);
        when(plugin.getLogger()).thenReturn(ComponentLogger.logger("FeatureCacheManagerTest"));
        return plugin;
    }
}
