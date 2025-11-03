package nl.hauntedmc.proxyfeatures.api.io.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Typed, thread-safe, CRUD-style API over a YamlFile, optionally rooted at a base path.
 * This is the SINGLE interface all configs use (main, feature-scoped, and local/*.yml).
 */
public class ConfigView {

    public final YamlFile file;
    protected final String base; // "" = root

    public ConfigView(YamlFile file, String basePath) {
        this.file = Objects.requireNonNull(file, "file");
        this.base = basePath == null ? "" : basePath;
    }

    /** Build a sub-view (e.g., view.scope("recipes")). */
    public ConfigView scope(String childBase) {
        String b = base(childBase);
        return new ConfigView(file, b);
    }

    protected String base(String key) {
        if (base.isEmpty()) return key == null || key.isEmpty() ? "" : key;
        return base + (key == null || key.isEmpty() ? "" : "." + key);
    }

    // -------- READS --------

    public Object get(String key) { return file.getRaw(base(key)); }

    public <T> T get(String key, Class<T> type) { return ConfigTypes.convert(get(key), type); }

    public <T> T get(String key, Class<T> type, T def) {
        return ConfigTypes.convertOrDefault(get(key), type, def);
    }

    public <T> List<T> getList(String key, Class<T> elemType) {
        return ConfigTypes.convertList(get(key), elemType);
    }

    public <T> List<T> getList(String key, Class<T> elemType, List<T> def) {
        try { return ConfigTypes.convertList(get(key), elemType); }
        catch (Exception ignored) { return def; }
    }

    public <V> Map<String, V> getMapValues(String key, Class<V> valueType) {
        return ConfigTypes.convertMapValues(get(key), valueType);
    }

    public <V> Map<String, V> getMapValues(String key, Class<V> valueType, Map<String, V> def) {
        try { return ConfigTypes.convertMapValues(get(key), valueType); }
        catch (Exception ignored) { return def; }
    }

    public ConfigNode node() {
        file.lock().readLock().lock();
        try {
            Object raw = file.getRaw(base);
            return ConfigNode.ofRaw(raw, base.isEmpty() ? "<root>" : base);
        } finally {
            file.lock().readLock().unlock();
        }
    }

    public ConfigNode node(String key) {
        return ConfigNode.ofRaw(get(key), base(key));
    }

    public ConfigNode nodeAt(String dottedPath) { return node().getAt(dottedPath); }

    public <T> T getAt(String dottedPath, Class<T> type) { return node().getAt(dottedPath).asRequired(type); }

    public <T> T getAt(String dottedPath, Class<T> type, T def) { return node().getAt(dottedPath).as(type, def); }

    // -------- WRITES --------

    public void put(String dottedPath, Object value) {
        file.setRawAndSave(base(dottedPath), value);
    }

    public void remove(String dottedPath) { put(dottedPath, null); }

    public boolean putIfAbsent(String dottedPath, Object value) {
        String p = base(dottedPath);
        if (!file.contains(p)) {
            file.setRawAndSave(p, value);
            return true;
        }
        return false;
    }

    public <T> T compute(String dottedPath, Class<T> type,
                         UnaryOperator<T> updateFn, Supplier<T> init) throws SerializationException {
        Objects.requireNonNull(updateFn, "updateFn");
        String p = base(dottedPath);

        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode root = file.snapshotUnsafe();
            CommentedConfigurationNode n = root.node(YamlFile.splitPath(p));
            T cur;
            try {
                cur = n.virtual() ? null : ConfigTypes.convert(n.get(Object.class), type);
            } catch (Exception e) {
                cur = null;
            }
            if (cur == null && init != null) cur = init.get();
            T next = Objects.requireNonNull(updateFn.apply(cur), "updateFn returned null");
            n.set(next);
            file.saveNow();
            return next;
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    public void appendToList(String dottedPath, Object value) {
        String p = base(dottedPath);
        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode root = file.snapshotUnsafe();
            CommentedConfigurationNode n = root.node(YamlFile.splitPath(p));
            List<?> current = n.getList(Object.class);
            List<Object> list = new ArrayList<>();
            if (current != null) list.addAll(current);
            list.add(value);
            n.set(list);
            file.saveNow();
        } catch (Exception ignored) {
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    public int removeFromList(String dottedPath, Predicate<Object> predicate) {
        String p = base(dottedPath);
        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode root = file.snapshotUnsafe();
            CommentedConfigurationNode n = root.node(YamlFile.splitPath(p));
            List<?> current = n.getList(Object.class);
            if (current == null || current.isEmpty()) return 0;
            List<Object> list = new ArrayList<>(current);
            int before = list.size();
            list.removeIf(predicate);
            if (list.size() != before) {
                n.set(list);
                file.saveNow();
            }
            return before - list.size();
        } catch (Exception e) {
            return 0;
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    /** Transaction-style batch mutation: everything saved once at the end if anything changed. */
    public void batch(Consumer<Batch> tx) {
        Objects.requireNonNull(tx, "tx");
        file.lock().writeLock().lock();
        try {
            CommentedConfigurationNode root = file.snapshotUnsafe();
            Batch b = new Batch(root);
            tx.accept(b);
            if (b.changed) file.saveNow();
        } finally {
            file.lock().writeLock().unlock();
        }
    }

    /** Direct raw mutation hook (keep for exceptional cases). */
    public void mutateRaw(Consumer<CommentedConfigurationNode> mutator) {
        file.mutateAndSave(mutator);
    }

    public final class Batch {
        private final CommentedConfigurationNode root;
        private boolean changed;

        private Batch(CommentedConfigurationNode root) { this.root = root; }

        public Batch put(String dottedPath, Object value) throws SerializationException {
            root.node(YamlFile.splitPath(base(dottedPath))).set(value);
            changed = true;
            return this;
        }

        public Batch putIfAbsent(String dottedPath, Object value) throws SerializationException {
            CommentedConfigurationNode n = root.node(YamlFile.splitPath(base(dottedPath)));
            if (n.virtual()) {
                n.set(value);
                changed = true;
            }
            return this;
        }

        public <T> Batch compute(String dottedPath, Class<T> type,
                                 UnaryOperator<T> updateFn, Supplier<T> init) throws SerializationException {
            CommentedConfigurationNode n = root.node(YamlFile.splitPath(base(dottedPath)));
            T cur;
            try { cur = n.virtual() ? null : ConfigTypes.convert(n.get(Object.class), type); }
            catch (Exception e) { cur = null; }
            if (cur == null && init != null) cur = init.get();
            T next = Objects.requireNonNull(updateFn.apply(cur));
            n.set(next);
            changed = true;
            return this;
        }

        public Batch appendToList(String dottedPath, Object value) throws SerializationException {
            CommentedConfigurationNode n = root.node(YamlFile.splitPath(base(dottedPath)));
            List<?> current = n.getList(Object.class);
            List<Object> list = new ArrayList<>();
            if (current != null) list.addAll(current);
            list.add(value);
            n.set(list);
            changed = true;
            return this;
        }

        public Batch removeFromList(String dottedPath, java.util.function.Predicate<Object> predicate) throws SerializationException {
            CommentedConfigurationNode n = root.node(YamlFile.splitPath(base(dottedPath)));
            List<?> current = n.getList(Object.class);
            if (current == null || current.isEmpty()) return this;
            List<Object> list = new ArrayList<>(current);
            int before = list.size();
            list.removeIf(predicate);
            if (list.size() != before) {
                n.set(list);
                changed = true;
            }
            return this;
        }

        public Batch remove(String dottedPath) throws SerializationException {
            root.node(YamlFile.splitPath(base(dottedPath))).set(null);
            changed = true;
            return this;
        }
    }

    public ConfigView root() {
        return base.isEmpty() ? this : new ConfigView(file, "");
    }

    public ConfigView at(String basePath) {
        if (basePath == null || basePath.isBlank()) return root();
        return new ConfigView(file, basePath);
    }

    /** Convenience views */
    public ConfigView globals() { return at("global"); }
    public ConfigView features() { return at("features"); }
}
