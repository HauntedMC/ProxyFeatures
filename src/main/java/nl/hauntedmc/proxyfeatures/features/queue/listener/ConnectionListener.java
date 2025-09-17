package nl.hauntedmc.proxyfeatures.features.queue.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import nl.hauntedmc.proxyfeatures.features.queue.QueueManager;

import java.util.UUID;

/**
 * Tracks disconnects (to start grace) and successful connects (to clear queue entries).
 */
public class ConnectionListener {
    private final QueueManager manager;

    public ConnectionListener(QueueManager manager) {
        this.manager = manager;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        manager.onDisconnect(id);
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        String newServer = event.getServer().getServerInfo().getName();
        manager.onPostConnect(event.getPlayer(), newServer);
    }
}
