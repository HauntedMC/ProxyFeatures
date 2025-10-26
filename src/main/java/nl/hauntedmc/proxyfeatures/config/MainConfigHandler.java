package nl.hauntedmc.proxyfeatures.config;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.resource.ResourceHandler;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.util.*;

/**
 * Main config handler for ProxyFeatures (Velocity/Configurate).
 * Mirrors the Paper-side ergonomics: dotted defaults, pruning, type reconciliation, typed accessors.
 */
public class MainConfigHandler {
    protected final ResourceHandler configResource;
    protected CommentedConfigurationNode config;
    private final Logger logger;

    public MainConfigHandler(ProxyFeatures plugin) {
        this.configResource = new ResourceHandler(plugin, "config.yml");
        this.config = configResource.getConfig();
        this.logger = plugin.getLogger();
        injectGlobalDefaults(Map.of("server_name", "proxy"));
    }

    /** Reloads config from disk. */
    public void reloadConfig() {
        configResource.reload();
        this.config = configResource.getConfig();
    }

    /** Registers a feature with enabled=false if missing. */
    public void registerFeature(String featureName) {
        CommentedConfigurationNode n = config.node("features", featureName, "enabled");
        if (n.virtual()) {
            try {
                n.set(false);
                configResource.save();
                logger.info("[ProxyFeatures] [Config] Added missing key 'features.{}.enabled' (default=false)", featureName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Injects defaults for a feature, prunes unknown top-level keys, and resets keys whose top-level type changed.
     * Supports dotted defaults (e.g. "anti_afk.enabled"). A dotted default implies the parent ("anti_afk")
     * is a map and must be preserved (not pruned).
     */
    public void injectFeatureDefaults(String featureName, ConfigMap defaultValues) {
        final String base = "features." + featureName;
        boolean updated = false;

        Set<String> allowedTopKeys = topLevelKeysFromDefaults(defaultValues);

        updated |= pruneUnknownFeatureKeys(featureName, allowedTopKeys);
        updated |= reconcileMismatchedFeatureKeyTypes(featureName, defaultValues);

        for (Map.Entry<String, Object> e : defaultValues.entrySet()) {
            Object[] path = path(base, e.getKey());
            CommentedConfigurationNode n = config.node(path);
            if (n.virtual()) {
                try {
                    n.set(e.getValue());
                    updated = true;
                    logger.info("[ProxyFeatures] [Config] Added missing key '{}'", String.join(".", toStrings(path)));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        if (updated) configResource.save();
    }

    /** Checks if a feature is enabled. */
    public boolean isFeatureEnabled(String featureName) {
        return config.node("features", featureName, "enabled").getBoolean(false);
    }

    /** Sets a feature’s enabled state. */
    public void setFeatureEnabled(String featureName, boolean enabled) {
        try {
            config.node("features", featureName, "enabled").set(enabled);
            configResource.save();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Removes feature sections that no longer exist. */
    public void cleanupUnusedFeatures(Set<String> registeredFeatures) {
        CommentedConfigurationNode featuresNode = config.node("features");
        if (featuresNode.virtual()) return;

        boolean updated = false;
        Map<Object, ? extends CommentedConfigurationNode> children = featuresNode.childrenMap();
        for (Object keyObj : new ArrayList<>(children.keySet())) {
            String key = String.valueOf(keyObj);
            if (!registeredFeatures.contains(key)) {
                featuresNode.removeChild(keyObj);
                updated = true;
                logger.info("[ProxyFeatures] [Config] Removed unused feature section 'features.{}'", key);
            }
        }
        if (updated) configResource.save();
    }

    // -------------------------
    // Global setting accessors
    // -------------------------

    /** Legacy raw getter (underlying Configurate value) at "global.<key or dotted path>". */
    public Object getGlobalSetting(String key) {
        try {
            return config.node(path("global", key)).get(Object.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Typed getter. */
    public <T> T getGlobalSetting(String key, Class<T> type) {
        return ConfigTypes.convert(getGlobalSetting(key), type);
    }

    /** Typed getter with default. */
    public <T> T getGlobalSetting(String key, Class<T> type, T defaultValue) {
        return ConfigTypes.convertOrDefault(getGlobalSetting(key), type, defaultValue);
    }

    /** A node view rooted at global.<key>. */
    public ConfigNode globalNode(String key) {
        return ConfigNode.ofRaw(getGlobalSetting(key), "global." + key);
    }

    // =========================
    // Global defaults injection
    // =========================

    private void injectGlobalDefaults(Map<String, Object> defaultValues) {
        boolean updated = false;
        for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
            CommentedConfigurationNode n = config.node(path("global", entry.getKey()));
            if (n.virtual()) {
                try {
                    n.set(entry.getValue());
                    updated = true;
                    logger.info("[ProxyFeatures] [Config] Added missing global key '{}'",
                            "global." + entry.getKey());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (updated) configResource.save();
    }

    // =====================================
    // Helpers: pruning & type reconciliation
    // =====================================

    /** Compute allowed top-level keys from defaults (handles dotted keys). */
    private Set<String> topLevelKeysFromDefaults(ConfigMap defaults) {
        Set<String> out = new LinkedHashSet<>();
        for (String key : defaults.keySet()) {
            int dot = key.indexOf('.');
            out.add(dot >= 0 ? key.substring(0, dot) : key);
        }
        return out;
    }

    /** Remove unknown top-level keys under features.<featureName>. */
    private boolean pruneUnknownFeatureKeys(String featureName, Set<String> allowedTopLevelKeys) {
        CommentedConfigurationNode section = config.node("features", featureName);
        if (section.virtual()) return false;

        boolean changed = false;
        for (Object existingKeyObj : new ArrayList<>(section.childrenMap().keySet())) {
            String existingKey = String.valueOf(existingKeyObj);
            if (!allowedTopLevelKeys.contains(existingKey)) {
                section.removeChild(existingKeyObj);
                changed = true;
                logger.info("[ProxyFeatures] [Config] Removed unknown key 'features.{}.{}'", featureName, existingKey);
            }
        }
        return changed;
    }

    /** Remove existing top-level keys whose kind differs from what defaults imply. */
    private boolean reconcileMismatchedFeatureKeyTypes(String featureName, ConfigMap defaults) {
        CommentedConfigurationNode base = config.node("features", featureName);
        if (base.virtual()) return false;

        boolean changed = false;
        for (Object topKeyObj : new ArrayList<>(base.childrenMap().keySet())) {
            String topKey = String.valueOf(topKeyObj);

            ConfigValueKind expected = expectedKindForTopKey(topKey, defaults);
            if (expected == null) continue; // unknown, handled by pruning

            CommentedConfigurationNode n = base.node(topKeyObj);
            ConfigValueKind actual = classifyNodeKind(n);

            if (actual != null && expected != actual) {
                base.removeChild(topKeyObj);
                changed = true;
                logger.info("[ProxyFeatures] [Config] Removed key 'features.{}.{}' due to schema change",
                        featureName, topKey);
            }
        }
        return changed;
    }

    /** Determine expected kind from defaults (handles dotted keys). */
    private ConfigValueKind expectedKindForTopKey(String topKey, ConfigMap defaults) {
        if (defaults.contains(topKey)) {
            return classifyValueKind(defaults.get(topKey));
        }
        String prefix = topKey + ".";
        for (String k : defaults.keySet()) {
            if (k.startsWith(prefix)) return ConfigValueKind.MAP;
        }
        return null;
    }

    private enum ConfigValueKind { MAP, LIST, BOOLEAN, NUMBER, STRING, OTHER }

    /** Classify a default value’s kind. */
    private ConfigValueKind classifyValueKind(Object value) {
        return switch (value) {
            case null -> null;
            case Map<?, ?> ignored -> ConfigValueKind.MAP;
            case List<?> ignored -> ConfigValueKind.LIST;
            case Boolean ignored -> ConfigValueKind.BOOLEAN;
            case Number ignored -> ConfigValueKind.NUMBER;
            case CharSequence ignored -> ConfigValueKind.STRING;
            default -> ConfigValueKind.OTHER;
        };
    }

    /** Classify actual node kind under Configurate. */
    private ConfigValueKind classifyNodeKind(CommentedConfigurationNode n) {
        if (n.virtual()) return null;
        // Try structural first
        try {
            if (!n.childrenMap().isEmpty()) return ConfigValueKind.MAP;
        } catch (UnsupportedOperationException ignored) {}
        try {
            if (!n.childrenList().isEmpty()) return ConfigValueKind.LIST;
        } catch (UnsupportedOperationException ignored) {}

        Object v;
        try { v = n.get(Object.class); } catch (Exception e) { return ConfigValueKind.OTHER; }
        return classifyValueKind(v);
    }

    // Paths

    private static Object[] path(String dottedLeft, String dottedRight) {
        List<Object> out = new ArrayList<>();
        if (dottedLeft != null && !dottedLeft.isBlank()) {
            Collections.addAll(out, dottedLeft.split("\\."));
        }
        if (dottedRight != null && !dottedRight.isBlank()) {
            Collections.addAll(out, dottedRight.split("\\."));
        }
        return out.toArray();
    }

    private static List<String> toStrings(Object[] arr) {
        List<String> out = new ArrayList<>(arr.length);
        for (Object o : arr) out.add(String.valueOf(o));
        return out;
    }

    public CommentedConfigurationNode getConfig() {
        return config;
    }
}
