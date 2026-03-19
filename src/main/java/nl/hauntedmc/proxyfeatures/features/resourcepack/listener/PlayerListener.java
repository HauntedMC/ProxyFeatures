package nl.hauntedmc.proxyfeatures.features.resourcepack.listener;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import nl.hauntedmc.proxyfeatures.features.resourcepack.ResourcePack;

public class PlayerListener {
    private final ResourcePack feature;

    public PlayerListener(ResourcePack feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onConfigurate(PlayerConfigurationEvent event, Continuation continuation) {
        Player player = event.player();

        if (event.server().getPreviousServer().isEmpty()) {
            sendResourcePackOffer(continuation, player, "global");
        } else {

            String prevServer = event.server().getPreviousServer().get().getServerInfo().getName();
            ResourcePackInfo oldPackInfo = feature.getResourcePackHandler().getPackInfo(prevServer);
            if (oldPackInfo != null) {
                player.removeResourcePacks(oldPackInfo);
            }

            String curServer = event.server().getServer().getServerInfo().getName();
            ResourcePackInfo packInfo = feature.getResourcePackHandler().getPackInfo(curServer);
            if (packInfo != null) {
                sendResourcePackOffer(continuation, player, curServer);
            } else {
                continuation.resume();
            }
        }
    }


    private void sendResourcePackOffer(Continuation continuation, Player player, String packIdentifier) {
        ResourcePackInfo packInfo = feature.getResourcePackHandler().getPackInfo(packIdentifier);
        if (packInfo == null) {
            continuation.resume();
            return;
        }

        if (!player.getAppliedResourcePacks().contains(packInfo)) {
            try {
                feature.getResourcePackHandler().blockConfiguration(player.getUniqueId(), continuation);
                player.sendResourcePackOffer(packInfo);
            } catch (Throwable t) {
                feature.getResourcePackHandler().unblockConfiguration(player.getUniqueId());
                feature.getLogger().warn("[ResourcePack] Failed to send resource pack offer to "
                        + player.getUsername() + ": " + t.getClass().getSimpleName());
            }
        } else {
            continuation.resume();
        }
    }

}
