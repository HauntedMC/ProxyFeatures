package nl.hauntedmc.proxyfeatures.features;

import nl.hauntedmc.commonlib.featureapi.Feature;
import nl.hauntedmc.commonlib.featureapi.meta.BaseMeta;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.internal.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.proxyfeatures.localization.LocalizationHandler;

import java.util.List;
import java.util.Map;

public abstract class VelocityBaseFeature<T extends BaseMeta> implements Feature {

    private final ProxyFeatures plugin;
    private final T meta;
    private final FeatureConfigHandler configHandler;
    private final FeatureLifecycleManager lifecycleManager;

    protected VelocityBaseFeature(ProxyFeatures plugin, T meta) {
        this.plugin = plugin;
        this.meta = meta;
        this.configHandler = new FeatureConfigHandler(plugin, getFeatureName());
        this.lifecycleManager = new FeatureLifecycleManager(plugin);
    }

    public String getFeatureName() {
        return meta.getFeatureName();
    }

    public String getFeatureVersion() {
        return meta.getFeatureVersion();
    }

    public List<String> getDependencies() {
        return meta.getDependencies();
    }

    public List<String> getPluginDependencies() {
        return meta.getPluginDependencies();
    }

    public ProxyFeatures getPlugin() {
        return plugin;
    }

    public FeatureConfigHandler getConfigHandler() {
        return configHandler;
    }

    public FeatureLifecycleManager getLifecycleManager() {
        return lifecycleManager;
    }

    public LocalizationHandler getLocalizationHandler() {
        return plugin.getLocalizationHandler();
    }

    /**
     * Each feature should define its default settings.
     */
    public abstract Map<String, Object> getDefaultConfig();

    /**
     * Each feature should define its default messages.
     */
    public abstract MessageMap getDefaultMessages();


    /**
     * Feature initialization logic (must be implemented by each feature).
     */
    public abstract void initialize();

    /**
     * Feature disable logic (must be implemented by each feature).
     */
    public abstract void disable();

    /**
     * Properly unloads the feature using the lifecycle manager.
     */
    public void cleanup() {
        plugin.getLogger().info("Disabling {}", getFeatureName());
        disable();
        lifecycleManager.cleanup();
    }
}
