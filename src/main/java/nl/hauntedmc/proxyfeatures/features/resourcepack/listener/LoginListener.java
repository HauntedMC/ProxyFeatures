package nl.hauntedmc.proxyfeatures.features.resourcepack.listener;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import nl.hauntedmc.proxyfeatures.features.resourcepack.ResourcePack;

public class LoginListener {
    private final ResourcePack feature;

    public LoginListener(ResourcePack feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onConfigurate(PlayerConfigurationEvent event, Continuation continuation) {
        Player player = event.player();
        feature.getResourcePackHandler().blockConfiguration(player.getUniqueId(), continuation);
        ResourcePackInfo packInfo = feature.getResourcePackHandler().getPackInfo("global");
        if (!player.getAppliedResourcePacks().contains(packInfo)) {
            player.sendResourcePackOffer(packInfo);
        } else {
            continuation.resume();
        }
    }
}