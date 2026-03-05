package nl.hauntedmc.proxyfeatures.features.votifier.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;

public final class VotifierPlayerListener {

    private final Votifier feature;

    public VotifierPlayerListener(Votifier feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        if (feature.getService() == null) return;
        feature.getService().onPlayerPostLogin(event.getPlayer());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (feature.getService() == null) return;
        feature.getService().onPlayerDisconnect(event.getPlayer().getUniqueId());
    }
}