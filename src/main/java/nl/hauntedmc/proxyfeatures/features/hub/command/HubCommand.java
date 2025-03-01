package nl.hauntedmc.proxyfeatures.features.hub.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.hub.Hub;

import java.util.Optional;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class HubCommand extends FeatureCommand {

    private final Hub feature;

    public HubCommand(Hub feature) {
        this.feature = feature;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player player)) {
            source.sendMessage(feature.getLocalizationHandler().getMessage("general.player_command", source));
            return;
        }
        Optional<RegisteredServer> lobby = feature.getPlugin().getProxy().getServer("lobby");

        if (lobby.isPresent()) {
            if (lobby.get().getPlayersConnected().contains(player)) {
                source.sendMessage(feature.getLocalizationHandler().getMessage("hub.already_connected", player));
                return;
            }
            player.createConnectionRequest(lobby.get()).connect();
        } else {
            player.sendMessage(feature.getLocalizationHandler().getMessage("hub.not_available", player));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.hub.use");
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of(""));
    }

    @Override
    public String getName() {
        return "hub";
    }

}
