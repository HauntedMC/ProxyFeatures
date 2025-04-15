package nl.hauntedmc.proxyfeatures.common.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;


import java.util.Arrays;
import java.util.Map;

public class TextUtils {

    private TextUtils(){}

    public static Component deserializeMultilineComponent(String text) {
        return Component.join(JoinConfiguration.separator(Component.newline()),
                Arrays.stream(text.split("<newline>"))
                        .map(line -> LegacyComponentSerializer.legacyAmpersand().deserialize(line))
                        .toList());
    }

    public static Component deserializeComponent(String text) {
        if (text.contains("<newline>")) {
            return deserializeMultilineComponent(text);
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    public static Component deserializeMMComponent(String text) {
        MiniMessage textSerializer = MiniMessage.builder().tags(TagResolver.builder()
                .resolver(StandardTags.color())
                .resolver(StandardTags.decorations())
                .resolver(StandardTags.clickEvent())
                .resolver(StandardTags.hoverEvent())
                .resolver(StandardTags.gradient())
                .resolver(StandardTags.newline())
                .resolver(StandardTags.transition())
                .resolver(StandardTags.shadowColor())
                .build())
                .build();

        return textSerializer.deserialize(text);
    }

    public static String parseLegacyColors(String message) {
        return LegacyComponentSerializer.legacyAmpersand()
                .serialize(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    public static String parsePlaceholders(String message, Map<String, String> placeholders) {
        String output = message;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return output;
    }
}
