package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.scheduler.Scheduler;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureLifecycleManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void exposesManagersAndCleanupRunsWithoutError() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        when(plugin.getDataDirectory()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(ComponentLogger.logger("FeatureLifecycleManagerTest"));
        when(plugin.getCommandManager()).thenReturn(mock(CommandManager.class));
        when(plugin.getEventManager()).thenReturn(mock(EventManager.class));
        when(plugin.getScheduler()).thenReturn(mock(Scheduler.class));

        FeatureLifecycleManager manager = new FeatureLifecycleManager(plugin);

        assertNotNull(manager.getTaskManager());
        assertNotNull(manager.getCommandManager());
        assertNotNull(manager.getListenerManager());
        assertNotNull(manager.getDataManager());
        assertNotNull(manager.getCacheManager());

        assertDoesNotThrow(manager::cleanup);
    }
}
