package nl.hauntedmc.proxyfeatures.config;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.util.Arrays;
import java.util.Collections;

/**
 * Feature-level config handler (Configurate).
 * - Legacy raw: getSetting(key)
 * - Typed: getSetting(key, Class<T>) and with default
 * - High-level: node() / node(key) / nodeAt(path) for painless nested parsing
 */
public class FeatureConfigHandler extends MainConfigHandler {

    private final String featureName;

    public FeatureConfigHandler(ProxyFeatures plugin, String featureName) {
        super(plugin);
        this.featureName = featureName;
    }

    /** Legacy raw getter at 'features.<feature>.<key or dotted path>'. */
    public Object getSetting(String key) {
        try {
            return config.node(path("features." + featureName, key)).get(Object.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Typed getter: coerces to requested type or throws. */
    public <T> T getSetting(String key, Class<T> type) {
        return ConfigTypes.convert(getSetting(key), type);
    }

    /** Typed getter with default. */
    public <T> T getSetting(String key, Class<T> type, T defaultValue) {
        return ConfigTypes.convertOrDefault(getSetting(key), type, defaultValue);
    }

    // -------------------------
    // Convenience for maps/lists
    // -------------------------

    public java.util.Map<String, Object> getMap(String key) {
        return getSetting(key, java.util.Map.class);
    }

    public java.util.Map<String, Object> getMap(String key, java.util.Map<String, Object> def) {
        return getSetting(key, java.util.Map.class, def);
    }

    public <T> java.util.List<T> getList(String key, Class<T> elemType) {
        Object raw = getSetting(key);
        return ConfigTypes.convertList(raw, elemType);
    }

    public <T> java.util.List<T> getList(String key, Class<T> elemType, java.util.List<T> def) {
        try {
            return ConfigTypes.convertList(getSetting(key), elemType);
        } catch (Exception ignored) {
            return def;
        }
    }

    public <V> java.util.Map<String, V> getMapValues(String key, Class<V> valueType) {
        return ConfigTypes.convertMapValues(getSetting(key), valueType);
    }

    public <V> java.util.Map<String, V> getMapValues(String key, Class<V> valueType, java.util.Map<String, V> def) {
        try {
            return ConfigTypes.convertMapValues(getSetting(key), valueType);
        } catch (Exception ignored) {
            return def;
        }
    }

    // -------------------------
    // Node API (zero boilerplate)
    // -------------------------

    /** Node rooted at 'features.<featureName>'. */
    public ConfigNode node() {
        return ConfigNode.ofRaw(getNode(""), "features." + featureName);
    }

    /** Node rooted at 'features.<featureName>.<key>'. */
    public ConfigNode node(String key) {
        return ConfigNode.ofRaw(getNode(key), "features." + featureName + (key.isBlank() ? "" : "." + key));
    }

    /** Node rooted at a dotted path under this feature (e.g., "items.cosmetic-item"). */
    public ConfigNode nodeAt(String dottedPath) {
        return node().getAt(dottedPath);
    }

    /** Typed value at dotted path (throws if invalid). */
    public <T> T getAt(String dottedPath, Class<T> type) {
        return node().getAt(dottedPath).asRequired(type);
    }

    /** Typed value at dotted path with default. */
    public <T> T getAt(String dottedPath, Class<T> type, T defaultValue) {
        return node().getAt(dottedPath).as(type, defaultValue);
    }

    private Object getNode(String dotted) {
        try {
            CommentedConfigurationNode n = config.node(path("features." + featureName, dotted));
            return n.get(Object.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object[] path(String dottedBase, String dottedTail) {
        java.util.List<Object> out = new java.util.ArrayList<>();
        Collections.addAll(out, dottedBase.split("\\."));
        if (dottedTail != null && !dottedTail.isBlank())
            out.addAll(Arrays.asList(dottedTail.split("\\.")));
        return out.toArray();
    }
}
