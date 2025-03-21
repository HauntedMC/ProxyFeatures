package nl.hauntedmc.proxyfeatures.localization;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.common.resources.ResourceHandler;
import nl.hauntedmc.proxyfeatures.common.util.TextUtils;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.util.EnumMap;
import java.util.Map;

public class LocalizationHandler {

    private final ProxyFeatures plugin;
    private final ResourceHandler defaultMessagesResource;
    private final EnumMap<Language, ResourceHandler> languageResources = new EnumMap<>(Language.class);

    public LocalizationHandler(ProxyFeatures plugin) {
        this.plugin = plugin;
        this.defaultMessagesResource = new ResourceHandler(plugin, "messages.yml");
        loadLanguageFiles();
    }

    /**
     * Loads each language file using the ResourceHandler.
     */
    private void loadLanguageFiles() {
        for (Language lang : Language.values()) {
            ResourceHandler resource = new ResourceHandler(plugin, lang.getFileName());
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

    /**
     * Retrieves a message based on the target audience.
     * If the target is a player, returns a localized version; otherwise, returns the default system message.
     */
    public Component getMessage(String key, Audience target, Map<String, String> placeholders) {
        if (target instanceof Player) {
            return getPlayerMessage(key, (Player) target, placeholders);
        } else {
            return getSystemMessage(key, placeholders);
        }
    }

    public Component getMessage(String key, Audience target) {
        return getMessage(key, target, null);
    }

    private Component getPlayerMessage(String key, Player targetPlayer, Map<String, String> placeholders) {
        String message = getTranslatedMessage(key, targetPlayer);
        if (placeholders != null) {
            message = TextUtils.parsePlaceholders(message, placeholders);
        }
        // If integrating with a placeholder API, apply it here.
        message = TextUtils.parseLegacyColors(message);
        return TextUtils.serializeComponent(message);
    }

    private Component getSystemMessage(String key, Map<String, String> placeholders) {
        String message = defaultMessagesResource.getConfig().node((Object[]) key.split("\\."))
                .getString("&cMessage not found: " + key);
        if (placeholders != null) {
            message = TextUtils.parsePlaceholders(message, placeholders);
        }
        message = TextUtils.parseLegacyColors(message);
        return TextUtils.serializeComponent(message);
    }

    private String getTranslatedMessage(String key, Player targetPlayer) {
        Language language = getPlayerLanguage(targetPlayer);
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

    private boolean isNodeMissing(CommentedConfigurationNode node, String key) {
        return node.node((Object[]) key.split("\\.")).virtual();
    }

    /**
     * Retrieve the player's language.
     * Modify this method as needed to detect the actual language of the player.
     */
    private Language getPlayerLanguage(Player player) {
        return Language.NL;
    }
}
