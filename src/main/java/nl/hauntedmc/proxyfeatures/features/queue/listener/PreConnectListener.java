package nl.hauntedmc.proxyfeatures.features.queue.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.queue.Queue;
import nl.hauntedmc.proxyfeatures.features.queue.QueueManager;

/**
 * Intercepts target connections and decides to allow or queue them.
 */
public class PreConnectListener {
    private final Queue feature;
    private final QueueManager manager;

    public PreConnectListener(Queue feature, QueueManager manager) {
        this.feature = feature;
        this.manager = manager;
    }

    @Subscribe
    public void onPreConnect(ServerPreConnectEvent event) {
        if (event.getOriginalServer() == null) return;

        String target = event.getOriginalServer().getServerInfo().getName();
        Player player = event.getPlayer();

        var decision = manager.handlePreConnect(player, target);
        if (!decision.allow()) {
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
        } else if (decision.bypass()) {
            player.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.join.bypass")
                    .withPlaceholders(java.util.Map.of("server", target))
                    .forAudience(player)
                    .build());
        }
    }
}
