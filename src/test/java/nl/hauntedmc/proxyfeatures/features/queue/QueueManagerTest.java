package nl.hauntedmc.proxyfeatures.features.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.queue.model.EnqueueDecision;
import nl.hauntedmc.proxyfeatures.features.queue.model.ServerQueue;
import nl.hauntedmc.proxyfeatures.features.queue.model.ServerStatus;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QueueManagerTest {

    private QueueManager manager;

    @BeforeEach
    void setUp() {
        Queue feature = mock(Queue.class);
        ProxyServer proxy = mock(ProxyServer.class);
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        Logger logger = mock(Logger.class);
        FeatureConfigHandler config = mock(FeatureConfigHandler.class);
        LocalizationHandler localization = mock(LocalizationHandler.class);
        LocalizationHandler.MessageBuilder builder = mock(LocalizationHandler.MessageBuilder.class);

        when(feature.getPlugin()).thenReturn(plugin);
        when(feature.getConfigHandler()).thenReturn(config);
        when(feature.getLocalizationHandler()).thenReturn(localization);
        when(plugin.getProxy()).thenReturn(proxy);

        when(config.get("grace-seconds", Integer.class, 60)).thenReturn(60);
        when(config.get("ping-timeout-seconds", Integer.class, 3)).thenReturn(3);
        when(config.get("ping-timeout-warn-cooldown-seconds", Integer.class, 60)).thenReturn(60);
        when(config.getList("servers-whitelist", String.class, List.of()))
                .thenReturn(List.of("survival", "lobby"));

        when(localization.getMessage(anyString())).thenReturn(builder);
        when(builder.with(anyString(), anyString())).thenReturn(builder);
        when(builder.with(anyString(), any(Number.class))).thenReturn(builder);
        when(builder.with(anyString(), any(Component.class))).thenReturn(builder);
        when(builder.forAudience(any())).thenReturn(builder);
        when(builder.build()).thenReturn(Component.text("ok"));

        this.manager = new QueueManager(feature, logger);
    }

    @Test
    void handlePreConnectAllowsUnknownTarget() {
        Player player = player(UUID.randomUUID(), false);

        EnqueueDecision decision = manager.handlePreConnect(player, "unknown");

        assertEquals(EnqueueDecision.ALLOW, decision);
    }

    @Test
    void handlePreConnectAllowsBypassAndConsumesAdvanceTicket() {
        UUID id = UUID.randomUUID();
        Player bypassed = player(id, true);
        setStatus("survival", ServerStatus.online(100, 100));

        assertEquals(EnqueueDecision.ALLOW_BYPASS, manager.handlePreConnect(bypassed, "survival"));

        Player normal = player(id, false);
        manager.grantAdvanceTicket(id, "survival");
        assertEquals(EnqueueDecision.ALLOW, manager.handlePreConnect(normal, "survival"));
        assertFalse(manager.consumeAdvanceTicket(id, "survival"));
    }

    @Test
    void handlePreConnectQueuesPlayerOnlyOnceWhenServerIsFull() {
        UUID id = UUID.randomUUID();
        Player player = player(id, false);
        setStatus("survival", ServerStatus.online(80, 80));

        assertEquals(EnqueueDecision.DENY_QUEUED, manager.handlePreConnect(player, "survival"));
        assertEquals(EnqueueDecision.DENY_QUEUED, manager.handlePreConnect(player, "survival"));

        ServerQueue queue = manager.getQueue("survival").orElseThrow();
        assertTrue(queue.contains(id));
        assertEquals(id, queue.pollNextConnectable().playerId());
        assertNull(queue.pollNextConnectable());
    }

    @Test
    void handlePreConnectMovesPlayerBetweenQueues() {
        UUID id = UUID.randomUUID();
        Player player = player(id, false);
        setStatus("lobby", ServerStatus.online(60, 60));
        setStatus("survival", ServerStatus.online(60, 60));

        assertEquals(EnqueueDecision.DENY_QUEUED, manager.handlePreConnect(player, "lobby"));
        assertTrue(manager.getQueue("lobby").orElseThrow().contains(id));

        assertEquals(EnqueueDecision.DENY_QUEUED, manager.handlePreConnect(player, "survival"));
        assertFalse(manager.getQueue("lobby").orElseThrow().contains(id));
        assertTrue(manager.getQueue("survival").orElseThrow().contains(id));
    }

    @Test
    void handlePreConnectAllowsWhenCapacityUnknownOrAvailable() {
        UUID id = UUID.randomUUID();
        Player player = player(id, false);

        assertEquals(EnqueueDecision.ALLOW, manager.handlePreConnect(player, "survival"));

        setStatus("survival", ServerStatus.online(40, 100));
        assertEquals(EnqueueDecision.ALLOW, manager.handlePreConnect(player, "survival"));
    }

    @Test
    void disconnectAndPostConnectClearTransientState() {
        UUID id = UUID.randomUUID();
        Player player = player(id, false);
        setStatus("survival", ServerStatus.online(60, 60));
        manager.handlePreConnect(player, "survival");
        assertTrue(manager.getQueue("survival").orElseThrow().contains(id));

        manager.grantAdvanceTicket(id, "survival");
        manager.onDisconnect(id);
        assertFalse(manager.consumeAdvanceTicket(id, "survival"));

        manager.onPostConnect(player, "survival");
        assertFalse(manager.getQueue("survival").orElseThrow().contains(id));
    }

    private void setStatus(String server, ServerStatus status) {
        manager.setStatus(server, status);
    }

    private static Player player(UUID id, boolean bypass) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.hasPermission(anyString())).thenReturn(false);
        if (bypass) {
            when(player.hasPermission("proxyfeatures.feature.queue.bypass")).thenReturn(true);
        }
        return player;
    }

}
