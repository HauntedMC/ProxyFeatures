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

    public ChatChannel(String id, String permission, String formatKey, String prefix) {
        this.id = id;
        this.permission = permission;
        this.formatKey = formatKey;
        this.prefix = prefix;
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
     *
     * @param feature the StaffChat feature (provides localization handler)
     * @param sender  the player sending the message
     * @param message the raw message content
     */
    public void broadcastMessage(StaffChat feature, Player sender, String message) {
        // Determine the sender's current server name.
        String serverName = sender.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("unknown");

        // Format the message using the localization handler and the channel’s format key.
        Component formattedMessage = feature.getLocalizationHandler().getMessage(formatKey, sender,
                Map.of(
                        "server", serverName,
                        "player", sender.getUsername(),
                        "message", message
                )
        );

        for (Player viewer : getViewers()) {
            if (viewer.hasPermission(permission)) {
                viewer.sendMessage(formattedMessage);
            }
        }
    }
}
