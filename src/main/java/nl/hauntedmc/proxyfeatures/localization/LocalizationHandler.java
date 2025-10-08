package nl.hauntedmc.proxyfeatures.localization;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.commonlib.localization.Language;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.commonlib.util.ComponentUtils;
import nl.hauntedmc.commonlib.util.PlaceholderUtils;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.common.resources.ResourceHandler;
import nl.hauntedmc.proxyfeatures.common.util.LanguageUtils;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.util.EnumMap;
import java.util.Map;

public class LocalizationHandler {
    public static final String LANG_DIR = "lang";

    private final ProxyFeatures plugin;
    private final ResourceHandler defaultMessagesResource;
    private final EnumMap<Language, ResourceHandler> languageResources = new EnumMap<>(Language.class);

    public LocalizationHandler(ProxyFeatures plugin) {
        this.plugin = plugin;
        this.defaultMessagesResource = new ResourceHandler(plugin, LANG_DIR + "/messages.yml");
        loadLanguageFiles();
    }

    /**
     * Loads each language file using the ResourceHandler.
     */
    private void loadLanguageFiles() {
        for (Language lang : Language.values()) {
            String resourcePath = LANG_DIR + "/" + lang.getFileName();
            ResourceHandler resource = new ResourceHandler(plugin, resourcePath);
            if (resource.getConfig() == null) {
                plugin.getLogger().warn("Language file {} not found. Please create it manually.", lang.getFileName());
                continue;
            }
            languageResources.put(lang, resource);
        }
    }

    /**
     * Reloads both the default messages and language-specific files.
     */
    public void reloadLocalization() {
        defaultMessagesResource.reload();
        languageResources.values().forEach(ResourceHandler::reload);
        plugin.getLogger().info("All localization files reloaded.");
    }

    /**
     * Registers multiple default messages.
     */
    public void registerDefaultMessages(MessageMap messageMap) {
        boolean changes = false;
        CommentedConfigurationNode config = defaultMessagesResource.getConfig();
        for (Map.Entry<String, String> entry : messageMap.getMessages().entrySet()) {
            String key = entry.getKey();
            String defaultValue = entry.getValue();
            if (isNodeMissing(config, key)) {
                try {
                    config.node((Object[]) key.split("\\.")).set(defaultValue);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                changes = true;
            }
        }
        if (changes) {
            defaultMessagesResource.save();
        }
    }

    // --- Fluent Builder API ---

    public MessageBuilder getMessage(String key) {
        return new MessageBuilder(key);
    }

    public class MessageBuilder {
        private final String key;
        private Audience audience;
        private Map<String, String> placeholders;

        private MessageBuilder(String key) {
            this.key = key;
        }

        public MessageBuilder forAudience(Audience audience) {
            this.audience = audience;
            return this;
        }

        public MessageBuilder withPlaceholders(Map<String, String> placeholders) {
            this.placeholders = placeholders;
            return this;
        }

        public Component build() {
            String rawMessage;
            if (audience instanceof Player) {
                rawMessage = getTranslatedMessage(key, (Player) audience);
            } else {
                rawMessage = defaultMessagesResource.getConfig().node((Object[]) key.split("\\."))
                        .getString("&cMessage not found: " + key);
            }
            return parseAndDeserialize(rawMessage, placeholders);
        }
    }

    // --- Private Helper Methods ---

    private boolean isNodeMissing(CommentedConfigurationNode node, String key) {
        return node.node((Object[]) key.split("\\.")).virtual();
    }

    /**
     * Retrieves a translated message for the player based on their language settings.
     * Falls back to the default message if a localized version is not found.
     */
    private String getTranslatedMessage(String key, Player targetPlayer) {
        Language language = LanguageUtils.getPlayerLanguage(targetPlayer);
        String message = null;
        if (language != null) {
            ResourceHandler resource = languageResources.get(language);
            if (resource != null && !isNodeMissing(resource.getConfig(), key)) {
                message = resource.getConfig().node((Object[]) key.split("\\.")).getString();
            }
        }
        if (message == null) {
            message = defaultMessagesResource.getConfig().node((Object[]) key.split("\\."))
                    .getString("&cMessage not found: " + key);
        }
        return message;
    }

    /**
     * Applies placeholders, converts legacy to MiniMessage tags, then parses as MiniMessage Component.
     */
    private Component parseAndDeserialize(String message, Map<String, String> placeholders) {
        if (placeholders != null) {
            message = PlaceholderUtils.parsePlaceholders(message, placeholders);
        }
        String mmReady = legacyToMiniMessage(message);
        return ComponentUtils.deserializeMMComponent(mmReady);
    }

    /**
     * Convert legacy color/format codes (both & and §), including Spigot hex (&x&R&RG&G&B&B) and &#RRGGBB,
     * into MiniMessage tags so strings can mix legacy and MiniMessage safely.
     */
    private static String legacyToMiniMessage(String input) {
        if (input == null || input.isEmpty()) return input;

        // Normalize § to &
        input = input.replace('§', '&');

        // Hex color formats:
        // 1) "&#RRGGBB"  -> "<#RRGGBB>"
        input = input.replaceAll("(?i)&#([0-9a-f]{6})", "<#$1>");

        // 2) "&x&R&R&G&G&B&B" (Spigot-style) -> "<#RRGGBB>"
        input = input.replaceAll(
                "(?i)&x&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])&([0-9a-f])",
                "<#$1$2$3$4$5$6>"
        );

        // Standard color codes
        input = input
                .replaceAll("(?i)&0", "<black>")
                .replaceAll("(?i)&1", "<dark_blue>")
                .replaceAll("(?i)&2", "<dark_green>")
                .replaceAll("(?i)&3", "<dark_aqua>")
                .replaceAll("(?i)&4", "<dark_red>")
                .replaceAll("(?i)&5", "<dark_purple>")
                .replaceAll("(?i)&6", "<gold>")
                .replaceAll("(?i)&7", "<gray>")
                .replaceAll("(?i)&8", "<dark_gray>")
                .replaceAll("(?i)&9", "<blue>")
                .replaceAll("(?i)&a", "<green>")
                .replaceAll("(?i)&b", "<aqua>")
                .replaceAll("(?i)&c", "<red>")
                .replaceAll("(?i)&d", "<light_purple>")
                .replaceAll("(?i)&e", "<yellow>")
                .replaceAll("(?i)&f", "<white>");

        // Formatting codes
        input = input
                .replaceAll("(?i)&l", "<bold>")
                .replaceAll("(?i)&n", "<underlined>")
                .replaceAll("(?i)&m", "<strikethrough>")
                .replaceAll("(?i)&o", "<italic>")
                .replaceAll("(?i)&k", "<obfuscated>")
                .replaceAll("(?i)&r", "<reset>");

        return input;
    }
}
