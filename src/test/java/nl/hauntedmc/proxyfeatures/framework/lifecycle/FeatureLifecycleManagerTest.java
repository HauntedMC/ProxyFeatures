package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class FeatureLifecycleManagerTest {

    @Test
    void cleanupDelegatesToAllManagersInOrderAndExposesInstances() {
        FeatureTaskManager taskManager = mock(FeatureTaskManager.class);
        FeatureCommandManager commandManager = mock(FeatureCommandManager.class);
        FeatureListenerManager listenerManager = mock(FeatureListenerManager.class);
        FeatureDataManager dataManager = mock(FeatureDataManager.class);
        FeatureCacheManager cacheManager = mock(FeatureCacheManager.class);

        FeatureLifecycleManager manager = new FeatureLifecycleManager(
                taskManager,
                commandManager,
                listenerManager,
                dataManager,
                cacheManager
        );

        assertSame(taskManager, manager.getTaskManager());
        assertSame(commandManager, manager.getCommandManager());
        assertSame(listenerManager, manager.getListenerManager());
        assertSame(dataManager, manager.getDataManager());
        assertSame(cacheManager, manager.getCacheManager());

        manager.cleanup();

        InOrder order = inOrder(listenerManager, taskManager, commandManager, dataManager, cacheManager);
        order.verify(listenerManager).unregisterAllListeners();
        order.verify(taskManager).cancelAllTasks();
        order.verify(commandManager).unregisterAllCommands();
        order.verify(commandManager).unregisterAllBrigadierCommands();
        order.verify(dataManager).closeAllConnections();
        order.verify(cacheManager).cleanupAll();
    }
}
