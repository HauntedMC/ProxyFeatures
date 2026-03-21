package nl.hauntedmc.proxyfeatures.api.io.resource;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourceHandler {
    private final ProxyFeatures plugin;
    private final String resourcePath;
    private final Path file;
    private final ConfigurationLoader<CommentedConfigurationNode> loader;
    private CommentedConfigurationNode config;

    public ResourceHandler(ProxyFeatures plugin, String fileName) {
        this.plugin = plugin;
        this.resourcePath = fileName;
        Path dataDir = plugin.getDataDirectory().toAbsolutePath().normalize();
        Path resolved = dataDir.resolve(fileName).normalize();
        if (!resolved.startsWith(dataDir)) {
            throw new IllegalArgumentException("Resource path escapes data directory: " + fileName);
        }
        this.file = resolved;
        this.loader = YamlConfigurationLoader.builder()
                .path(file)
                .nodeStyle(NodeStyle.BLOCK)
                .build();
        ensureFileExists();
        load();
    }

    /**
     * Ensures that the file exists in the data directory.
     * If it does not exist, attempts to copy the default from the plugin resources.
     */
    private void ensureFileExists() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                try (InputStream in = openResourceStream()) {
                    if (in != null) {
                        Files.copy(in, file);
                        return;
                    } else {
                        plugin.getLogger().warn("Default '{}' not found in resources. Creating empty file '{}'.", resourcePath, file);
                    }
                }
                Files.createFile(file);
            }
        } catch (IOException e) {
            plugin.getLogger().error("Error ensuring file exists: {}", file, e);
        }
    }

    /**
     * Loads the configuration from disk.
     */
    private void load() {
        try {
            this.config = loader.load();
        } catch (IOException e) {
            plugin.getLogger().error("Error loading file: {}", file, e);
            this.config = CommentedConfigurationNode.root();
        }
    }

    /**
     * Reloads the configuration from disk.
     */
    public void reload() {
        load();
    }

    /**
     * Returns the current configuration node.
     */
    public CommentedConfigurationNode getConfig() {
        return config;
    }

    /**
     * Saves any changes to the file.
     */
    public void save() {
        try {
            loader.save(config);
        } catch (IOException e) {
            plugin.getLogger().error("Error saving file: {}", file, e);
        }
    }

    private InputStream openResourceStream() {
        ClassLoader classLoader = plugin.getClass().getClassLoader();
        InputStream in = classLoader.getResourceAsStream(resourcePath);
        if (in != null) {
            return in;
        }

        String fallback = file.getFileName().toString();
        return classLoader.getResourceAsStream(fallback);
    }
}
