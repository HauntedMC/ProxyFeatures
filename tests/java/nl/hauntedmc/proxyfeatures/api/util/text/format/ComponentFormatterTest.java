package nl.hauntedmc.proxyfeatures.api.util.text.format;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import nl.hauntedmc.proxyfeatures.api.util.text.format.inspect.FormatInspector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ComponentFormatterTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void allDefaultsContainsCommonFeatures() {
        Set<ComponentFormatter.Feature> defaults = ComponentFormatter.ALL_DEFAULTS();
        assertTrue(defaults.contains(ComponentFormatter.Feature.COLORS));
        assertTrue(defaults.contains(ComponentFormatter.Feature.CLICK));
        assertTrue(defaults.contains(ComponentFormatter.Feature.NEWLINE));
    }

    @Test
    void deserializeSanitizesDisallowedTagsAndCanAutoLinkUrls() {
        Component sanitized = ComponentFormatter.deserialize("<click:open_url:https://x>visit</click>")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .features(ComponentFormatter.Feature.COLORS)
                .toComponent();
        assertEquals("visit", PLAIN.serialize(sanitized));
        assertFalse(FormatInspector.hasFormatting(sanitized, Set.of(FormatInspector.ComponentAspect.EVENTS)));

        Component linked = ComponentFormatter.deserialize("visit https://example.com")
                .expect(TextFormatter.InputFormat.PLAIN)
                .features(ComponentFormatter.Feature.CLICK, ComponentFormatter.Feature.DECORATIONS)
                .autoLinkUrls(false)
                .toComponent();
        assertTrue(PLAIN.serialize(linked).contains("https://example.com"));
        assertTrue(FormatInspector.hasFormatting(linked, Set.of(FormatInspector.ComponentAspect.EVENTS)));
    }

    @Test
    void deserializeSupportsCustomTagsAndStrictParsing() {
        TagResolver resolver = TagResolver.resolver("x", Tag.inserting(Component.text("OK")));
        Component custom = ComponentFormatter.deserialize("<x>")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .features(Set.of())
                .withCustomTag("x", resolver)
                .toComponent();
        assertEquals("OK", PLAIN.serialize(custom));

        assertThrows(Exception.class, () -> ComponentFormatter.deserialize("<bold><italic>broken</bold>")
                .expect(TextFormatter.InputFormat.MINIMESSAGE)
                .features(Set.of(ComponentFormatter.Feature.DECORATIONS))
                .sanitizeUnknownTags(false)
                .strict(true)
                .toComponent());
    }

    @Test
    void serializerBuilderSupportsAllOutputFormatsAndOptions() {
        Component source = Component.text("Hello", NamedTextColor.GREEN)
                .append(Component.text(" link").clickEvent(ClickEvent.openUrl("https://example.com")));

        String defaultMini = ComponentFormatter.serialize(source).build();
        assertTrue(defaultMini.contains("Hello"));

        String plain = ComponentFormatter.serialize(source)
                .format(ComponentFormatter.Serializer.Format.PLAIN)
                .build();
        assertEquals("Hello link", plain);

        String legacyAmp = ComponentFormatter.serialize(source)
                .format(ComponentFormatter.Serializer.Format.LEGACY_AMPERSAND)
                .build();
        assertTrue(legacyAmp.contains("&a"));

        ComponentFormatter.Serializer.LegacyOptions section = ComponentFormatter.Serializer.LegacyOptions.section();
        String legacySec = ComponentFormatter.serialize(source)
                .format(ComponentFormatter.Serializer.Format.LEGACY_SECTION)
                .legacyOptions(section)
                .build();
        assertTrue(legacySec.contains("§a"));

        String json = ComponentFormatter.serialize(source)
                .format(ComponentFormatter.Serializer.Format.JSON)
                .jsonPretty(true)
                .build();
        assertTrue(json.contains("Hello"));

        String downsampled = ComponentFormatter.serialize(source)
                .format(ComponentFormatter.Serializer.Format.JSON_DOWNSAMPLED)
                .jsonPretty(false)
                .build();
        assertTrue(downsampled.contains("Hello"));
    }

    @Test
    void converterAndSerializerInternalBranchesAreCovered() throws Exception {
        Component fromNull = ComponentFormatter.deserialize(null)
                .expect(Set.of(TextFormatter.InputFormat.PLAIN))
                .preprocess(s -> s + "x")
                .toComponent();
        assertEquals("x", PLAIN.serialize(fromNull));

        Component autoLinked = ComponentFormatter.deserialize("www.example.com")
                .features(ComponentFormatter.Feature.CLICK, ComponentFormatter.Feature.DECORATIONS)
                .autoLinkUrls()
                .toComponent();
        assertTrue(FormatInspector.hasFormatting(autoLinked, Set.of(FormatInspector.ComponentAspect.EVENTS)));

        String removedHex = invokeStripDisallowed("<#A1B2C3>Hello", Set.of(), null);
        assertEquals("Hello", removedHex);
        assertEquals("", invokeStripDisallowed("", Set.of(), Set.of("custom")));
        assertEquals("", invokeStripDisallowed("</bold>", Set.of(), null));

        assertNull(invokeAutoLinkString(null, true));
        assertEquals("", invokeAutoLinkString("", true));
        String linkedString = invokeAutoLinkString("visit www.example.com", true);
        assertTrue(linkedString.contains("<click:open_url:https://www.example.com>"));
        assertTrue(linkedString.contains("<underlined>www.example.com</underlined>"));

        Component linkedComponent = invokeAutoLinkComponent(Component.text("www.example.com"), true);
        assertNotNull(linkedComponent.clickEvent());
        assertEquals(TextDecoration.State.TRUE, linkedComponent.decoration(TextDecoration.UNDERLINED));
        assertEquals("https://www.example.com", linkedComponent.clickEvent().value());
        assertEquals(Component.empty(), invokeAutoLinkComponent(null, false));

        assertNotNull(ComponentFormatter.Serializer.LegacyOptions.ampersand());
        assertNotNull(invokeBuildLegacy(null));
        String ampWithCustomLegacy = ComponentFormatter.serialize(Component.text("x", NamedTextColor.GREEN))
                .format(ComponentFormatter.Serializer.Format.LEGACY_AMPERSAND)
                .legacyOptions(ComponentFormatter.Serializer.LegacyOptions.section())
                .build();
        assertTrue(ampWithCustomLegacy.contains("§a"));

        String secDefault = ComponentFormatter.serialize(Component.text("x", NamedTextColor.GREEN))
                .format(ComponentFormatter.Serializer.Format.LEGACY_SECTION)
                .build();
        assertTrue(secDefault.contains("§a"));

        String compactJson = ComponentFormatter.serialize(Component.text("x", NamedTextColor.GREEN))
                .format(ComponentFormatter.Serializer.Format.JSON)
                .jsonPretty(false)
                .build();
        assertTrue(compactJson.contains("x"));

        String prettyDownsampled = ComponentFormatter.serialize(Component.text("x", NamedTextColor.GREEN))
                .format(ComponentFormatter.Serializer.Format.JSON_DOWNSAMPLED)
                .jsonPretty(true)
                .build();
        assertTrue(prettyDownsampled.contains("\n"));
    }

    @SuppressWarnings("unchecked")
    private static String invokeStripDisallowed(String mm, Set<ComponentFormatter.Feature> features, Set<String> allowedCustom) throws Exception {
        Class<?> sanitizer = Class.forName("nl.hauntedmc.proxyfeatures.api.util.text.format.ComponentFormatter$Sanitizer");
        Method method = sanitizer.getDeclaredMethod("stripDisallowed", String.class, Set.class, Set.class);
        method.setAccessible(true);
        return (String) method.invoke(null, mm, features, allowedCustom);
    }

    private static String invokeAutoLinkString(String mm, boolean underline) throws Exception {
        Class<?> linker = Class.forName("nl.hauntedmc.proxyfeatures.api.util.text.format.ComponentFormatter$AutoLinker");
        Method method = linker.getDeclaredMethod("autoLink", String.class, boolean.class);
        method.setAccessible(true);
        return (String) method.invoke(null, mm, underline);
    }

    private static Component invokeAutoLinkComponent(Component root, boolean underline) throws Exception {
        Class<?> linker = Class.forName("nl.hauntedmc.proxyfeatures.api.util.text.format.ComponentFormatter$AutoLinker");
        Method method = linker.getDeclaredMethod("autoLink", Component.class, boolean.class);
        method.setAccessible(true);
        return (Component) method.invoke(null, root, underline);
    }

    private static Object invokeBuildLegacy(ComponentFormatter.Serializer.LegacyOptions options) throws Exception {
        Class<?> serializer = Class.forName("nl.hauntedmc.proxyfeatures.api.util.text.format.ComponentFormatter$Serializer");
        Method method = serializer.getDeclaredMethod("buildLegacy", ComponentFormatter.Serializer.LegacyOptions.class);
        method.setAccessible(true);
        return method.invoke(null, options);
    }
}
