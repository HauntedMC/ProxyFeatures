package nl.hauntedmc.proxyfeatures.config;

import java.util.*;

/**
 * Immutable view over a (normalized) config node.
 * Traverse nested maps via dotted paths and fetch typed values without casts.
 */
public final class ConfigNode {
    private final Object value; // normalized via ConfigTypes.toPlain
    private final String path;  // dotted path for context

    private ConfigNode(Object normalizedValue, String path) {
        this.value = normalizedValue;
        this.path = path == null ? "" : path;
    }

    /**
     * Create a node from a raw value (already fetched from Configurate) and normalize it.
     */
    public static ConfigNode ofRaw(Object raw, String path) {
        return new ConfigNode(ConfigTypes.toPlain(raw), path);
    }

    public boolean isNull() {
        return value == null;
    }

    public <T> T as(Class<T> type, T defaultValue) {
        return ConfigTypes.convertOrDefault(value, type, defaultValue);
    }

    public <T> T asRequired(Class<T> type) {
        T v = ConfigTypes.convert(value, type);
        if (v == null) throw new IllegalStateException("Required config missing at '" + path + "'");
        return v;
    }

    public ConfigNode get(String key) {
        if (!(value instanceof Map<?, ?> m)) return new ConfigNode(null, childPath(key));
        Object raw = m.get(key);
        return new ConfigNode(ConfigTypes.toPlain(raw), childPath(key));
    }

    public ConfigNode getAt(String dottedPath) {
        if (dottedPath == null || dottedPath.isBlank()) return this;
        String[] parts = dottedPath.split("\\.");
        ConfigNode cur = this;
        for (String p : parts) cur = cur.get(p);
        return cur;
    }

    public Set<String> keys() {
        if (value instanceof Map<?, ?> m) {
            LinkedHashSet<String> out = new LinkedHashSet<>();
            for (Object k : m.keySet()) out.add(String.valueOf(k));
            return out;
        }
        return Collections.emptySet();
    }

    public Map<String, ConfigNode> children() {
        if (!(value instanceof Map<?, ?> m)) return Map.of();
        LinkedHashMap<String, ConfigNode> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String k = String.valueOf(e.getKey());
            out.put(k, new ConfigNode(ConfigTypes.toPlain(e.getValue()), childPath(k)));
        }
        return out;
    }

    public <T> List<T> listOf(Class<T> elemType) {
        return ConfigTypes.convertList(value, elemType);
    }

    public <V> Map<String, V> mapValues(Class<V> valueType) {
        return ConfigTypes.convertMapValues(value, valueType);
    }

    public Object raw() {
        return value;
    }

    public String path() {
        return path;
    }

    private String childPath(String key) {
        return path.isEmpty() ? key : path + "." + key;
    }

    @Override
    public String toString() {
        return "ConfigNode(" + path + ")";
    }
}
