package nl.hauntedmc.proxyfeatures.features.messager.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.messager.Messenger;
import nl.hauntedmc.proxyfeatures.features.messager.internal.MessagingHandler;

public class PlayerListener {
    private final MessagingHandler handler;

    public PlayerListener(Messenger feature) {
        this.handler = feature.getHandler();
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        // Load this player's persisted settings into memory
        handler.loadPlayerSettings(player);

        // Auto-enable spy mode if the player has the spy permission and isn't already spying
        if (player.hasPermission("proxyfeatures.feature.messager.command.spy")
                && !handler.isSpy(player.getUniqueId())) {
            handler.toggleSpy(player.getUniqueId());
        }
    }
}
