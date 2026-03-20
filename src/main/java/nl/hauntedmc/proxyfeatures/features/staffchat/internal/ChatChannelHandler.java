package nl.hauntedmc.proxyfeatures.features.staffchat.internal;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Acts as a registry for all chat channels.
 */
public class ChatChannelHandler {

    private final Map<String, ChatChannel> channels;

    public ChatChannelHandler(StaffChat feature) {
        this.channels = new HashMap<>();

        String staffPrefix = prefixOrDefault(feature.getConfigHandler().get("staff_prefix", String.class, "!"), "!");
        channels.put(staffPrefix, new ChatChannel("staff", staffPrefix));

        String teamPrefix = prefixOrDefault(feature.getConfigHandler().get("team_prefix", String.class, "?"), "?");
        channels.put(teamPrefix, new ChatChannel("team", teamPrefix));

        String adminPrefix = prefixOrDefault(feature.getConfigHandler().get("admin_prefix", String.class, "#"), "#");
        channels.put(adminPrefix, new ChatChannel("admin", adminPrefix));
    }

    private static String prefixOrDefault(String value, String def) {
        if (value == null) return def;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? def : trimmed;
    }

    public Map<String, ChatChannel> getChannels() {
        return channels;
    }

    /**
     * Retrieves a channel by its prefix.
     *
     * @param prefix the channel prefix
     * @return the corresponding ChatChannel or null if not found
     */
    public ChatChannel getChannelByPrefix(String prefix) {
        return channels.get(prefix);
    }

    /**
     * Initializes the viewer lists for all channels based on currently connected players.
     *
     * @param players a collection of connected players
     */
    public void initializeViewers(Collection<Player> players) {
        for (Player player : players) {
            for (ChatChannel channel : channels.values()) {
                if (player.hasPermission(channel.getPermission())) {
                    channel.addViewer(player);
                }
            }
        }
    }
}
