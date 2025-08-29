package nl.hauntedmc.proxyfeatures.features.vanish.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.proxyfeatures.features.vanish.Vanish;

import java.util.UUID;

public class EventBusHandler {

    private final MessagingDataAccess redisBus;
    private final Vanish feature;

    private Subscription subscription;

    public EventBusHandler(Vanish feature, MessagingDataAccess redisBus) {
        this.feature = feature;
        this.redisBus = redisBus;
    }

    public void subscribeChannel(String channel) {
        try {
            subscription = redisBus.subscribe(
                    channel,
                    VanishStateMessage.class,
                    this::handleIncoming
            );
        } catch (Exception ex) {
            feature.getLogger().error("Failed to subscribe to vanish channel "+channel+": " + ex.getMessage());
        }
    }

    private void handleIncoming(VanishStateMessage msg) {
        if (msg == null) return;

        UUID uuid = null;
        try {
            if (msg.getPlayerUuid() != null) {
                uuid = UUID.fromString(msg.getPlayerUuid());
            }
        } catch (IllegalArgumentException iae) {
            feature.getLogger().warn("Received vanish update with invalid UUID: " + msg.getPlayerUuid());
            return;
        }

        try {
            feature.getVanishRegistry().applyUpdate(uuid, msg.getPlayerName(), msg.isVanished());
        } catch (Throwable t) {
            feature.getLogger().error("Error applying vanish update for "+msg.getPlayerName()+": " + t.getMessage());
        }
    }

    public void disable() {
        if (subscription != null) {
            try {
                subscription.unsubscribe();
            } catch (Exception ignored) {
            }
            subscription = null;
        }
    }
}
