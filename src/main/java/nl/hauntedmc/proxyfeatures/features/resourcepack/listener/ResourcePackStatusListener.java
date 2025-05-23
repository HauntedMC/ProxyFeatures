package nl.hauntedmc.proxyfeatures.features.resourcepack.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.resourcepack.ResourcePack;
import net.kyori.adventure.text.Component;

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
            case ACCEPTED:
                message = feature.getLocalizationHandler()
                                .getMessage("resourcepack.accepted")
                                .forAudience(player)
                                .build();
                player.sendMessage(message);
                return;
            case DOWNLOADED:
                message = feature.getLocalizationHandler()
                        .getMessage("resourcepack.downloaded")
                        .forAudience(player)
                        .build();
                player.sendMessage(message);
                return;
            case SUCCESSFUL:
                message = feature.getLocalizationHandler()
                                .getMessage("resourcepack.loaded")
                                .forAudience(player)
                                .build();
                player.sendMessage(message);
                feature.getHandler().unblockConfiguration(player.getUniqueId());
                return;
            case DECLINED, DISCARDED:
                message = feature.getLocalizationHandler()
                                .getMessage("resourcepack.kick_declined")
                                .forAudience(player)
                                .build();
                player.disconnect(message);
                break;
            case FAILED_DOWNLOAD:
                message = feature.getLocalizationHandler()
                                .getMessage("resourcepack.kick_failed")
                                .forAudience(player)
                                .build();
                player.disconnect(message);
                break;
            case FAILED_RELOAD:
                message = feature.getLocalizationHandler()
                        .getMessage("resourcepack.reload_failed")
                        .forAudience(player)
                        .build();
                player.sendMessage(message);
                return;
            case INVALID_URL:
                message = feature.getLocalizationHandler()
                        .getMessage("resourcepack.url_invalid")
                        .forAudience(player)
                        .build();
                player.sendMessage(message);
                return;
            default:
        }
    }
}