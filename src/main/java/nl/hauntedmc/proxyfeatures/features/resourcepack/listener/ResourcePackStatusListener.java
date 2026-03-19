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
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        Component message;
        switch (status) {
            case ACCEPTED, DOWNLOADED:
                return;
            case SUCCESSFUL:
                feature.getResourcePackHandler().unblockConfiguration(player.getUniqueId());
                return;
            case DECLINED, DISCARDED:
                feature.getResourcePackHandler().unblockConfiguration(player.getUniqueId());
                message = feature.getLocalizationHandler()
                        .getMessage("resourcepack.kick_declined")
                        .forAudience(player)
                        .build();
                player.disconnect(message);
                break;
            case FAILED_DOWNLOAD:
                feature.getResourcePackHandler().unblockConfiguration(player.getUniqueId());
                message = feature.getLocalizationHandler()
                        .getMessage("resourcepack.kick_failed")
                        .forAudience(player)
                        .build();
                player.disconnect(message);
                break;
            case FAILED_RELOAD:
                feature.getResourcePackHandler().unblockConfiguration(player.getUniqueId());
                message = feature.getLocalizationHandler()
                        .getMessage("resourcepack.reload_failed")
                        .forAudience(player)
                        .build();
                player.sendMessage(message);
                return;
            case INVALID_URL:
                feature.getResourcePackHandler().unblockConfiguration(player.getUniqueId());
                message = feature.getLocalizationHandler()
                        .getMessage("resourcepack.url_invalid")
                        .forAudience(player)
                        .build();
                player.sendMessage(message);
                return;
            default:
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        feature.getResourcePackHandler().unblockConfiguration(event.getPlayer().getUniqueId());
    }
}
