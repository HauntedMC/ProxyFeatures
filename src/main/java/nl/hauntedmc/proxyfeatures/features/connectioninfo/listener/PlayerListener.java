package nl.hauntedmc.proxyfeatures.features.connectioninfo.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import nl.hauntedmc.proxyfeatures.features.connectioninfo.ConnectionInfo;

public class PlayerListener {

    private final ConnectionInfo feature;

    public PlayerListener(ConnectionInfo feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        feature.getSessionHandler().recordJoin(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        feature.getSessionHandler().clearJoin(event.getPlayer().getUniqueId());
    }
}
