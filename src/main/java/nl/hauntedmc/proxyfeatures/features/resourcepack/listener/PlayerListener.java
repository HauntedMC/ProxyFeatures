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
        boolean hasPreviousServer = event.server().getPreviousServer().isPresent();
        String currentServer = event.server().getServer().getServerInfo().getName();

        ResourcePackInfo previousPackInfo = null;
        ResourcePackInfo currentPackInfo = null;

        if (hasPreviousServer) {
            String previousServer = event.server().getPreviousServer().get().getServerInfo().getName();
            previousPackInfo = feature.getResourcePackHandler().getPackInfo(previousServer);
            currentPackInfo = feature.getResourcePackHandler().getPackInfo(currentServer);
        }

        ResourcePackTransitionPolicy.TransitionPlan transitionPlan = ResourcePackTransitionPolicy.plan(
                hasPreviousServer,
                currentServer,
                previousPackInfo != null,
                currentPackInfo != null
        );

        if (transitionPlan.removePreviousPack() && previousPackInfo != null) {
            player.removeResourcePacks(previousPackInfo);
        }

        if (transitionPlan.resumeImmediately()) {
            continuation.resume();
            return;
        }

        sendResourcePackOffer(continuation, player, transitionPlan.offerPackIdentifier());
    }


    private void sendResourcePackOffer(Continuation continuation, Player player, String packIdentifier) {
        ResourcePackInfo packInfo = feature.getResourcePackHandler().getPackInfo(packIdentifier);
        boolean packExists = packInfo != null;
        boolean alreadyApplied = packExists && player.getAppliedResourcePacks().contains(packInfo);

        if (ResourcePackOfferPolicy.resolve(packExists, alreadyApplied) == ResourcePackOfferPolicy.Action.RESUME) {
            continuation.resume();
            return;
        }

        try {
            feature.getResourcePackHandler().blockConfiguration(player.getUniqueId(), continuation);
            player.sendResourcePackOffer(packInfo);
        } catch (Throwable t) {
            feature.getResourcePackHandler().unblockConfiguration(player.getUniqueId());
            feature.getLogger().warn("[ResourcePack] Failed to send resource pack offer to "
                    + player.getUsername() + ": " + t.getClass().getSimpleName());
        }
    }

}
