package nl.hauntedmc.proxyfeatures.features.staffchat.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.ChatChannel;

public class EventBusHandler {

    private final MessagingDataAccess redisBus;
    private final StaffChat feature;
    private Subscription chatSubscription;


    public EventBusHandler(StaffChat feature, MessagingDataAccess redisBus) {
        this.feature = feature;
        this.redisBus = redisBus;
    }

    public void subscribeChannel(String channel) {
        try {
            chatSubscription = redisBus.subscribe(
                    channel,
                    StaffChatMessage.class,
                    this::handleIncoming
            );
        } catch (Exception ex) {
            feature.getLogger().error("Failed to subscribe to channel");
        }}


    private void handleIncoming(StaffChatMessage scMessage) {
        ChatChannel channel = feature.getChatChannelHandler().getChannelByPrefix(scMessage.getPrefix());
        if (channel != null) {
            channel.broadcastMessage(feature, scMessage.getSenderServer(), scMessage.getSenderName(), scMessage.getMessage());
        }

    }

    public void disable() {
        if (chatSubscription != null) {
            chatSubscription.unsubscribe();
            chatSubscription = null;
        }
    }
}
