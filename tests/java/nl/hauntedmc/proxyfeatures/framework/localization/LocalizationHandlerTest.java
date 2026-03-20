package nl.hauntedmc.proxyfeatures.framework.localization;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.APIRegistry;
import nl.hauntedmc.proxyfeatures.api.io.localization.Language;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalizationHandlerTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @TempDir
    Path tempDir;

    @AfterEach
    void clearApis() {
        APIRegistry.clear();
    }

    @Test
    void reloadAndDefaultRegistrationWorkWithMissingLanguageFile() throws IOException {
        writeLangFile("messages.yml", "general:\n  usage: \"&aUse /proxyfeatures\"\n");
        writeLangFile("messages_EN.yml", "general:\n  usage: \"&bUse /proxyfeatures\"\n");
        writeLangFile("messages_NL.yml", "general:\n  usage: \"&cGebruik /proxyfeatures\"\n");
        // Intentionally malformed YAML to force a load failure branch for this language.
        writeLangFile("messages_DE.yml", "general:\n  usage: [broken");

        LocalizationHandler handler = new LocalizationHandler(mockPlugin());
        assertDoesNotThrow(handler::reloadLocalization);

        MessageMap defaults = new MessageMap();
        defaults.add("general.usage", "&aUse /proxyfeatures");
        defaults.add("general.extra", "&7Extra");
        handler.registerDefaultMessages(defaults);
        handler.registerDefaultMessages(defaults);

        String file = Files.readString(tempDir.resolve("lang/messages.yml"));
        assertTrue(file.contains("general"));
        assertTrue(file.contains("extra"));
    }

    @Test
    void messageBuilderUsesPlayerTranslationAndFallbacks() throws IOException {
        writeLangFile("messages.yml", """
                general:
                  usage: "&aDefault usage"
                greeting:
                  text: "Hello {name}"
                """);
        writeLangFile("messages_EN.yml", """
                general:
                  usage: "&bEnglish usage"
                greeting:
                  text: "Welcome {name}"
                """);
        writeLangFile("messages_NL.yml", """
                general:
                  usage: "&cNederlandse usage"
                """);
        writeLangFile("messages_DE.yml", """
                general:
                  usage: "&6Deutsche usage"
                """);

        nl.hauntedmc.proxyfeatures.features.playerlanguage.api.LanguageAPI languageApi =
                mock(nl.hauntedmc.proxyfeatures.features.playerlanguage.api.LanguageAPI.class);
        UUID uuid = UUID.randomUUID();
        when(languageApi.get(uuid)).thenReturn(Language.EN);
        APIRegistry.register(nl.hauntedmc.proxyfeatures.features.playerlanguage.api.LanguageAPI.class, languageApi);

        LocalizationHandler handler = new LocalizationHandler(mockPlugin());
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);

        Component translated = handler.getMessage("general.usage")
                .forAudience(player)
                .build();
        assertTrue(PLAIN.serialize(translated).contains("English usage"));

        Component missingForPlayer = handler.getMessage("missing.key")
                .forAudience(player)
                .build();
        assertTrue(PLAIN.serialize(missingForPlayer).contains("Message not found"));

        when(languageApi.get(uuid)).thenReturn(null);
        Component fallback = handler.getMessage("general.usage")
                .forAudience(player)
                .build();
        assertTrue(PLAIN.serialize(fallback).contains("Nederlandse usage"));

        Audience nonPlayer = mock(Audience.class);
        Component nonPlayerMsg = handler.getMessage("general.usage")
                .forAudience(nonPlayer)
                .build();
        assertTrue(PLAIN.serialize(nonPlayerMsg).contains("Default usage"));
    }

    @Test
    void placeholderAndAutoLinkRenderingPathsAreExercised() throws IOException {
        writeLangFile("messages.yml", """
                greeting:
                  text: "Hi {name}, total={count}, link=https://example.com and {component}"
                """);
        writeLangFile("messages_EN.yml", "greeting:\n  text: \"Hi {name}\"\n");
        writeLangFile("messages_NL.yml", "greeting:\n  text: \"Hoi {name}\"\n");
        writeLangFile("messages_DE.yml", "greeting:\n  text: \"Hallo {name}\"\n");

        LocalizationHandler handler = new LocalizationHandler(mockPlugin());
        Component rendered = handler.getMessage("greeting.text")
                .withPlaceholders(null)
                .with("name", "Remy")
                .with("count", 7)
                .with("component", Component.text("COMP"))
                .autoLinkUrls(true)
                .autoLinkUnderline(false)
                .build();

        String plain = PLAIN.serialize(rendered);
        assertTrue(plain.contains("Remy"));
        assertTrue(plain.contains("7"));
        assertTrue(plain.contains("COMP"));

        Component missing = handler.getMessage("missing.key").build();
        assertTrue(PLAIN.serialize(missing).contains("Message not found"));
    }

    private ProxyFeatures mockPlugin() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        when(plugin.getDataDirectory()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(mock(ComponentLogger.class));
        return plugin;
    }

    private void writeLangFile(String fileName, String content) throws IOException {
        Path langDir = tempDir.resolve("lang");
        Files.createDirectories(langDir);
        Files.writeString(langDir.resolve(fileName), content);
    }
}
