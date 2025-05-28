package nl.hauntedmc.proxyfeatures.features.hub.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.hub.Hub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class HubCommand extends FeatureCommand {

    private final Hub feature;

    public HubCommand(Hub feature) {
        this.feature = feature;
    }

    @Override
    public String getAliases() {
        return "";
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player player)) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.player_command")
                    .forAudience(source)
                    .build());
            return;
        }

        // Attempt to retrieve the lobby server by name "lobby".
        Optional<RegisteredServer> lobbyOptional = feature.getPlugin().getProxy().getServer("lobby");
        if (lobbyOptional.isEmpty()) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("hub.not_available")
                    .withPlaceholders(Map.of("server", "lobby"))
                    .forAudience(player)
                    .build());
            return;
        }

        RegisteredServer lobby = lobbyOptional.get();
        // Check if the player is already connected to the lobby.
        if (lobby.getPlayersConnected().contains(player)) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("hub.already_connected")
                    .forAudience(player)
                    .build());
            return;
        }

        // Initiate connection asynchronously with proper feedback.
        player.createConnectionRequest(lobby).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("hub.connection_success")
                        .withPlaceholders(Map.of("server", "lobby"))
                        .forAudience(player)
                        .build());
            } else {
                String reason;
                if (result.getReasonComponent().isPresent()) {
                    reason = LegacyComponentSerializer.legacyAmpersand().serialize(
                            result.getReasonComponent().get());
                } else {
                    Component unknown = feature.getLocalizationHandler()
                            .getMessage("hub.unknown_failure_reason")
                            .build();
                    reason = LegacyComponentSerializer.legacyAmpersand().serialize(unknown);
                }
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("hub.connection_failure")
                        .withPlaceholders(Map.of("server", "lobby", "reason", reason))
                        .forAudience(player)
                        .build());
            }
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.hub.use");
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public String getName() {
        return "hub";
    }
}
