package nl.hauntedmc.proxyfeatures.features.staffchat.internal;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;
import net.kyori.adventure.text.Component;

import java.util.*;

public class ChatChannelHandler {

    private final StaffChat feature;
    private final HashMap<String, ChatChannel> channels;
    private final Map<String, Set<Player>> viewers;

    public ChatChannelHandler(StaffChat feature) {
        this.feature = feature;
        this.channels = new HashMap<>();
        this.viewers = new HashMap<>();

        // Initialize channels using configuration values and fixed permission/format keys.
        String staffPrefix = (String) feature.getConfigHandler().getSetting("staff_prefix");
        channels.put(staffPrefix, new ChatChannel("staff", "proxyfeatures.feature.staffchat.staff", "staffchat.staff_format"));

        String teamPrefix = (String) feature.getConfigHandler().getSetting("team_prefix");
        channels.put(teamPrefix, new ChatChannel("team", "proxyfeatures.feature.staffchat.team", "staffchat.team_format"));

        String adminPrefix = (String) feature.getConfigHandler().getSetting("admin_prefix");
        channels.put(adminPrefix, new ChatChannel("admin", "proxyfeatures.feature.staffchat.admin", "staffchat.admin_format"));

        // Initialize an empty viewers set for each channel.
        for (ChatChannel channel : channels.values()) {
            viewers.put(channel.getId(), Collections.synchronizedSet(new HashSet<>()));
        }
    }

    public HashMap<String, ChatChannel> getChannels() {
        return channels;
    }

    public Set<Player> getViewers(ChatChannel channel) {
        return viewers.get(channel.getId());
    }

    public void addViewer(ChatChannel channel, Player player) {
        viewers.get(channel.getId()).add(player);
    }

    public void removeViewer(ChatChannel channel, Player player) {
        viewers.get(channel.getId()).remove(player);
    }

    public void sendChannelMessage(ChatChannel channel, Player sender, String message) {
        // Determine the sender's current server name.
        String serverName = sender.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("unknown");

        // Format the message using the localization handler and the channel’s format key.
        Component formattedMessage = feature.getLocalizationHandler().getMessage(channel.getFormatKey(), sender,
                Map.of(
                        "server", serverName,
                        "player", sender.getUsername(),
                        "message", message
                )
        );

        // Broadcast the formatted message to all viewers in this channel.
        synchronized (getViewers(channel)) {
            for (Player viewer : getViewers(channel)) {
                if (viewer.hasPermission(channel.getPermission())) {
                    viewer.sendMessage(formattedMessage);
                }
            }
        }
    }
}
