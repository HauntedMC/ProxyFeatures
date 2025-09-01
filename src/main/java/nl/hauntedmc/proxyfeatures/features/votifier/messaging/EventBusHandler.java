package nl.hauntedmc.proxyfeatures.features.votifier.messaging;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;

public class EventBusHandler {

    private final MessagingDataAccess redisBus;
    private final Votifier feature;

    public EventBusHandler(Votifier feature, MessagingDataAccess redisBus) {
        this.feature = feature;
        this.redisBus = redisBus;
    }

    public void publishVote(VoteMessage msg, String channel) {
        redisBus.publish(channel, msg).exceptionally(ex -> {
            feature.getLogger().error("Failed to publish vote message: " + ex.getMessage());
            return null;
        });
    }
}
