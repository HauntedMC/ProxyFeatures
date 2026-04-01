package nl.hauntedmc.proxyfeatures.features.vanish.internal.messaging;

import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.proxyfeatures.features.vanish.Vanish;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.VanishRegistry;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EventBusHandlerTest {

    @Test
    void subscribeAndIncomingValidUpdateAppliesToRegistry() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        Vanish feature = mock(Vanish.class);
        VanishRegistry registry = mock(VanishRegistry.class);
        when(feature.getVanishRegistry()).thenReturn(registry);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<VanishStateMessage>> captor = ArgumentCaptor.forClass(Consumer.class);
        when(redis.subscribe(eq("proxy.vanish.update"), eq(VanishStateMessage.class), captor.capture()))
                .thenReturn(mock(Subscription.class));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        UUID uuid = UUID.randomUUID();
        handler.subscribeChannel("proxy.vanish.update");
        captor.getValue().accept(new VanishStateMessage("vanish_update", uuid.toString(), "Remy", true, "hub-1"));

        verify(registry).applyUpdate(uuid, "Remy", true);
    }

    @Test
    void invalidUuidUpdateIsRejectedAndWarned() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        Vanish feature = mock(Vanish.class);
        VanishRegistry registry = mock(VanishRegistry.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        when(feature.getVanishRegistry()).thenReturn(registry);
        when(feature.getLogger()).thenReturn(logger);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<VanishStateMessage>> captor = ArgumentCaptor.forClass(Consumer.class);
        when(redis.subscribe(eq("proxy.vanish.update"), eq(VanishStateMessage.class), captor.capture()))
                .thenReturn(mock(Subscription.class));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribeChannel("proxy.vanish.update");
        captor.getValue().accept(new VanishStateMessage("vanish_update", "not-a-uuid", "Remy", true, "hub-1"));

        verify(logger).warn(contains("invalid UUID"));
        verifyNoInteractions(registry);
    }

    @Test
    void nullMessageIsIgnored() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        Vanish feature = mock(Vanish.class);
        VanishRegistry registry = mock(VanishRegistry.class);
        when(feature.getVanishRegistry()).thenReturn(registry);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<VanishStateMessage>> captor = ArgumentCaptor.forClass(Consumer.class);
        when(redis.subscribe(eq("proxy.vanish.update"), eq(VanishStateMessage.class), captor.capture()))
                .thenReturn(mock(Subscription.class));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribeChannel("proxy.vanish.update");
        captor.getValue().accept(null);

        verifyNoInteractions(registry);
    }

    @Test
    void registryFailureIsLoggedAsError() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        Vanish feature = mock(Vanish.class);
        VanishRegistry registry = mock(VanishRegistry.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        when(feature.getVanishRegistry()).thenReturn(registry);
        when(feature.getLogger()).thenReturn(logger);
        doThrow(new RuntimeException("boom"))
                .when(registry).applyUpdate(any(), any(), anyBoolean());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<VanishStateMessage>> captor = ArgumentCaptor.forClass(Consumer.class);
        when(redis.subscribe(eq("proxy.vanish.update"), eq(VanishStateMessage.class), captor.capture()))
                .thenReturn(mock(Subscription.class));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        UUID uuid = UUID.randomUUID();
        handler.subscribeChannel("proxy.vanish.update");
        captor.getValue().accept(new VanishStateMessage("vanish_update", uuid.toString(), "Remy", false, "hub-1"));

        verify(logger).error(contains("Error applying vanish update"));
    }

    @Test
    void subscribeFailureAndDisableUnsubscribeAreHandled() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        Vanish feature = mock(Vanish.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        when(feature.getLogger()).thenReturn(logger);

        when(redis.subscribe(eq("proxy.vanish.update"), eq(VanishStateMessage.class), any()))
                .thenThrow(new RuntimeException("down"));

        EventBusHandler failing = new EventBusHandler(feature, redis);
        failing.subscribeChannel("proxy.vanish.update");
        verify(logger).error(contains("Failed to subscribe to vanish channel"));

        Subscription subscription = mock(Subscription.class);
        when(subscription.unsubscribe()).thenThrow(new RuntimeException("ignore"));
        when(redis.subscribe(eq("proxy.vanish.update"), eq(VanishStateMessage.class), any()))
                .thenReturn(subscription);

        EventBusHandler normal = new EventBusHandler(feature, redis);
        normal.subscribeChannel("proxy.vanish.update");
        normal.disable();
        normal.disable();

        verify(subscription, times(1)).unsubscribe();
    }
}
