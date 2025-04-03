package nl.hauntedmc.proxyfeatures.features.hlink.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.hlink.HLink;

public class PlayerListener {

    private final HLink feature;

    public PlayerListener(HLink feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onPlayerLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        feature.getPlugin().getLogger().info("Updating");
        feature.getHLinkHandler().updatePlayerData(player);
    }
}
