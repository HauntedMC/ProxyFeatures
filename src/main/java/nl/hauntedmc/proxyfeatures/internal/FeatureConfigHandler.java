package nl.hauntedmc.proxyfeatures.internal;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.config.MainConfigHandler;
import org.spongepowered.configurate.serialize.SerializationException;

public class FeatureConfigHandler extends MainConfigHandler {

    private final String featureName;

    public FeatureConfigHandler(ProxyFeatures plugin, String featureName) {
        super(plugin);
        this.featureName = featureName;
    }

    /**
     * Get a feature-specific setting.
     */
    public Object getSetting(String key) {
        try {
            return config.node("features", featureName, key).get(Object.class);
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }
}
