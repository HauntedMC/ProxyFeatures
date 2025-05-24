package nl.hauntedmc.proxyfeatures.features.serverlinks.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import nl.hauntedmc.proxyfeatures.features.serverlinks.ServerLinks;

public class JoinListener {

    private final ServerLinks feature;

    public JoinListener(ServerLinks feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        feature.getServerLinksHandler().applyLinks(event.getPlayer());
    }
}
