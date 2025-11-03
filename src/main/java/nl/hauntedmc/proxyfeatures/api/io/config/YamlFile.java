package nl.hauntedmc.proxyfeatures.api.io.config;

import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Owns a single YAML file (Configurate) + its in-memory root node + a read/write lock.
 */
public final class YamlFile {
    private final Path path;
    private final Logger logger;
    private final ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
    private final YamlConfigurationLoader loader;
    private volatile CommentedConfigurationNode root;

    public YamlFile(Path path, Logger logger) {
        this.path = path;
        this.logger = logger;

        this.loader = YamlConfigurationLoader.builder()
            .path(path)
            .nodeStyle(NodeStyle.BLOCK)
            .defaultOptions(ConfigurationOptions.defaults())
            .build();

        reload(); // initial load
    }

    public ReentrantReadWriteLock lock() { return rw; }

    /** Load from disk. */
    public void reload() {
        rw.writeLock().lock();
        try {
            this.root = loader.load();
        } catch (IOException e) {
            logger.error("[ProxyFeatures] Could not load YAML '{}': {}", path, e.getMessage());
            this.root = CommentedConfigurationNode.root();
        } finally {
            rw.writeLock().unlock();
        }
    }

    /** Persist to disk (caller holds write-intent via higher APIs). */
    void saveNow() {
        try {
            loader.save(root);
        } catch (IOException e) {
            logger.error("[ProxyFeatures] Could not save YAML '{}': {}", path, e.getMessage());
        }
    }

    /** Direct raw mutation with automatic save under write lock. */
    public void mutateAndSave(Consumer<CommentedConfigurationNode> mutator) {
        rw.writeLock().lock();
        try {
            mutator.accept(root);
            saveNow();
        } finally {
            rw.writeLock().unlock();
        }
    }

    // -------- Low-level access used by ConfigView --------

    Object getRaw(String absolutePath) {
        rw.readLock().lock();
        try {
            if (absolutePath == null || absolutePath.isBlank()) {
                return root.get(Object.class);
            }
            return root.node(splitPath(absolutePath)).get(Object.class);
        } catch (Exception e) {
            return null;
        } finally {
            rw.readLock().unlock();
        }
    }

    boolean contains(String absolutePath) {
        rw.readLock().lock();
        try {
            if (absolutePath == null || absolutePath.isBlank()) {
                return !root.virtual();
            }
            return !root.node(splitPath(absolutePath)).virtual();
        } finally {
            rw.readLock().unlock();
        }
    }

    void setRawAndSave(String absolutePath, Object value) {
        rw.writeLock().lock();
        try {
            if (absolutePath == null || absolutePath.isBlank()) {
                root.set(value);
            } else {
                root.node(splitPath(absolutePath)).set(value);
            }
            saveNow();
        } catch (Exception e) {
            logger.error("[ProxyFeatures] Failed setting '{}': {}", absolutePath, e.getMessage());
        } finally {
            rw.writeLock().unlock();
        }
    }

    CommentedConfigurationNode snapshotUnsafe() { // guarded by external lock in ConfigView when used
        return root;
    }

    static Object[] splitPath(String dotted) {
        if (dotted == null || dotted.isBlank()) return new Object[0];
        String[] parts = dotted.split("\\.");
        Object[] out = new Object[parts.length];
        System.arraycopy(parts, 0, out, 0, parts.length);
        return out;
    }
}
