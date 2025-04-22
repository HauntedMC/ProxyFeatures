package nl.hauntedmc.proxyfeatures.features.staffchat.internal;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;
import net.kyori.adventure.text.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a chat channel. Each channel manages its own viewers and handles message broadcasting.
 */
public class ChatChannel {

    private final String id;
    private final String permission;
    private final String formatKey;
    private final String prefix;
    // Thread-safe set for viewers.
    private final Set<Player> viewers = ConcurrentHashMap.newKeySet();

    public ChatChannel(String id, String prefix) {
        this.id = id;
        this.prefix = prefix;
        this.permission = "proxyfeatures.feature.staffchat." + id;
        this.formatKey = "staffchat."+id+"_format";
    }


    public String getId() {
        return id;
    }

    public String getPermission() {
        return permission;
    }

    public String getFormatKey() {
        return formatKey;
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * Returns an unmodifiable view of the viewers.
     */
    public Set<Player> getViewers() {
        return Collections.unmodifiableSet(viewers);
    }

    /**
     * Adds a player to the viewer list.
     *
     * @param player the player to add
     */
    public void addViewer(Player player) {
        viewers.add(player);
    }

    /**
     * Removes a player from the viewer list.
     *
     * @param player the player to remove
     */
    public void removeViewer(Player player) {
        viewers.remove(player);
    }

    /**
     * Broadcasts a message to all viewers with the required permission.
     */
    public void broadcastMessage(StaffChat feature, String serverName, String playerName, String message) {
        // Format the message using the localization handler and the channel’s format key.
        Component formattedMessage = feature.getLocalizationHandler().getMessage(formatKey).withPlaceholders(
                Map.of(
                        "server", serverName,
                        "player", playerName,
                        "message", message
                )
        ).build();

        for (Player viewer : getViewers()) {
            if (viewer.hasPermission(permission)) {
                viewer.sendMessage(formattedMessage);
            }
        }
        feature.getPlugin().getLogger().info(formattedMessage);
    }
}
