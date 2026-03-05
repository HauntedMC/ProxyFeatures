package nl.hauntedmc.proxyfeatures.features.votifier.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;

public final class VotifierMonthResultListener {

    private final Votifier feature;

    public VotifierMonthResultListener(Votifier feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        if (feature.getService() == null) return;
        feature.getService().onPlayerPostLogin(event.getPlayer());
    }
}