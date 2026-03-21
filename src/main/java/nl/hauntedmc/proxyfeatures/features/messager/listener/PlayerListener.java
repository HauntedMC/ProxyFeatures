package nl.hauntedmc.proxyfeatures.features.messager.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.messager.Messenger;
import nl.hauntedmc.proxyfeatures.features.messager.internal.MessagingHandler;

import java.util.UUID;

public class PlayerListener {
    private final MessagingHandler handler;

    public PlayerListener(Messenger feature) {
        this.handler = feature.getHandler();
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        // Load this player's persisted settings into memory (also enforces spy permission)
        handler.loadPlayerSettings(player);

        final String SPY_PERM = "proxyfeatures.feature.messager.command.spy";

        SpyStatePolicy.Action action = SpyStatePolicy.reconcile(
                player.hasPermission(SPY_PERM),
                handler.isSpy(id)
        );
        if (action == SpyStatePolicy.Action.ENABLE) {
            handler.setSpy(id, true);
        } else if (action == SpyStatePolicy.Action.DISABLE) {
            handler.setSpy(id, false);
        }
    }
}
