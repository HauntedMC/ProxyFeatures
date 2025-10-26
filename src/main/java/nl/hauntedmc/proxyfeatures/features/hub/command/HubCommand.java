package nl.hauntedmc.proxyfeatures.features.hub.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.hub.Hub;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class HubCommand implements FeatureCommand {

    private static final String LOBBY_NAME = "lobby";
    private final Hub feature;

    public HubCommand(Hub feature) {
        this.feature = feature;
    }


    public String[] getAliases() {
        return new String[]{""};
    }


    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player player)) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.player_command")
                    .forAudience(source)
                    .build());
            return;
        }

        Optional<RegisteredServer> lobbyOptional = feature.getPlugin().getProxy().getServer(LOBBY_NAME);
        if (lobbyOptional.isEmpty()) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("hub.not_available")
                    .with("server", LOBBY_NAME)
                    .forAudience(player)
                    .build());
            return;
        }

        RegisteredServer lobby = lobbyOptional.get();

        if (lobby.getPlayersConnected().contains(player)) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("hub.already_connected")
                    .forAudience(player)
                    .build());
            return;
        }

        lobby.ping().whenComplete((ping, err) -> {
            if (err != null || ping == null) {
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("hub.offline")
                        .with("server", LOBBY_NAME)
                        .forAudience(player)
                        .build());
                return;
            }

            player.createConnectionRequest(lobby).connect().thenAccept(result -> {
                if (result.isSuccessful()) {
                    player.sendMessage(feature.getLocalizationHandler()
                            .getMessage("hub.connection_success")
                            .with("server", LOBBY_NAME)
                            .forAudience(player)
                            .build());
                } else {
                    // Only send a failure message if a reason is provided.
                    result.getReasonComponent().ifPresent(component -> {
                        String reason = LegacyComponentSerializer.legacyAmpersand().serialize(component);
                        player.sendMessage(feature.getLocalizationHandler()
                                .getMessage("hub.connection_failure")
                                .with("server", LOBBY_NAME)
                                .with("reason", reason)
                                .forAudience(player)
                                .build());
                    });
                }
            });
        });
    }


    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.hub.use");
    }

    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }

    public String getName() {
        return "hub";
    }
}
