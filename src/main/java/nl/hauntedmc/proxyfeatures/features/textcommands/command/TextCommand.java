package nl.hauntedmc.proxyfeatures.features.textcommands.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.commonlib.util.ComponentUtils;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.textcommands.TextCommands;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TextCommand extends FeatureCommand {

    private final String name;
    private final String rawOutput;
    private final TextCommands feature;

    public TextCommand(TextCommands feature, String name, String rawOutput) {
        this.feature = feature;
        this.name = name;
        this.rawOutput = rawOutput;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player player)) {
            source.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command").forAudience(source).build());
            return;
        }
        
        Component output = ComponentUtils.deserializeMMComponent(this.rawOutput);
        player.sendMessage(output);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of(""));
    }

    @Override
    public String getName() {
        return this.name;
    }
}
