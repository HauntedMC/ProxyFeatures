package nl.hauntedmc.proxyfeatures.features.clientinfo.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.clientinfo.ClientInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ClientInfoCommand implements FeatureCommand {
    private final ClientInfo feature;

    public ClientInfoCommand(ClientInfo feature) {
        this.feature = feature;
    }

    public String getName() {
        return "clientinfo";
    }

    public String[] getAliases() {
        return new String[]{""};
    }

    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.clientinfo.command");
    }

    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        List<String> args = List.of(invocation.arguments());

        if (args.size() != 1) {
            source.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("clientinfo.cmd_usage")
                            .forAudience(source)
                            .build()
            );
            return;
        }

        String targetName = args.getFirst();
        Optional<Player> optPlayer = feature.getPlugin().getProxy().getPlayer(targetName);
        if (optPlayer.isEmpty()) {
            source.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("clientinfo.cmd_playerNotFound")
                            .withPlaceholders(Map.of("player", targetName))
                            .forAudience(source)
                            .build()
            );
            return;
        }

        Player target = optPlayer.get();
        PlayerSettings settings = target.getPlayerSettings();
        ProtocolVersion proto = target.getProtocolVersion();
        String clientVersion = (proto == null)
                ? "Unknown"
                : proto + " (protocol " + proto.getProtocol() + ")";

        // Header
        source.sendMessage(
                feature.getLocalizationHandler()
                        .getMessage("clientinfo.cmd_header")
                        .withPlaceholders(Map.of("player", target.getUsername()))
                        .forAudience(source)
                        .build()
        );

        // Settings entries
        source.sendMessage(
                feature.getLocalizationHandler()
                        .getMessage("clientinfo.cmd_entry")
                        .withPlaceholders(Map.of(
                                "setting", "Render Distance",
                                "value", String.valueOf(settings.getViewDistance())
                        ))
                        .forAudience(source)
                        .build()
        );
        source.sendMessage(
                feature.getLocalizationHandler()
                        .getMessage("clientinfo.cmd_entry")
                        .withPlaceholders(Map.of(
                                "setting", "Chat Mode",
                                "value", settings.getChatMode().name()
                        ))
                        .forAudience(source)
                        .build()
        );
        source.sendMessage(
                feature.getLocalizationHandler()
                        .getMessage("clientinfo.cmd_entry")
                        .withPlaceholders(Map.of(
                                "setting", "Particles",
                                "value", settings.getParticleStatus().name()
                        ))
                        .forAudience(source)
                        .build()
        );
        source.sendMessage(
                feature.getLocalizationHandler()
                        .getMessage("clientinfo.cmd_entry")
                        .withPlaceholders(Map.of(
                                "setting", "Language",
                                "value", settings.getLocale().getDisplayCountry()
                        ))
                        .forAudience(source)
                        .build()
        );
        source.sendMessage(
                feature.getLocalizationHandler()
                        .getMessage("clientinfo.cmd_entry")
                        .withPlaceholders(Map.of(
                                "setting", "Client Version",
                                "value", clientVersion
                        ))
                        .forAudience(source)
                        .build()
        );
    }

    
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();

        // No arg yet (or they've just typed a space): show every online player
        if (args.length == 0 || args[0].isEmpty()) {
            List<String> allNames = feature.getPlugin()
                    .getProxy()
                    .getAllPlayers()
                    .stream()
                    .map(Player::getUsername)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(allNames);
        }

        // Otherwise, filter by what they've started typing
        String partial = args[0].toLowerCase();
        List<String> matching = feature.getPlugin()
                .getProxy()
                .getAllPlayers()
                .stream()
                .map(Player::getUsername)
                .filter(name -> name.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        return CompletableFuture.completedFuture(matching);
    }

}
