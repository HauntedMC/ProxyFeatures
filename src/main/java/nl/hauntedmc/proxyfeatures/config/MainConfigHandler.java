package nl.hauntedmc.proxyfeatures.config;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.common.resources.ResourceHandler;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.util.Map;
import java.util.Set;

public class MainConfigHandler {
    protected final ResourceHandler configResource;
    protected CommentedConfigurationNode config;

    public MainConfigHandler(ProxyFeatures plugin) {
        this.configResource = new ResourceHandler(plugin, "config.yml");
        this.config = configResource.getConfig();
    }

    /**
     * Reloads config from disk using the ResourceHandler.
     */
    public void reloadConfig() {
        configResource.reload();
        this.config = configResource.getConfig();
    }

    /**
     * Registers a feature in config with `enabled: false` if missing.
     */
    public void registerFeature(String featureName) {
        if (config.node("features", featureName).virtual()) {
            try {
                config.node("features", featureName, "enabled").set(false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            configResource.save();
        }
    }

    /**
     * Injects missing default settings for a specific feature.
     */
    public void injectFeatureDefaults(String featureName, Map<String, Object> defaultValues) {
        boolean updated = false;
        for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
            if (config.node("features", featureName, entry.getKey()).virtual()) {
                try {
                    config.node("features", featureName, entry.getKey()).set(entry.getValue());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                updated = true;
            }
        }
        if (updated) {
            configResource.save();
        }
    }

    /**
     * Checks if a feature is enabled.
     */
    public boolean isFeatureEnabled(String featureName) {
        return config.node("features", featureName, "enabled").getBoolean(false);
    }

    /**
     * Sets a feature’s enabled state.
     */
    public void setFeatureEnabled(String featureName, boolean enabled) {
        try {
            config.node("features", featureName, "enabled").set(enabled);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        configResource.save();
    }

    /**
     * Cleans up removed features from config.
     */
    public void cleanupUnusedFeatures(Set<String> registeredFeatures) {
        CommentedConfigurationNode featuresNode = config.node("features");
        if (!featuresNode.virtual()) {
            for (Object key : featuresNode.childrenMap().keySet()) {
                if (key instanceof String stringKey) {
                    if (!registeredFeatures.contains(stringKey)) {
                        featuresNode.removeChild(stringKey);
                    }
                }
            }
            configResource.save();
        }
    }

    public CommentedConfigurationNode getConfig() {
        return config;
    }

}
