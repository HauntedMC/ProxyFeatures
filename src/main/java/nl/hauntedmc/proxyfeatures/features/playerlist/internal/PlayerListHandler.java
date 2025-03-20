package nl.hauntedmc.proxyfeatures.features.playerlist.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.JoinConfiguration;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.playerlist.PlayerList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PlayerListHandler {

    private final PlayerList feature;

    public PlayerListHandler(PlayerList feature) {
        this.feature = feature;
    }

    /**
     * Retrieve the players on a given server.
     */
    public Collection<Player> getPlayersOnServer(String serverName) {
        ProxyFeatures plugin = feature.getPlugin();
        return plugin.getProxy().getServer(serverName)
                .map(RegisteredServer::getPlayersConnected)
                .orElseGet(java.util.Collections::emptyList);
    }

    /**
     * Formats a global list of servers using localized messages.
     * All text (including color codes and placeholders) is retrieved via flat keys.
     */
    public Component formatGlobalList(Collection<RegisteredServer> servers, Player audience) {
        int totalPlayers = servers.stream()
                .mapToInt(server -> server.getPlayersConnected().size())
                .sum();

        String currentServerName = audience.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("");

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());

        // Total players message.
        Component totalMessage;
        if (totalPlayers == 0) {
            totalMessage = feature.getLocalizationHandler().getMessage("playerlist.total_players_none", audience);
        } else if (totalPlayers == 1) {
            totalMessage = feature.getLocalizationHandler().getMessage("playerlist.total_players_one", audience);
        } else {
            totalMessage = feature.getLocalizationHandler().getMessage("playerlist.total_players_multiple", audience,
                    Map.of("count", String.valueOf(totalPlayers)));
        }
        lines.add(totalMessage);
        lines.add(Component.empty());

        // Order servers by the number of online players in descending order.
        List<RegisteredServer> sortedServers = servers.stream()
                .sorted((s1, s2) -> Integer.compare(
                        s2.getPlayersConnected().size(),
                        s1.getPlayersConnected().size()))
                .toList();

        // Server list.
        for (RegisteredServer server : sortedServers) {
            String serverName = server.getServerInfo().getName();
            int online = server.getPlayersConnected().size();
            boolean isCurrent = serverName.equals(currentServerName);

            boolean isServerOnline = true;
            try {
                // Ping with a timeout of 50ms.
                server.ping().get(50, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                isServerOnline = false;
            }

            // Use the localized bullet based on online status.
            Component bullet = isServerOnline
                    ? feature.getLocalizationHandler().getMessage("playerlist.server_bullet_online", audience)
                    : feature.getLocalizationHandler().getMessage("playerlist.server_bullet_offline", audience);

            // Localized dash separator.
            Component dash = feature.getLocalizationHandler().getMessage("playerlist.server_dash", audience);

            // Localized online count.
            Component onlineComponent = feature.getLocalizationHandler().getMessage("playerlist.server_online_count", audience,
                    Map.of("online", String.valueOf(online)));

            // Localized server name (different for current vs. other).
            Component nameComponent = isCurrent
                    ? feature.getLocalizationHandler().getMessage("playerlist.server_name_current", audience, Map.of("server", serverName))
                    : feature.getLocalizationHandler().getMessage("playerlist.server_name_other", audience, Map.of("server", serverName));

            // Build clickable buttons.
            TextComponent.Builder buttons = Component.text();
            if (!isCurrent) {
                Component buttonConnect = feature.getLocalizationHandler().getMessage("playerlist.server_connect", audience)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/server " + serverName))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                feature.getLocalizationHandler().getMessage("playerlist.server_connect_hover", audience, Map.of("server", serverName))
                        ));
                buttons.append(buttonConnect);
            }
            Component buttonPlayers = feature.getLocalizationHandler().getMessage("playerlist.server_players", audience)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/list " + serverName))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            feature.getLocalizationHandler().getMessage("playerlist.server_players_hover", audience, Map.of("server", serverName))
                    ));
            buttons.append(buttonPlayers);

            // Combine all components into one line.
            TextComponent.Builder lineBuilder = Component.text()
                    .append(Component.text("  "))
                    .append(bullet).append(Component.text(" "))
                    .append(nameComponent).append(dash)
                    .append(onlineComponent)
                    .append(buttons.build());
            lines.add(lineBuilder.build());
        }

        lines.add(Component.empty());
        // Global tip.
        Component tip = feature.getLocalizationHandler().getMessage("playerlist.global_tip", audience);
        lines.add(tip);
        lines.add(Component.empty());

        return Component.join(JoinConfiguration.separator(Component.newline()), lines);
    }

    /**
     * Formats the player list for a specific server using localized messages.
     */
    public Component formatPlayerList(String serverName, Collection<Player> players, Player audience) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());

        int count = players.size();
        Component countMessage;
        if (count == 0) {
            countMessage = feature.getLocalizationHandler().getMessage("playerlist.server_count_none", audience, Map.of("server", serverName));
        } else if (count == 1) {
            countMessage = feature.getLocalizationHandler().getMessage("playerlist.server_count_one", audience, Map.of("server", serverName));
        } else {
            countMessage = feature.getLocalizationHandler().getMessage("playerlist.server_count_multiple", audience,
                    Map.of("server", serverName, "count", String.valueOf(count)));
        }
        lines.add(countMessage);

        if (!players.isEmpty()) {
            lines.add(Component.empty());
            String playerNames = players.stream()
                    .map(Player::getUsername)
                    .collect(Collectors.joining(", "));
            Component playerListLine = feature.getLocalizationHandler().getMessage("playerlist.server_players_list", audience,
                    Map.of("players", playerNames));
            lines.add(playerListLine);
        }
        lines.add(Component.empty());
        Component tip = feature.getLocalizationHandler().getMessage("playerlist.server_tip_global", audience);
        lines.add(tip);
        lines.add(Component.empty());

        return Component.join(JoinConfiguration.separator(Component.newline()), lines);
    }
}
