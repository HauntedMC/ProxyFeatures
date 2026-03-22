package nl.hauntedmc.proxyfeatures.features.hlink.internal.hook;

import com.velocitypowered.api.proxy.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeMutateEvent;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.hlink.HLink;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LuckPermsHook {

    private static final List<EventSubscription<?>> subscriptions = new ArrayList<>();

    /**
     * Subscribes to LuckPerms events and updates the player's data when their direct group assignments change.
     *
     * @param feature The HLink feature instance.
     */
    public static void subscribeLuckPermsHook(HLink feature) {
        try {
            LuckPerms api = LuckPermsProvider.get();
            EventBus eventBus = api.getEventBus();

            EventSubscription<NodeMutateEvent> nodeSubscription = eventBus.subscribe(
                    feature.getPlugin(),
                    NodeMutateEvent.class,
                    event -> {
                        String friendlyName = event.getTarget().getFriendlyName();
                        Optional<Player> playerOpt = ProxyFeatures.getProxyInstance().getPlayer(friendlyName);
                        playerOpt.ifPresent(player -> feature.getHLinkHandler().updatePlayerData(player));
                    }
            );
            subscriptions.add(nodeSubscription);
        } catch (Exception t) {
            feature.getLogger().warn("HLink: LuckPerms hook unavailable; continuing without group sync hook.");
        }
    }

    /**
     * Unsubscribes from all LuckPerms events that were subscribed via this hook.
     */
    public static void unsubscribeLuckPermsHook() {
        for (EventSubscription<?> sub : subscriptions) {
            sub.close();
        }
        subscriptions.clear();
    }
}
