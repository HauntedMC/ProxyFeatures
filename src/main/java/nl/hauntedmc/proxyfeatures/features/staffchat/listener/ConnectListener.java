package nl.hauntedmc.proxyfeatures.features.staffchat.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;

public class ConnectListener {

    private final StaffChat feature;

    public ConnectListener(StaffChat feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("proxyfeatures.feature.staffchat.staff")) {
            feature.getStaffChatHandler().addViewer(player);
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        feature.getStaffChatHandler().removeViewer(player);
    }
}
