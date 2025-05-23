package nl.hauntedmc.proxyfeatures.features.resourcepack.listener;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import nl.hauntedmc.proxyfeatures.features.resourcepack.ResourcePack;
import nl.hauntedmc.proxyfeatures.features.resourcepack.util.ResourceUtils;

public class JoinListener {
    private final ResourcePack feature;
    private final String url;
    private final byte[] hash;

    public JoinListener(ResourcePack feature) {
        this.feature = feature;
        this.url = (String) feature.getConfigHandler().getSetting("url");
        this.hash = ResourceUtils.hexToBytes((String) feature.getConfigHandler().getSetting("hash"));
    }

    @Subscribe
    public void onPostLogin(PlayerConfigurationEvent event, Continuation continuation) {
        Player player = event.player();
        feature.getHandler().blockConfiguration(player.getUniqueId(), continuation);
        ResourcePackInfo packInfo = feature.getHandler().buildPackInfo(url, hash);
        player.sendResourcePackOffer(packInfo);
    }
}