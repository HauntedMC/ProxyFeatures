package nl.hauntedmc.proxyfeatures.features.logger.internal;

import com.velocitypowered.api.proxy.Player;

import nl.hauntedmc.proxyfeatures.features.logger.Logger;


public class LogHandler {

    private final Logger feature;

    public LogHandler(Logger feature) {
        this.feature = feature;
    }

    /**
     * Applies all configured chat filters to the incoming event.
     * If a filter rule matches, the event result is set to "denied" and notifications are sent.
     */
    public void logChat(Player player, String message) {
        if (player.hasPermission("proxyfeatures.feature.logger.bypass")) {
            return;
        }

        logChatMessage(message, player);
    }

    private void logChatMessage(String message, Player player) {
        String serverName = player.getCurrentServer()
                .map(server -> server.getServerInfo().getName())
                .orElse("unknown");
        feature.getPlugin().getLogger().info("[{}] {}: {}", serverName, player.getUsername(), message);
    }
}
