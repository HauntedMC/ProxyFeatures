package nl.hauntedmc.proxyfeatures.features.playerlist.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.api.util.type.CastUtils;
import nl.hauntedmc.proxyfeatures.features.playerlist.PlayerList;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ListCommand implements FeatureCommand {

    private final PlayerList feature;
    private final List<String> blacklist;

    public ListCommand(PlayerList feature) {
        this.feature = feature;
        this.blacklist = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("blacklist"), String.class);
    }

    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player player)) {
            source.sendMessage(feature.getLocalizationHandler().getMessage("playerlist.command_only_players").forAudience(source).build());
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            // No arguments: list players on the current server.
            Optional<ServerConnection> currentServerOpt = player.getCurrentServer();
            if (currentServerOpt.isEmpty()) {
                player.sendMessage(feature.getLocalizationHandler().getMessage("playerlist.not_on_server").forAudience(player).build());
                return;
            }
            String serverName = currentServerOpt.get().getServerInfo().getName();

            var players = feature.getPlayerListHandler().getPlayersOnServer(serverName);
            Component message = feature.getPlayerListHandler().formatPlayerList(serverName, players, player);
            player.sendMessage(message);
        } else if (args.length == 1) {
            String targetServer = args[0];
            Optional<RegisteredServer> serverOpt = feature.getPlugin().getProxy().getServer(targetServer);
            if (serverOpt.isEmpty() || blacklist.contains(targetServer)) {
                player.sendMessage(feature.getLocalizationHandler().getMessage("playerlist.server_not_found").forAudience(player).build()
                        .replaceText(builder -> builder.matchLiteral("{server}").replacement(targetServer)));
                return;
            }

            var players = feature.getPlayerListHandler().getPlayersOnServer(targetServer);
            Component message = feature.getPlayerListHandler().formatPlayerList(targetServer, players, player);

            player.sendMessage(message);
        } else {
            player.sendMessage(feature.getLocalizationHandler().getMessage("playerlist.usage").forAudience(player).build());
        }
    }

    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.playerlist.command.list");
    }

    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();

        // If no argument is provided (or an empty string), return the full list.
        if (args.length == 0 || args[0].isEmpty()) {
            List<String> suggestions = feature.getPlugin().getProxy().getAllServers().stream()
                    .map(server -> server.getServerInfo().getName())
                    .filter(name -> !blacklist.contains(name))
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(suggestions);
        }

        // Otherwise, filter based on the partial input.
        String partial = args[0].toLowerCase();
        List<String> suggestions = feature.getPlugin().getProxy().getAllServers().stream()
                .map(server -> server.getServerInfo().getName())
                .filter(name -> !blacklist.contains(name))
                .filter(name -> name.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        return CompletableFuture.completedFuture(suggestions);
    }

    public String getName() {
        return "list";
    }

    public String[] getAliases() {
        return new String[]{""};
    }
}
