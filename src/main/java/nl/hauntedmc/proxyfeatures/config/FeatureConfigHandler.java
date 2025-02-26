package nl.hauntedmc.proxyfeatures.config;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

public class FeatureConfigHandler {

    private final CommentedConfigurationNode config;
    private final String featureName;

    public FeatureConfigHandler(ProxyFeatures plugin, String featureName) {
        this.config = plugin.getConfig();
        this.featureName = featureName;
    }

    /**
     * Get a feature-specific setting.
     */
    public Object getSetting(String key) {
        try {
            return config.node("features", featureName, key.split("\\.")).get(Object.class);
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get a boolean setting.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return config.node("features", featureName, key.split("\\.")).getBoolean(defaultValue);
    }
}
