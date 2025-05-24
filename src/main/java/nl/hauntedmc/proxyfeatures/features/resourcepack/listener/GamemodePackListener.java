package nl.hauntedmc.proxyfeatures.features.resourcepack.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import nl.hauntedmc.proxyfeatures.features.resourcepack.ResourcePack;


public class GamemodePackListener {

    private final ResourcePack feature;

    public GamemodePackListener(ResourcePack feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
    }
}
