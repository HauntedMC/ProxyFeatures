package nl.hauntedmc.proxyfeatures.config;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class ConfigHandler {
    private final ProxyFeatures plugin;

    private CommentedConfigurationNode config;

    private final Path configFile;
    private final ConfigurationLoader<CommentedConfigurationNode> loader;
    public ConfigHandler(ProxyFeatures plugin) {
        this.plugin = plugin;
        // Use the plugin's data directory to locate config.yml
        this.configFile = plugin.getDataDirectory().resolve("config.yml");
        this.loader = YamlConfigurationLoader.builder()
                .path(configFile)
                .build();

        ensureConfigFileExists();
        reloadConfig();
    }

    /**
     * Ensures the configuration file exists. If not, copies the default from resources.
     */
    private void ensureConfigFileExists() {
        try {
            Files.createDirectories(configFile.getParent());
            if (!Files.exists(configFile)) {
                try (InputStream in = plugin.getClass().getResourceAsStream("/config.yml")) {
                    if (in != null) {
                        Files.copy(in, configFile);
                    } else {
                        plugin.getLogger().error("Default config.yml not found in resources!");
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().error("Error ensuring config file exists", e);
        }
    }

    /**
     * Reloads config from disk.
     */
    public void reloadConfig() {
        try {
            this.config = loader.load();
        } catch (IOException e) {
            plugin.getLogger().error("Error reloading config file", e);
        }
    }

    /**
     * Registers a feature in config with `enabled: false` if missing.
     */
    public void registerFeature(String featureName) {
        if (config.node("features", featureName).virtual()) {
            try {
                config.node("features", featureName, "enabled").set(false);
            } catch (SerializationException e) {
                throw new RuntimeException(e);
            }
            saveConfig();
        }
    }

    /**
     * Injects missing default settings for a specific feature.
     */
    public void injectFeatureDefaults(String featureName, Map<String, Object> defaultValues) {
        boolean updated = false;
        for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
            if (config.node("features", featureName, entry.getKey().split("\\.")).virtual()) {
                try {
                    config.node("features", featureName, entry.getKey().split("\\.")).set(entry.getValue());
                } catch (SerializationException e) {
                    throw new RuntimeException(e);
                }
                updated = true;
            }
        }
        if (updated) {
            saveConfig();
        }
    }

    /**
     * Checks if a feature is enabled.
     */
    public boolean isFeatureEnabled(String featureName) {
        return config.node("features", featureName, "enabled").getBoolean(false);
    }

    public void setFeatureEnabled(String featureName, boolean enabled) {
        try {
            config.node("features", featureName, "enabled").set(enabled);
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
        saveConfig();
    }

    /**
     * Cleans up removed features from config.
     */
    public void cleanupUnusedFeatures(Set<String> registeredFeatures) {
        CommentedConfigurationNode featuresNode = config.node("features");
        if (!featuresNode.virtual()) {
            for (Object key : featuresNode.childrenMap().keySet()) {
                if (key instanceof String stringKey) {
                    if (!registeredFeatures.contains(stringKey)) {
                        featuresNode.removeChild(stringKey);
                    }
                }
            }
            saveConfig();
        }
    }

    private void saveConfig() {
        try {
            loader.save(config);
        } catch (IOException e) {
            plugin.getLogger().error("Error saving config file", e);
        }
    }

    public CommentedConfigurationNode getConfig() {
        return config;
    }
}
