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
    private final Path file;
    private final ConfigurationLoader<CommentedConfigurationNode> loader;
    private CommentedConfigurationNode config;

    public ResourceHandler(ProxyFeatures plugin, String fileName) {
        this.plugin = plugin;
        this.file = plugin.getDataDirectory().resolve(fileName);
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
                try (InputStream in = plugin.getClass().getResourceAsStream("/" + file.getFileName().toString())) {
                    if (in != null) {
                        Files.copy(in, file);
                    } else {
                        plugin.getLogger().error("Default {} not found in resources!", file.getFileName());
                    }
                }
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
}
