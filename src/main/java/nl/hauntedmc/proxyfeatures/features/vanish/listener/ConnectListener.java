package nl.hauntedmc.proxyfeatures.features.vanish.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import nl.hauntedmc.proxyfeatures.features.vanish.Vanish;

public class ConnectListener {

    private final Vanish feature;

    public ConnectListener(Vanish feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        feature.getVanishRegistry().remove(event.getPlayer().getUniqueId());
    }
}
