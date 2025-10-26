package nl.hauntedmc.proxyfeatures.features.slashserver.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.slashserver.SlashServer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class SlashServerCommand implements FeatureCommand {

    private final SlashServer feature;
    private final String serverName;

    public SlashServerCommand(SlashServer feature, String serverName) {
        this.feature = feature;
        this.serverName = serverName;
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

        Optional<RegisteredServer> optionalServer = feature.getPlugin().getProxy().getServer(serverName);
        if (optionalServer.isEmpty()) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("slash.not_available")
                    .with("server", serverName)
                    .forAudience(player)
                    .build());
            return;
        }

        RegisteredServer targetServer = optionalServer.get();

        if (targetServer.getPlayersConnected().contains(player)) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("slash.already_connected")
                    .forAudience(player)
                    .build());
            return;
        }

        targetServer.ping().whenComplete((ping, err) -> {
            if (err != null || ping == null) {
                player.sendMessage(feature.getLocalizationHandler()
                        .getMessage("slash.offline")
                        .with("server", serverName)
                        .forAudience(player)
                        .build());
                return;
            }

            player.createConnectionRequest(targetServer).connect().thenAccept(result -> {
                if (result.isSuccessful()) {
                    player.sendMessage(feature.getLocalizationHandler()
                            .getMessage("slash.connection_success")
                            .with("server", serverName)
                            .forAudience(player)
                            .build());
                } else {
                    // Only send a failure message if the proxy provided a reason.
                    result.getReasonComponent().ifPresent(component -> {
                        String reason = LegacyComponentSerializer.legacyAmpersand().serialize(component);
                        player.sendMessage(feature.getLocalizationHandler()
                                .getMessage("slash.connection_failure")
                                .with("server", serverName)
                                .with("reason", reason)
                                .forAudience(player)
                                .build());
                    });
                }
            });
        });
    }


    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.slashserver.use");
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }


    public String getName() {
        return serverName;
    }
}
