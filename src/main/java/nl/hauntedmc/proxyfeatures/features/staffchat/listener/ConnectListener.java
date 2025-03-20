package nl.hauntedmc.proxyfeatures.features.staffchat.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.ChatChannel;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.ChatChannelHandler;

public class ConnectListener {

    private final StaffChat feature;
    private final ChatChannelHandler handler;

    public ConnectListener(StaffChat feature) {
        this.feature = feature;
        this.handler = feature.getChatChannelHandler();
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        for (ChatChannel channel : handler.getChannels()) {
            if (player.hasPermission(channel.getPermission())) {
                handler.addViewer(channel, player);
            }
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        for (ChatChannel channel : handler.getChannels()) {
            handler.removeViewer(channel, player);
        }
    }
}
