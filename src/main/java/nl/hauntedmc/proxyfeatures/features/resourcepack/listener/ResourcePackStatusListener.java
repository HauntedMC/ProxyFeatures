package nl.hauntedmc.proxyfeatures.features.resourcepack.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.features.resourcepack.ResourcePack;

public class ResourcePackStatusListener {
    private final ResourcePack feature;

    public ResourcePackStatusListener(ResourcePack feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        ResourcePackStatusPolicy.Resolution resolution = ResourcePackStatusPolicy.resolve(event.getStatus());

        if (resolution.unblockConfiguration()) {
            feature.getResourcePackHandler().unblockConfiguration(player.getUniqueId());
        }

        if (resolution.action() == ResourcePackStatusPolicy.Action.NONE) {
            return;
        }

        Component message = feature.getLocalizationHandler()
                .getMessage(resolution.localizationKey())
                .forAudience(player)
                .build();

        if (resolution.action() == ResourcePackStatusPolicy.Action.DISCONNECT) {
            player.disconnect(message);
        } else {
            player.sendMessage(message);
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        feature.getResourcePackHandler().unblockConfiguration(event.getPlayer().getUniqueId());
    }
}
