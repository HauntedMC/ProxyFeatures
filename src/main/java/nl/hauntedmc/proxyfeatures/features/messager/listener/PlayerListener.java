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

        // If player has permission and isn't already spying -> enable and persist
        if (player.hasPermission(SPY_PERM) && !handler.isSpy(id)) {
            handler.setSpy(id, true); // persist + in-memory
        }

        // If player lacks permission but somehow is spying -> disable and persist (belt & suspenders)
        if (!player.hasPermission(SPY_PERM) && handler.isSpy(id)) {
            handler.setSpy(id, false); // persist + in-memory
        }
    }
}

