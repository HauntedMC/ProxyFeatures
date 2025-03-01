package nl.hauntedmc.proxyfeatures.localization;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.audience.Audience;
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
import java.util.EnumMap;
import java.util.Map;

public class LocalizationHandler {

    private final ProxyFeatures plugin;

    // Default messages configuration and file.
    private CommentedConfigurationNode defaultMessagesConfig;
    private final Path defaultMessagesFile;
    private final ConfigurationLoader<CommentedConfigurationNode> defaultLoader;

    // Map to hold language-specific configurations.
    private final EnumMap<Language, CommentedConfigurationNode> languageConfigs = new EnumMap<>(Language.class);

    public LocalizationHandler(ProxyFeatures plugin) {
        this.plugin = plugin;
        this.defaultMessagesFile = plugin.getDataDirectory().resolve("messages.yml");
        this.defaultLoader = YamlConfigurationLoader.builder()
                .path(defaultMessagesFile)
                .build();
        loadDefaultMessages();
        loadLanguageFiles();
    }

    /**
     * Loads the default messages.yml file.
     */
    private void loadDefaultMessages() {
        ensureFileExists(defaultMessagesFile);
        try {
            defaultMessagesConfig = defaultLoader.load();
        } catch (IOException e) {
            plugin.getLogger().error("Error loading messages.yml", e);
        }
    }

    /**
     * Loads each language file based on the Language enum.
     * If a file does not exist, a warning is logged.
     */
    private void loadLanguageFiles() {
        for (Language lang : Language.values()) {
            Path langFile = plugin.getDataDirectory().resolve(lang.getFileName());
            if (!Files.exists(langFile)) {
                plugin.getLogger().warn("Language file {} not found. Please create it manually.", lang.getFileName());
                continue;
            }
            ConfigurationLoader<CommentedConfigurationNode> loader = YamlConfigurationLoader.builder()
                    .path(langFile)
                    .build();
            try {
                CommentedConfigurationNode langConfig = loader.load();
                languageConfigs.put(lang, langConfig);
            } catch (IOException e) {
                plugin.getLogger().error("Error loading language file {}", lang.getFileName(), e);
            }
        }
    }

    /**
     * Reloads both the default and language-specific message files.
     */
    public void reloadLocalization() {
        loadDefaultMessages();
        loadLanguageFiles();
        plugin.getLogger().info("All localization files reloaded.");
    }

    /**
     * Register multiple default messages in the default file.
     * Only keys not already present will be added.
     */
    public void registerDefaultMessages(MessageMap messageMap) {
        boolean changes = false;
        for (Map.Entry<String, String> entry : messageMap.getMessages().entrySet()) {
            String key = entry.getKey();
            String defaultValue = entry.getValue();
            if (isNodeMissing(defaultMessagesConfig, key)) {
                try {
                    defaultMessagesConfig.node((Object[]) key.split("\\.")).set(defaultValue);
                    changes = true;
                } catch (SerializationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (changes) {
            saveDefaultMessagesFile();
        }
    }

    /**
     * Register a single default message.
     */
    public void registerDefaultMessage(String key, String defaultValue) {
        if (isNodeMissing(defaultMessagesConfig, key)) {
            try {
                defaultMessagesConfig.node((Object[]) key.split("\\.")).set(defaultValue);
            } catch (SerializationException e) {
                throw new RuntimeException(e);
            }
            saveDefaultMessagesFile();
        }
    }

    private boolean isNodeMissing(CommentedConfigurationNode node, String key) {
        return node.node((Object[]) key.split("\\.")).virtual();
    }

    private void saveDefaultMessagesFile() {
        try {
            defaultLoader.save(defaultMessagesConfig);
        } catch (IOException e) {
            plugin.getLogger().error("Could not save messages.yml: {}", e.getMessage(), e);
        }
    }

    /**
     * Ensures that the given file exists.
     * If it does not exist, it is copied from the plugin’s resources.
     */
    private void ensureFileExists(Path file) {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            plugin.getLogger().error("Could not create plugin data directory", e);
        }

        if (!Files.exists(file)) {
            try (InputStream in = plugin.getClass().getResourceAsStream("/messages.yml")) {
                if (in != null) {
                    Files.copy(in, file);
                } else {
                    plugin.getLogger().error("Could not find " + "/messages.yml" + " in resources!");
                }
            } catch (IOException e) {
                plugin.getLogger().error("Failed to copy " + "/messages.yml", e);
            }
        }
    }

    // --- Methods to retrieve messages using Audience ---

    /**
     * Retrieves a message based on the target audience.
     * If the audience is a player, it retrieves the message using the player's language.
     * Otherwise, it returns the system message.
     */
    public Component getMessage(String key, Audience target, Map<String, String> placeholders) {
        if (target instanceof Player) {
            return getPlayerMessage(key, (Player) target, placeholders);
        } else {
            return getSystemMessage(key, placeholders);
        }
    }

    /**
     * Overload without placeholders.
     */
    public Component getMessage(String key, Audience target) {
        return getMessage(key, target, null);
    }

    /**
     * Retrieves a system message (from the default messages file).
     */
    private Component getSystemMessage(String key, Map<String, String> placeholders) {
        String message = defaultMessagesConfig.node((Object[]) key.split("\\."))
                .getString("&cMessage not found: " + key);
        if (placeholders != null) {
            message = TextUtils.parsePlaceholders(message, placeholders);
        }
        message = TextUtils.parseLegacyColors(message);
        return TextUtils.serializeComponent(message);
    }

    /**
     * Retrieves a player message using the player's language.
     */
    private Component getPlayerMessage(String key, Player player, Map<String, String> placeholders) {
        String message = getTranslatedMessage(key, player);
        if (placeholders != null) {
            message = TextUtils.parsePlaceholders(message, placeholders);
        }
        // Uncomment and adjust if you have a similar placeholder API integration in Velocity.
        // message = TextUtils.parseWithPAPI(message, player);
        message = TextUtils.parseLegacyColors(message);
        return TextUtils.serializeComponent(message);
    }

    private String getTranslatedMessage(String key, Player player) {
        Language language = getPlayerLanguage(player);
        String message = null;
        if (language != null) {
            CommentedConfigurationNode langConfig = languageConfigs.get(language);
            if (langConfig != null && !isNodeMissing(langConfig, key)) {
                message = langConfig.node((Object[]) key.split("\\.")).getString();
            }
        }
        if (message == null) {
            message = defaultMessagesConfig.node((Object[]) key.split("\\."))
                    .getString("&cMessage not found: " + key);
        }
        return message;
    }

    /**
     * Retrieve the player's language.
     * For now, this returns a default language (e.g. EN), but you can implement
     * language detection based on your own criteria (such as a player’s settings).
     */
    private Language getPlayerLanguage(Player player) {
        // Example: Always return English. Modify this method to return the player's actual language.
        return Language.DE;
    }
}
