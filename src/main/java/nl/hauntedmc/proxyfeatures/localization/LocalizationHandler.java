package nl.hauntedmc.proxyfeatures.localization;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.commonlib.localization.Language;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.commonlib.localization.MessageType;
import nl.hauntedmc.commonlib.util.ComponentUtils;
import nl.hauntedmc.commonlib.util.PlaceholderUtils;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.common.resources.ResourceHandler;
import nl.hauntedmc.proxyfeatures.common.util.VelocityUtils;
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

    /**
     * Entry point for retrieving messages. Use the returned builder to configure
     * the audience, message type, placeholders, and then call build() to get the message.
     *
     * Example usage:
     *
     *     Component comp = localizationHandler
     *                            .message("welcome.message")
     *                            .audience(someAudience)
     *                            .type(MessageType.MiniMessage)
     *                            .placeholders(somePlaceholders)
     *                            .build();
     */
    public MessageBuilder getMessage(String key) {
        return new MessageBuilder(key);
    }

    public class MessageBuilder {
        private final String key;
        private Audience audience;
        private MessageType messageType = MessageType.Legacy;
        private Map<String, String> placeholders;

        private MessageBuilder(String key) {
            this.key = key;
        }

        /**
         * Set the target audience (for example, a Player).
         * If no audience is provided, a system (default) message is assumed.
         */
        public MessageBuilder forAudience(Audience audience) {
            this.audience = audience;
            return this;
        }

        /**
         * Specify the deserialization method: Legacy or MiniMessage.
         * Defaults to MessageType.Legacy.
         */
        public MessageBuilder ofType(MessageType messageType) {
            this.messageType = messageType;
            return this;
        }

        /**
         * Set the placeholders to apply to the message.
         */
        public MessageBuilder withPlaceholders(Map<String, String> placeholders) {
            this.placeholders = placeholders;
            return this;
        }

        /**
         * Build and return the configured message component.
         */
        public Component build() {
            String rawMessage;
            // If an audience is provided and is a Player, retrieve the translated message.
            if (audience instanceof Player) {
                rawMessage = getTranslatedMessage(key, (Player) audience);
            } else {
                rawMessage = defaultMessagesResource.getConfig().node((Object[]) key.split("\\."))
                        .getString("&cMessage not found: " + key);
            }
            return parseAndDeserialize(rawMessage, messageType, placeholders);
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
        Language language = VelocityUtils.getPlayerLanguage(targetPlayer);
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
     * Applies placeholders and color parsing to the message and then deserializes
     * it to an Adventure component using the proper method based on the MessageType.
     */
    private Component parseAndDeserialize(String message, MessageType messageType, Map<String, String> placeholders) {
        if (placeholders != null) {
            message = PlaceholderUtils.parsePlaceholders(message, placeholders);
        }
        message = ComponentUtils.serializeLegacyString(message);
        return (messageType == MessageType.MiniMessage)
                ? ComponentUtils.deserializeMMComponent(message)
                : ComponentUtils.deserializeComponent(message);
    }
}
