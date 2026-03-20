package nl.hauntedmc.proxyfeatures.api.io.cache.impl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import nl.hauntedmc.proxyfeatures.api.io.cache.CacheValue;
import nl.hauntedmc.proxyfeatures.api.io.cache.FileCacheStore;

import java.io.*;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public class JsonCacheFile implements FileCacheStore {
    private static final String EXP_TS = "expirationTimestamp";
    private static final String VALUE = "value";

    private final File file;
    private final Gson gson = new Gson();
    private final Object lock = new Object();
    // key → single { "value": Map, "expirationTimestamp": long }
    private Map<String, Map<String, Object>> rawMap;
    private static final Type RAW_MAP_TYPE =
            new TypeToken<Map<String, Map<String, Object>>>() {
            }.getType();

    public JsonCacheFile(File file) {
        this.file = Objects.requireNonNull(file, "file");
        ensureFileExists();
        load();
    }

    @Override
    public File getUnderlyingFile() {
        return file;
    }

    private void ensureFileExists() {
        if (!file.exists()) {
            try {
                File parent = file.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                file.createNewFile();
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot create cache file " + file, ex);
            }
        }
    }

    private void load() {
        synchronized (lock) {
            try (Reader r = new FileReader(file)) {
                rawMap = gson.fromJson(r, RAW_MAP_TYPE);
                if (rawMap == null) rawMap = new LinkedHashMap<>();
            } catch (IOException ex) {
                throw new IllegalStateException("Cannot load cache file " + file, ex);
            }
        }
    }

    private void saveLocked() {
        try (Writer w = new FileWriter(file)) {
            gson.toJson(rawMap, w);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot save cache file " + file, ex);
        }
    }

    @Override
    public void put(String key, CacheValue value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        synchronized (lock) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(VALUE, value.getData());
            entry.put(EXP_TS, value.getExpirationTimestamp());
            rawMap.put(key, entry);
            saveLocked();
        }
    }

    @Override
    public void cleanupExpired() {
        synchronized (lock) {
            cleanupExpiredLocked();
        }
    }

    private void cleanupExpiredLocked() {
        long now = System.currentTimeMillis();
        rawMap.entrySet().removeIf(e -> {
            Map<String, Object> ent = e.getValue();
            if (ent == null) return true;
            long ts = asLong(ent.getOrDefault(EXP_TS, -1L), -1L);
            return ts >= 0 && now > ts;
        });

        if (rawMap.isEmpty()) {
            if (!file.delete() && file.exists()) {
                file.deleteOnExit();
            }
        } else {
            saveLocked();
        }
    }

    @Override
    public CacheValue get(String key) {
        synchronized (lock) {
            cleanupExpiredLocked();
            return toCacheValue(rawMap.get(key));
        }
    }

    @Override
    public Map<String, CacheValue> listAll() {
        synchronized (lock) {
            cleanupExpiredLocked();
            Map<String, CacheValue> result = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> e : rawMap.entrySet()) {
                CacheValue cv = toCacheValue(e.getValue());
                if (cv != null) {
                    result.put(e.getKey(), cv);
                }
            }
            return result;
        }
    }

    @Override
    public Map<String, CacheValue> find(String regex) {
        synchronized (lock) {
            cleanupExpiredLocked();
            Pattern pat = Pattern.compile(regex);
            Map<String, CacheValue> result = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Object>> e : rawMap.entrySet()) {
                if (!pat.matcher(e.getKey()).matches()) {
                    continue;
                }
                CacheValue cv = toCacheValue(e.getValue());
                if (cv != null) {
                    result.put(e.getKey(), cv);
                }
            }
            return result;
        }
    }

    @Override
    public boolean isEmpty() {
        synchronized (lock) {
            return rawMap.isEmpty();
        }
    }

    @Override
    public void delete() {
        synchronized (lock) {
            rawMap.clear();
            if (!file.delete() && file.exists()) {
                file.deleteOnExit();
            }
        }
    }

    private CacheValue toCacheValue(Map<String, Object> entry) {
        if (entry == null) return null;
        Map<String, Object> data = asDataMap(entry.get(VALUE));
        long ts = asLong(entry.getOrDefault(EXP_TS, -1L), -1L);
        return CacheValue.of(data, ts);
    }

    private static Map<String, Object> asDataMap(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() == null) continue;
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private static long asLong(Object raw, long def) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }
}
