package nl.hauntedmc.proxyfeatures.localization;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.common.util.TextUtils;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class LocalizationHandler {

    private final ProxyFeatures plugin;
    private CommentedConfigurationNode messagesConfig;
    private final ConfigurationLoader<CommentedConfigurationNode> loader;
    private final Path messagesFile;

    public LocalizationHandler(ProxyFeatures plugin) {
        this.plugin = plugin;
        this.messagesFile = plugin.getDataDirectory().resolve("messages.yml");
        this.loader = YamlConfigurationLoader.builder()
                .path(messagesFile)
                .build();
        loadMessages();
    }

    private void loadMessages() {
        ensureMessagesFileExists();
        try {
            messagesConfig = loader.load();
        } catch (IOException e) {
            plugin.getLogger().error("Error loading messages.yml", e);
        }
    }

    /**
     * Ensures that the plugin's data directory exists and that the messages.yml file is present.
     * If messages.yml does not exist, it is copied from the plugin's resources.
     */
    private void ensureMessagesFileExists() {
        // Ensure the data directory exists.
        try {
            Files.createDirectories(messagesFile.getParent());
        } catch (IOException e) {
            plugin.getLogger().error("Could not create plugin data directory", e);
        }

        // If the messages file doesn't exist, copy it from the resources.
        if (!Files.exists(messagesFile)) {
            try (InputStream in = plugin.getClass().getResourceAsStream("/messages.yml")) {
                if (in != null) {
                    Files.copy(in, messagesFile);
                } else {
                    plugin.getLogger().error("Could not find messages.yml in resources!");
                }
            } catch (IOException e) {
                plugin.getLogger().error("Failed to copy messages.yml", e);
            }
        }
    }

    public void reloadLocalization() {
        loadMessages();
        plugin.getLogger().info("Localization file reloaded.");
    }

    /**
     * Register multiple default messages in a single pass.
     * If a key is missing, set the default value and save the file.
     */
    public void registerDefaultMessages(MessageMap messageMap) {
        boolean changes = false;
        for (Map.Entry<String, String> entry : messageMap.getMessages().entrySet()) {
            if (isNodeMissing(entry.getKey())) {
                try {
                    messagesConfig.node((Object[]) entry.getKey().split("\\.")).set(entry.getValue());
                    changes = true;
                } catch (SerializationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (changes) {
            saveMessagesFile();
        }
    }

    /**
     * Register a single default message.
     */
    public void registerDefaultMessage(String key, String defaultValue) {
        if (isNodeMissing(key)) {
            try {
                messagesConfig.node((Object[]) key.split("\\.")).set(defaultValue);
            } catch (SerializationException e) {
                throw new RuntimeException(e);
            }
            saveMessagesFile();
        }
    }

    private boolean isNodeMissing(String key) {
        return messagesConfig.node((Object[]) key.split("\\.")).virtual();
    }

    private void saveMessagesFile() {
        try {
            loader.save(messagesConfig);
        } catch (IOException e) {
            plugin.getLogger().error("Could not save messages.yml: {}", e.getMessage(), e);
        }
    }

    // -- Methods to retrieve messages --

    public Component getMessage(String key, Map<String, String> placeholders) {
        String message = messagesConfig.node((Object[]) key.split("\\.")).getString("&cMessage not found: " + key);
        if (placeholders != null) {
            message = TextUtils.parsePlaceholders(message, placeholders);
        }
        message = TextUtils.parseLegacyColors(message);
        return TextUtils.serializeComponent(message);
    }

    public Component getMessage(String key) {
        return getMessage(key, null);
    }
}
