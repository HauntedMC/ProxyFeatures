package nl.hauntedmc.proxyfeatures.localization;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.localization.Language;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.api.io.resource.ResourceHandler;
import nl.hauntedmc.proxyfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.proxyfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.proxyfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.proxyfeatures.common.util.LanguageUtils;
import org.jetbrains.annotations.NotNull;
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

    // Load each language file using ResourceHandler (Configurate backed)
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

    /** Reload defaults and per-language files. */
    public void reloadLocalization() {
        defaultMessagesResource.reload();
        languageResources.values().forEach(ResourceHandler::reload);
        plugin.getLogger().info("All localization files reloaded.");
    }

    /** Register defaults (create missing keys only). */
    public void registerDefaultMessages(MessageMap messageMap) {
        boolean changes = false;
        CommentedConfigurationNode root = defaultMessagesResource.getConfig();
        for (Map.Entry<String, String> e : messageMap.getMessages().entrySet()) {
            String key = e.getKey();
            if (isMissing(root, key)) {
                try {
                    node(root, key).set(e.getValue());
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to set default message for key: " + key, ex);
                }
                changes = true;
            }
        }
        if (changes) defaultMessagesResource.save();
    }

    // --- Fluent Builder API (mirrors the Bukkit version) ---

    public MessageBuilder getMessage(String key) {
        return new MessageBuilder(key);
    }

    public class MessageBuilder {
        private final String key;
        private Audience audience;
        private MessagePlaceholders placeholders = MessagePlaceholders.empty();

        private boolean autoLinkUrls = false;
        private boolean autoLinkUnderline = true;

        private MessageBuilder(String key) {
            this.key = key;
        }

        public MessageBuilder forAudience(Audience audience) {
            this.audience = audience;
            return this;
        }

        public MessageBuilder withPlaceholders(MessagePlaceholders placeholders) {
            if (placeholders != null) this.placeholders = placeholders;
            return this;
        }

        public MessageBuilder with(String k, String v) {
            this.placeholders = MessagePlaceholders.builder()
                    .addAll(this.placeholders).addString(k, v).build();
            return this;
        }

        public MessageBuilder with(String k, Number v) {
            this.placeholders = MessagePlaceholders.builder()
                    .addAll(this.placeholders).addNumber(k, v).build();
            return this;
        }

        public MessageBuilder with(String k, Component v) {
            this.placeholders = MessagePlaceholders.builder()
                    .addAll(this.placeholders).addComponent(k, v).build();
            return this;
        }

        public MessageBuilder autoLinkUrls(boolean on) {
            this.autoLinkUrls = on;
            return this;
        }

        public MessageBuilder autoLinkUnderline(boolean on) {
            this.autoLinkUnderline = on;
            return this;
        }

        /** Build: legacy(&/§ + hex) -> MiniMessage tags -> MiniMessage parse -> Component. */
        public Component build() {
            String raw = (audience instanceof Player p)
                    ? getTranslatedMessage(key, p)
                    : node(defaultMessagesResource.getConfig(), key).getString("&cMessage not found: " + key);
            return render(raw);
        }

        private Component render(String s) {
            s = TextFormatter.convert(s)
                    .expect(TextFormatter.InputFormat.MIXED_INPUT)
                    .preprocess(str -> MessagePlaceholders.applyPlaceholders(str, placeholders))
                    .toMiniMessage();

            ComponentFormatter.Converter conv = ComponentFormatter.deserialize(s)
                    .expect(TextFormatter.InputFormat.MINIMESSAGE)
                    .features(ComponentFormatter.ALL_DEFAULTS());

            if (autoLinkUrls) conv.autoLinkUrls(autoLinkUnderline);
            return conv.toComponent();
        }
    }

    // --- Helpers ---

    private static CommentedConfigurationNode node(CommentedConfigurationNode root, String dottedKey) {
        return root.node((Object[]) dottedKey.split("\\."));
    }

    private static boolean isMissing(CommentedConfigurationNode root, String dottedKey) {
        return node(root, dottedKey).virtual();
    }

    /** Get translated message for a Velocity player; fallback to defaults. */
    private @NotNull String getTranslatedMessage(String key, Player player) {
        Language language = LanguageUtils.getPlayerLanguage(player);
        String msg = null;
        if (language != null) {
            ResourceHandler res = languageResources.get(language);
            if (res != null && !isMissing(res.getConfig(), key)) {
                msg = node(res.getConfig(), key).getString();
            }
        }
        if (msg == null) {
            msg = node(defaultMessagesResource.getConfig(), key)
                    .getString("&cMessage not found: " + key);
        }
        return msg;
    }
}
