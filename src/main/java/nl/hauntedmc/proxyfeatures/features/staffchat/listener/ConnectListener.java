package nl.hauntedmc.proxyfeatures.features.staffchat.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.ChatChannel;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.ChatChannelHandler;

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

    /**
     * Broadcasts a localized message to all viewers in the given channel,
     * building the message per *recipient* so translations are correct.
     */
    private void broadcastToChannel(ChatChannel channel, String messageKey, Map<String, String> placeholders) {
        for (Player viewer : channel.getViewers()) {
            if (viewer.hasPermission(channel.getPermission())) {
                Component message = feature.getLocalizationHandler()
                        .getMessage(messageKey)
                        .withPlaceholders(MessagePlaceholders.of(placeholders))
                        .forAudience(viewer) // build for the recipient's locale
                        .build();
                viewer.sendMessage(message);
            }
        }
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        // Add the player as a viewer to all channels for which they have permission.
        for (ChatChannel channel : handler.getChannels().values()) {
            if (player.hasPermission(channel.getPermission())) {
                channel.addViewer(player);
            }
        }
        // Special behavior for the staff channel.
        if (player.hasPermission("proxyfeatures.feature.staffchat.staff")) {
            getStaffChannel().ifPresent(staffChannel -> broadcastToChannel(
                    staffChannel,
                    "staffchat.staff_join",
                    Map.of("player", player.getUsername())
            ));
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        // Remove the player from all channels.
        for (ChatChannel channel : handler.getChannels().values()) {
            channel.removeViewer(player);
        }
        // Special behavior for the staff channel.
        if (player.hasPermission("proxyfeatures.feature.staffchat.staff")) {
            getStaffChannel().ifPresent(staffChannel -> broadcastToChannel(
                    staffChannel,
                    "staffchat.staff_leave",
                    Map.of("player", player.getUsername())
            ));
        }
    }

    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        Player player = event.getPlayer();

        if (event.getPreviousServer().isEmpty()) {
            return;
        }
        // Special behavior for the staff channel.
        if (player.hasPermission("proxyfeatures.feature.staffchat.staff")) {
            getStaffChannel().ifPresent(staffChannel -> {
                String from = event.getPreviousServer().get().getServerInfo().getName();
                String to = event.getServer().getServerInfo().getName();
                broadcastToChannel(
                        staffChannel,
                        "staffchat.staff_switch",
                        Map.of("player", player.getUsername(), "from", from, "to", to)
                );
            });
        }
    }
}
