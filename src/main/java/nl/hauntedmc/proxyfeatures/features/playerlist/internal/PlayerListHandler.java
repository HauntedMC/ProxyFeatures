package nl.hauntedmc.proxyfeatures.features.playerlist.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.JoinConfiguration;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.common.util.APIRegistry;
import nl.hauntedmc.proxyfeatures.features.playerlist.PlayerList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.VanishAPI;

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
                .filter(player -> !APIRegistry.get(VanishAPI.class)
                        .map(api -> api.isVanished(player.getUniqueId()))
                        .orElse(false))
                .toList();
        int totalPlayers = allPlayers.size();

        String currentServerName = audience.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("");

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());

        Component totalMessage;
        if (totalPlayers == 0) {
            totalMessage = feature.getLocalizationHandler().getMessage("playerlist.total_players_none").forAudience(audience).build();
        } else if (totalPlayers == 1) {
            totalMessage = feature.getLocalizationHandler().getMessage("playerlist.total_players_one").forAudience(audience).build();
        } else {
            totalMessage = feature.getLocalizationHandler().getMessage("playerlist.total_players_multiple").forAudience(audience).with("count", totalPlayers).build();
        }
        lines.add(totalMessage);
        lines.add(Component.empty());

        // Order servers by the number of non-vanished online players in descending order.
        List<RegisteredServer> sortedServers = servers.stream()
                .sorted((s1, s2) -> {
                    int count1 = (int) s1.getPlayersConnected().stream().filter(p -> !APIRegistry.get(VanishAPI.class)
                            .map(api -> api.isVanished(p.getUniqueId()))
                            .orElse(false)).count();
                    int count2 = (int) s2.getPlayersConnected().stream().filter(p -> !APIRegistry.get(VanishAPI.class)
                            .map(api -> api.isVanished(p.getUniqueId()))
                            .orElse(false)).count();
                    return Integer.compare(count2, count1);
                })
                .toList();

        for (RegisteredServer server : sortedServers) {
            String serverName = server.getServerInfo().getName();
            // Only count non-vanished players.
            List<Player> onlinePlayers = server.getPlayersConnected().stream()
                    .filter(player -> !APIRegistry.get(VanishAPI.class)
                            .map(api -> api.isVanished(player.getUniqueId()))
                            .orElse(false))
                    .toList();
            int online = onlinePlayers.size();
            boolean isCurrent = serverName.equals(currentServerName);

            boolean isServerOnline = true;
            try {
                server.ping().get(50, TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                isServerOnline = false;
            }

            // Use the localized bullet based on online status.
            Component bullet = isServerOnline
                    ? feature.getLocalizationHandler().getMessage("playerlist.server_bullet_online").forAudience(audience).build()
                    : feature.getLocalizationHandler().getMessage("playerlist.server_bullet_offline").forAudience(audience).build();

            Component dash = feature.getLocalizationHandler().getMessage("playerlist.server_dash").forAudience(audience).build();

            Component onlineComponent = feature.getLocalizationHandler().getMessage("playerlist.server_online_count").forAudience(audience).with("online", String.valueOf(online)).build();

            Component nameComponent = isCurrent
                    ? feature.getLocalizationHandler().getMessage("playerlist.server_name_current").forAudience(audience).with("server", serverName).build()
                    : feature.getLocalizationHandler().getMessage("playerlist.server_name_other").forAudience(audience).with("server", serverName).build();

            // Build clickable buttons.
            TextComponent.Builder buttons = Component.text();
            if (!isCurrent) {
                Component buttonConnect = feature.getLocalizationHandler().getMessage("playerlist.server_connect").forAudience(audience).build()
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/server " + serverName))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                feature.getLocalizationHandler().getMessage("playerlist.server_connect_hover").forAudience(audience).with("server", serverName).build()
                        ));
                buttons.append(buttonConnect);
            }
            Component buttonPlayers = feature.getLocalizationHandler().getMessage("playerlist.server_players").forAudience(audience).build()
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/list " + serverName))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            feature.getLocalizationHandler().getMessage("playerlist.server_players_hover").forAudience(audience).with("server", serverName).build()
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
        Component tip = feature.getLocalizationHandler().getMessage("playerlist.global_tip").forAudience(audience).build();
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
                .filter(player -> !APIRegistry.get(VanishAPI.class)
                        .map(api -> api.isVanished(player.getUniqueId()))
                        .orElse(false))
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
            playerCountMessage = feature.getLocalizationHandler().getMessage("playerlist.server_count_none").forAudience(audience).with("server", serverName).build();
        } else if (playerCount == 1) {
            playerCountMessage = feature.getLocalizationHandler().getMessage("playerlist.server_count_one").forAudience(audience).with("server", serverName).build();
        } else {
            playerCountMessage = feature.getLocalizationHandler().getMessage("playerlist.server_count_multiple").forAudience(audience).with("server", serverName).with("count", String.valueOf(playerCount)).build();
        }
        lines.add(playerCountMessage);

        if (!nonStaffPlayers.isEmpty()) {
            lines.add(Component.empty());
            String playerNames = nonStaffPlayers.stream()
                    .map(Player::getUsername)
                    .collect(Collectors.joining(", "));
            Component playerListLine = feature.getLocalizationHandler().getMessage("playerlist.server_players_list").forAudience(audience).with("players", playerNames).build();
            lines.add(playerListLine);
        }

        if (!staffPlayers.isEmpty()) {
            lines.add(Component.empty());
            String staffNames = staffPlayers.stream()
                    .map(Player::getUsername)
                    .collect(Collectors.joining(", "));
            Component staffListLine = feature.getLocalizationHandler().getMessage("playerlist.server_staff_list").forAudience(audience).with("players", staffNames).build();
            lines.add(staffListLine);
        }

        lines.add(Component.empty());
        Component tip = feature.getLocalizationHandler().getMessage("playerlist.server_tip_global").forAudience(audience).build();
        lines.add(tip);
        lines.add(Component.empty());

        return Component.join(JoinConfiguration.separator(Component.newline()), lines);
    }
}
