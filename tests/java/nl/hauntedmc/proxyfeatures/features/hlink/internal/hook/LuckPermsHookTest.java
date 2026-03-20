package nl.hauntedmc.proxyfeatures.features.hlink.internal.hook;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.model.PermissionHolder;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.hlink.HLink;
import nl.hauntedmc.proxyfeatures.features.hlink.internal.HLinkHandler;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Optional;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LuckPermsHookTest {

    @AfterEach
    void cleanupSubscriptions() {
        LuckPermsHook.unsubscribeLuckPermsHook();
    }

    @Test
    void utilityClassCanBeInstantiated() {
        org.junit.jupiter.api.Assertions.assertNotNull(new LuckPermsHook());
    }

    @Test
    void subscribeReceivesEventsAndUpdatesOnlinePlayerData() {
        HLink feature = mock(HLink.class);
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        HLinkHandler handler = mock(HLinkHandler.class);
        when(feature.getPlugin()).thenReturn(plugin);
        when(feature.getHLinkHandler()).thenReturn(handler);

        LuckPerms lp = mock(LuckPerms.class);
        EventBus eventBus = mock(EventBus.class);
        @SuppressWarnings("unchecked")
        EventSubscription<NodeMutateEvent> subscription = mock(EventSubscription.class);
        when(lp.getEventBus()).thenReturn(eventBus);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<NodeMutateEvent>> captor = ArgumentCaptor.forClass(Consumer.class);
        when(eventBus.subscribe(eq(plugin), eq(NodeMutateEvent.class), captor.capture()))
                .thenReturn(subscription);

        ProxyServer proxy = mock(ProxyServer.class);
        Player player = mock(Player.class);
        when(proxy.getPlayer("Remy")).thenReturn(Optional.of(player));

        try (MockedStatic<LuckPermsProvider> lpProvider = mockStatic(LuckPermsProvider.class);
             MockedStatic<ProxyFeatures> proxyStatic = mockStatic(ProxyFeatures.class)) {
            lpProvider.when(LuckPermsProvider::get).thenReturn(lp);
            proxyStatic.when(ProxyFeatures::getProxyInstance).thenReturn(proxy);

            LuckPermsHook.subscribeLuckPermsHook(feature);

            NodeMutateEvent event = mock(NodeMutateEvent.class);
            PermissionHolder target = mock(PermissionHolder.class);
            when(event.getTarget()).thenReturn(target);
            when(target.getFriendlyName()).thenReturn("Remy");
            captor.getValue().accept(event);
        }

        verify(handler).updatePlayerData(player);
        LuckPermsHook.unsubscribeLuckPermsHook();
        verify(subscription).close();
    }

    @Test
    void subscribeFailureLogsWarningAndDoesNotThrow() {
        HLink feature = mock(HLink.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        when(feature.getLogger()).thenReturn(logger);

        try (MockedStatic<LuckPermsProvider> lpProvider = mockStatic(LuckPermsProvider.class)) {
            lpProvider.when(LuckPermsProvider::get).thenThrow(new RuntimeException("missing"));
            LuckPermsHook.subscribeLuckPermsHook(feature);
        }

        verify(logger).warn(contains("LuckPerms hook unavailable"));
    }
}
