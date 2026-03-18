package nl.hauntedmc.proxyfeatures.features.textcommands.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.proxyfeatures.features.textcommands.TextCommands;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TextCommand implements FeatureCommand {

    private final String name;
    private final String messageKey;
    private final Map<String, String> placeholders;
    private final TextCommands feature;

    public TextCommand(TextCommands feature, String name, String messageKey, Map<String, String> placeholders) {
        this.feature = feature;
        this.name = name;
        this.messageKey = messageKey;
        this.placeholders = placeholders;
    }


    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player player)) {
            // Reuse your global message if available; otherwise add your own key to this feature.
            source.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(source).build());
            return;
        }

        Component output = feature.getLocalizationHandler()
                .getMessage(this.messageKey)
                .withPlaceholders(MessagePlaceholders.of(this.placeholders))
                .forAudience(player)
                .build();

        player.sendMessage(output);
    }


    public boolean hasPermission(Invocation invocation) {
        return true;
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }


    public String getName() {
        return this.name;
    }


    public String[] getAliases() {
        return new String[0];
    }
}
