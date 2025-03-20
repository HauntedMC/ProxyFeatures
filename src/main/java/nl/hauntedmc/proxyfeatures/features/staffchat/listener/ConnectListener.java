package nl.hauntedmc.proxyfeatures.features.staffchat.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.ChatChannel;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.ChatChannelHandler;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.Optional;

public class ConnectListener {

    private final StaffChat feature;
    private final ChatChannelHandler handler;

    public ConnectListener(StaffChat feature) {
        this.feature = feature;
        this.handler = feature.getChatChannelHandler();
    }

    // Helper to retrieve the staff channel.
    private Optional<ChatChannel> getStaffChannel() {
        return handler.getChannels().values().stream()
                .filter(channel -> channel.getId().equals("staff"))
                .findFirst();
    }

    // Broadcasts a message to all viewers in a given channel.
    private void broadcastToChannel(ChatChannel channel, Component message) {
        for (Player viewer : handler.getViewers(channel)) {
            if (viewer.hasPermission(channel.getPermission())) {
                viewer.sendMessage(message);
            }
        }
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        // Add the player to all channels for which they have permission.
        for (ChatChannel channel : handler.getChannels().values()) {
            if (player.hasPermission(channel.getPermission())) {
                handler.addViewer(channel, player);
            }
        }
        // Extra behavior for the staff channel: send join message.
        if (player.hasPermission("proxyfeatures.feature.staffchat.staff")) {
            getStaffChannel().ifPresent(staffChannel -> {
                Component joinMessage = feature.getLocalizationHandler().getMessage("staffchat.staff_join", player,
                        Map.of("player", player.getUsername()));
                broadcastToChannel(staffChannel, joinMessage);
            });
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        // Remove the player from all channels.
        for (ChatChannel channel : handler.getChannels().values()) {
            handler.removeViewer(channel, player);
        }
        // Extra behavior for the staff channel: send leave message.
        if (player.hasPermission("proxyfeatures.feature.staffchat.staff")) {
            getStaffChannel().ifPresent(staffChannel -> {
                Component leaveMessage = feature.getLocalizationHandler().getMessage("staffchat.staff_leave", player,
                        Map.of("player", player.getUsername()));
                broadcastToChannel(staffChannel, leaveMessage);
            });
        }
    }

    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        // Only process actual server switches (ignore initial join without a previous server)
        if (event.getPreviousServer().isEmpty()) {
            return;
        }
        // Extra behavior for the staff channel: send switch message.
        if (player.hasPermission("proxyfeatures.feature.staffchat.staff")) {
            getStaffChannel().ifPresent(staffChannel -> {
                String from = event.getPreviousServer().get().getServerInfo().getName();
                String to = event.getServer().getServerInfo().getName();
                Component switchMessage = feature.getLocalizationHandler().getMessage("staffchat.staff_switch", player,
                        Map.of("player", player.getUsername(), "from", from, "to", to));
                broadcastToChannel(staffChannel, switchMessage);
            });
        }
    }
}
