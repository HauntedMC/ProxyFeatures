package nl.hauntedmc.proxyfeatures.features.staffchat.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.ChatChannel;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.ChatChannelHandler;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EventBusHandlerTest {

    @Test
    void subscribeAndIncomingMessageBroadcastsOnMatchingChannel() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        StaffChat feature = mock(StaffChat.class);
        ChatChannelHandler channelHandler = mock(ChatChannelHandler.class);
        ChatChannel channel = mock(ChatChannel.class);
        when(feature.getChatChannelHandler()).thenReturn(channelHandler);
        when(channelHandler.getChannelByPrefix("!")).thenReturn(channel);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<StaffChatMessage>> captor = ArgumentCaptor.forClass(Consumer.class);
        when(redis.subscribe(eq("staff.chat"), eq(StaffChatMessage.class), captor.capture()))
                .thenReturn(mock(Subscription.class));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribeChannel("staff.chat");
        captor.getValue().accept(new StaffChatMessage("staffchat", "!", "hello", "Remy", "hub-1"));

        verify(channel).broadcastMessage(feature, "hub-1", "Remy", "hello");
    }

    @Test
    void incomingMessageWithoutMatchingChannelIsIgnored() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        StaffChat feature = mock(StaffChat.class);
        ChatChannelHandler channelHandler = mock(ChatChannelHandler.class);
        when(feature.getChatChannelHandler()).thenReturn(channelHandler);
        when(channelHandler.getChannelByPrefix("$")).thenReturn(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<StaffChatMessage>> captor = ArgumentCaptor.forClass(Consumer.class);
        when(redis.subscribe(eq("staff.chat"), eq(StaffChatMessage.class), captor.capture()))
                .thenReturn(mock(Subscription.class));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribeChannel("staff.chat");
        captor.getValue().accept(new StaffChatMessage("staffchat", "$", "hello", "Remy", "hub-1"));

        verify(channelHandler).getChannelByPrefix("$");
        verifyNoMoreInteractions(channelHandler);
    }

    @Test
    void disableUnsubscribesStoredSubscription() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        StaffChat feature = mock(StaffChat.class);
        Subscription subscription = mock(Subscription.class);
        when(redis.subscribe(eq("staff.chat"), eq(StaffChatMessage.class), any()))
                .thenReturn(subscription);

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribeChannel("staff.chat");
        handler.disable();
        handler.disable();

        verify(subscription, times(1)).unsubscribe();
    }

    @Test
    void subscribeFailureIsLogged() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        StaffChat feature = mock(StaffChat.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        when(feature.getLogger()).thenReturn(logger);
        when(redis.subscribe(eq("staff.chat"), eq(StaffChatMessage.class), any()))
                .thenThrow(new RuntimeException("boom"));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribeChannel("staff.chat");

        verify(logger).error("Failed to subscribe to channel");
    }
}
