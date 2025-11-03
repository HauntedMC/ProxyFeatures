package nl.hauntedmc.proxyfeatures.api.io.config;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry/factory for YAML files on Velocity (Configurate).
 * - Ensures parent dirs exist.
 * - Optionally copies defaults from plugin resources if the file is missing.
 * - Caches YamlFile per absolute path to share locks & memory across views.
 */
public final class ConfigService {

    private final Path dataDir;
    private final Logger logger;
    private final ClassLoader resources;
    private final ConcurrentHashMap<Path, YamlFile> cache = new ConcurrentHashMap<>();

    /** Preferred: build from the plugin for data dir, logger, and resource classloader. */
    public ConfigService(ProxyFeatures plugin) {
        this(Objects.requireNonNull(plugin.getDataDirectory(), "dataDirectory"),
             Objects.requireNonNull(plugin.getLogger(), "logger"),
             plugin.getClass().getClassLoader());
    }

    /** Generic: supply a data dir + logger + resource classloader. */
    public ConfigService(Path dataDir, Logger logger, ClassLoader resources) {
        this.dataDir = dataDir.toAbsolutePath().normalize();
        this.logger = logger;
        this.resources = resources == null ? ConfigService.class.getClassLoader() : resources;
    }

    /**
     * Opens (and creates if missing) a YAML file at a path relative to the plugin data folder.
     * If copyDefaultsIfPresent is true and a resource exists in the JAR at that path,
     * the resource is copied; otherwise an empty file is created.
     */
    public YamlFile open(String relativePath, boolean copyDefaultsIfPresent) {
        Objects.requireNonNull(relativePath, "relativePath");
        Path abs = dataDir.resolve(relativePath).normalize();

        return cache.computeIfAbsent(abs, p -> {
            try {
                Files.createDirectories(p.getParent());
                if (Files.notExists(p)) {
                    if (copyDefaultsIfPresent) {
                        try (InputStream in = resources.getResourceAsStream(relativePath)) {
                            if (in != null) {
                                Files.copy(in, p);
                                logger.info("[ProxyFeatures] Copied default resource '{}'", relativePath);
                            } else {
                                Files.createFile(p);
                                logger.info("[ProxyFeatures] Created empty file '{}'", relativePath);
                            }
                        }
                    } else {
                        Files.createFile(p);
                        logger.info("[ProxyFeatures] Created empty file '{}'", relativePath);
                    }
                }
                return new YamlFile(p, logger);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to open YAML file: " + p, e);
            }
        });
    }

    /** Convenience: opens a root-scoped view on a YAML file. */
    public ConfigView view(String relativePath, boolean copyDefaultsIfPresent) {
        return new ConfigView(open(relativePath, copyDefaultsIfPresent), "");
    }

    /** Convenience: opens a scoped view at basePath inside a YAML file. */
    public ConfigView view(String relativePath, boolean copyDefaultsIfPresent, String basePath) {
        return new ConfigView(open(relativePath, copyDefaultsIfPresent), basePath);
    }
}
