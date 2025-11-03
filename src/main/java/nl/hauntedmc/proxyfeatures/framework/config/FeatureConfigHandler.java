package nl.hauntedmc.proxyfeatures.framework.config;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigService;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigView;

/**
 * Feature-level config handler (Velocity/Configurate) using the unified ConfigView API.
 */
public class FeatureConfigHandler extends ConfigView {

    private final String featureName;

    /** Convenience if you don't hold a MainConfigHandler instance. */
    public FeatureConfigHandler(ProxyFeatures plugin, String featureName) {
        super(new ConfigService(plugin).open("config.yml", true), "features." + featureName);
        this.featureName = featureName;
    }

    public void reloadConfig() { file.reload(); }

    public String featureName() { return featureName; }

    // ---- Global access (no need to depend on MainConfigHandler) ----
    public ConfigView globals() { return super.globals(); }
    public Object getGlobalSetting(String key) { return globals().get(key); }
    public <T> T getGlobalSetting(String key, Class<T> type) { return globals().get(key, type); }
    public <T> T getGlobalSetting(String key, Class<T> type, T def) { return globals().get(key, type, def); }
    public ConfigNode globalNode(String key) { return globals().node(key); }
}
