package nl.hauntedmc.proxyfeatures.features.playerlist.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import de.myzelyam.api.vanish.VelocityVanishAPI;
import net.kyori.adventure.text.JoinConfiguration;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.common.util.PlayerUtils;
import nl.hauntedmc.proxyfeatures.features.playerlist.PlayerList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.*;
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
                .orElseGet(Collections::emptyList);
    }

    /**
     * Formats a global list of servers using localized messages.
     * Excludes vanished players from both the global count and per-server count.
     */
    public Component formatGlobalList(Collection<RegisteredServer> servers, Player audience) {
        // Get all players from the proxy and filter out vanished ones.
        List<Player> allPlayers = feature.getPlugin().getProxy().getAllPlayers().stream()
                .filter(player -> !PlayerUtils.isVanished(player))
                .toList();
        int totalPlayers = allPlayers.size();

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

        // Order servers by the number of non-vanished online players in descending order.
        List<RegisteredServer> sortedServers = servers.stream()
                .sorted((s1, s2) -> {
                    int count1 = (int) s1.getPlayersConnected().stream().filter(p -> !PlayerUtils.isVanished(p)).count();
                    int count2 = (int) s2.getPlayersConnected().stream().filter(p -> !PlayerUtils.isVanished(p)).count();
                    return Integer.compare(count2, count1);
                })
                .toList();

        // Server list.
        for (RegisteredServer server : sortedServers) {
            String serverName = server.getServerInfo().getName();
            // Only count non-vanished players.
            List<Player> onlinePlayers = server.getPlayersConnected().stream()
                    .filter(player -> !PlayerUtils.isVanished(player))
                    .toList();
            int online = onlinePlayers.size();
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
     * Excludes vanished players from the count and lists.
     */
    public Component formatPlayerList(String serverName, Collection<Player> players, Player audience) {
        // Exclude vanished players.
        List<Player> visiblePlayers = players.stream()
                .filter(player -> !PlayerUtils.isVanished(player))
                .toList();

        // Separate players into staff and non-staff groups, sorted alphabetically by username.
        List<Player> staffPlayers = visiblePlayers.stream()
                .filter(player -> player.hasPermission("proxyfeatures.feature.playerlist.staff"))
                .sorted(Comparator.comparing(Player::getUsername, String::compareToIgnoreCase))
                .toList();

        List<Player> nonStaffPlayers = visiblePlayers.stream()
                .filter(player -> !player.hasPermission("proxyfeatures.feature.playerlist.staff"))
                .sorted(Comparator.comparing(Player::getUsername, String::compareToIgnoreCase))
                .toList();

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());

        int playerCount = visiblePlayers.size();
        Component playerCountMessage;
        if (playerCount == 0) {
            playerCountMessage = feature.getLocalizationHandler().getMessage("playerlist.server_count_none", audience, Map.of("server", serverName));
        } else if (playerCount == 1) {
            playerCountMessage = feature.getLocalizationHandler().getMessage("playerlist.server_count_one", audience, Map.of("server", serverName));
        } else {
            playerCountMessage = feature.getLocalizationHandler().getMessage("playerlist.server_count_multiple", audience,
                    Map.of("server", serverName, "count", String.valueOf(playerCount)));
        }
        lines.add(playerCountMessage);

        if (!nonStaffPlayers.isEmpty()) {
            lines.add(Component.empty());
            String playerNames = nonStaffPlayers.stream()
                    .map(Player::getUsername)
                    .collect(Collectors.joining(", "));
            Component playerListLine = feature.getLocalizationHandler().getMessage("playerlist.server_players_list", audience,
                    Map.of("players", playerNames));
            lines.add(playerListLine);
        }

        // Format staff players if the list is not empty.
        if (!staffPlayers.isEmpty()) {
            lines.add(Component.empty());
            String staffNames = staffPlayers.stream()
                    .map(Player::getUsername)
                    .collect(Collectors.joining(", "));
            Component staffListLine = feature.getLocalizationHandler().getMessage("playerlist.server_staff_list", audience,
                    Map.of("players", staffNames));
            lines.add(staffListLine);
        }

        lines.add(Component.empty());
        Component tip = feature.getLocalizationHandler().getMessage("playerlist.server_tip_global", audience);
        lines.add(tip);
        lines.add(Component.empty());

        return Component.join(JoinConfiguration.separator(Component.newline()), lines);
    }
}
