package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;

import java.util.Objects;

public class FeatureLifecycleManager {

    private final FeatureTaskManager taskManager;
    private final FeatureCommandManager commandManager;
    private final FeatureListenerManager listenerManager;
    private final FeatureDataManager dataManager;
    private final FeatureCacheManager cacheManager;

    public FeatureLifecycleManager(ProxyFeatures plugin) {
        this(
                new FeatureTaskManager(plugin),
                new FeatureCommandManager(plugin),
                new FeatureListenerManager(plugin),
                new FeatureDataManager(plugin),
                new FeatureCacheManager(plugin)
        );
    }

    FeatureLifecycleManager(
            FeatureTaskManager taskManager,
            FeatureCommandManager commandManager,
            FeatureListenerManager listenerManager,
            FeatureDataManager dataManager,
            FeatureCacheManager cacheManager
    ) {
        this.taskManager = Objects.requireNonNull(taskManager, "taskManager");
        this.commandManager = Objects.requireNonNull(commandManager, "commandManager");
        this.listenerManager = Objects.requireNonNull(listenerManager, "listenerManager");
        this.dataManager = Objects.requireNonNull(dataManager, "dataManager");
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
    }

    /**
     * Provides access to the task manager.
     */
    public FeatureTaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * Provides access to the command manager.
     */
    public FeatureCommandManager getCommandManager() {
        return commandManager;
    }

    /**
     * Provides access to the listener manager.
     */
    public FeatureListenerManager getListenerManager() {
        return listenerManager;
    }

    /**
     * Provides access to the data manager.
     */
    public FeatureDataManager getDataManager() {
        return dataManager;
    }

    /**
     * Access to the cache manager for this feature.
     */
    public FeatureCacheManager getCacheManager() {
        return cacheManager;
    }

    /**
     * Cleans up all registered listeners, tasks, and commands.
     */
    public void cleanup() {
        listenerManager.unregisterAllListeners();
        taskManager.cancelAllTasks();
        commandManager.unregisterAllCommands();
        commandManager.unregisterAllBrigadierCommands();
        dataManager.closeAllConnections();
        cacheManager.cleanupAll();
    }
}
