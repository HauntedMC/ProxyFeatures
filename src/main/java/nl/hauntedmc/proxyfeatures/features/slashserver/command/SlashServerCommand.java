package nl.hauntedmc.proxyfeatures.features.slashserver.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.slashserver.SlashServer;

import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SlashServerCommand extends FeatureCommand {

    private final SlashServer feature;
    private final String serverName;

    public SlashServerCommand(SlashServer feature, String serverName) {
        this.feature = feature;
        this.serverName = serverName;
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

        Optional<RegisteredServer> optionalServer = feature.getPlugin().getProxy().getServer(serverName);
        if (optionalServer.isEmpty()) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("slash.not_available")
                    .withPlaceholders(Map.of("server", serverName))
                    .forAudience(player)
                    .build());
            return;
        }

        RegisteredServer targetServer = optionalServer.get();
        // Check if the player is already connected to this server
        if (targetServer.getPlayersConnected().contains(player)) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("slash.already_connected")
                    .forAudience(player)
                    .build());
            return;
        }

        // Initiate server connection asynchronously and send feedback using localization keys
        player.createConnectionRequest(targetServer).connect().thenAccept(result -> {
            if (result.isSuccessful()) {
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("slash.connection_success")
                        .withPlaceholders(Map.of("server", serverName))
                        .forAudience(player)
                        .build());
            } else {
                String reason;
                if (result.getReasonComponent().isPresent()) {
                    reason = LegacyComponentSerializer.legacyAmpersand().serialize(result.getReasonComponent().get());
                } else {
                    Component unknownReason = feature.getLocalizationHandler().getMessage("slash.unknown_failure_reason").build();
                    reason = LegacyComponentSerializer.legacyAmpersand().serialize(unknownReason);
                }
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("slash.connection_failure")
                        .withPlaceholders(Map.of("server", serverName, "reason", reason))
                        .forAudience(player)
                        .build());
            }
        });
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.slashserver.use");
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public String getName() {
        return serverName;
    }
}
