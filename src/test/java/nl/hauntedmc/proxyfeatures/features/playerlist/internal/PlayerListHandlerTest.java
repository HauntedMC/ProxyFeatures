package nl.hauntedmc.proxyfeatures.features.playerlist.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.APIRegistry;
import nl.hauntedmc.proxyfeatures.features.playerlist.PlayerList;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.VanishAPI;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerListHandlerTest {

    private PlayerList feature;
    private ProxyFeatures plugin;
    private ProxyServer proxy;
    private LocalizationHandler localization;
    private LocalizationHandler.MessageBuilder builder;
    private PlayerListHandler handler;

    @BeforeEach
    void setUp() {
        feature = mock(PlayerList.class);
        plugin = mock(ProxyFeatures.class);
        proxy = mock(ProxyServer.class);
        localization = mock(LocalizationHandler.class);
        builder = mock(LocalizationHandler.MessageBuilder.class);
        when(feature.getPlugin()).thenReturn(plugin);
        when(plugin.getProxy()).thenReturn(proxy);
        when(feature.getLocalizationHandler()).thenReturn(localization);
        when(localization.getMessage(anyString())).thenReturn(builder);
        when(builder.with(anyString(), anyString())).thenReturn(builder);
        when(builder.with(anyString(), any(Number.class))).thenReturn(builder);
        when(builder.with(anyString(), any(Component.class))).thenReturn(builder);
        when(builder.forAudience(any())).thenReturn(builder);
        when(builder.build()).thenReturn(Component.text("ok"));
        handler = new PlayerListHandler(feature);
    }

    @AfterEach
    void tearDown() {
        APIRegistry.clear();
    }

    @Test
    void formatPlayerListFiltersVanishedPlayersAndSeparatesStaff() {
        UUID visibleUserId = UUID.randomUUID();
        UUID visibleStaffId = UUID.randomUUID();
        UUID vanishedId = UUID.randomUUID();

        Player audience = player("Audience", UUID.randomUUID(), false);
        Player visibleUser = player("Alice", visibleUserId, false);
        Player visibleStaff = player("Zed", visibleStaffId, true);
        Player vanished = player("Ghost", vanishedId, false);

        VanishAPI vanishApi = mock(VanishAPI.class);
        when(vanishApi.isVanished(vanishedId)).thenReturn(true);
        APIRegistry.register(VanishAPI.class, vanishApi);

        handler.formatPlayerList("survival", List.of(visibleUser, visibleStaff, vanished), audience);

        verify(builder).with("count", "2");
        verify(builder).with("players", "Alice");
        verify(builder).with("players", "Zed");
        verify(builder, never()).with(eq("players"), argThat((String v) -> v.contains("Ghost")));
    }

    @Test
    void formatGlobalListUsesVisibleCountsAndServerOrder() {
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID carlId = UUID.randomUUID();
        UUID ghostId = UUID.randomUUID();

        Player audience = player("Audience", UUID.randomUUID(), false);
        ServerConnection connection = mock(ServerConnection.class);
        ServerInfo audienceServerInfo = new ServerInfo("survival", new java.net.InetSocketAddress("127.0.0.1", 25565));
        when(connection.getServerInfo()).thenReturn(audienceServerInfo);
        when(audience.getCurrentServer()).thenReturn(Optional.of(connection));

        Player alice = player("Alice", aliceId, false);
        Player bob = player("Bob", bobId, false);
        Player carl = player("Carl", carlId, false);
        Player ghost = player("Ghost", ghostId, false);

        VanishAPI vanishApi = mock(VanishAPI.class);
        when(vanishApi.isVanished(ghostId)).thenReturn(true);
        APIRegistry.register(VanishAPI.class, vanishApi);

        RegisteredServer survival = server("survival", List.of(alice, ghost), CompletableFuture.completedFuture(mock(ServerPing.class)));
        CompletableFuture<ServerPing> failedPing = new CompletableFuture<>();
        failedPing.completeExceptionally(new RuntimeException("offline"));
        RegisteredServer lobby = server("lobby", List.of(bob, carl), failedPing);

        when(proxy.getAllPlayers()).thenReturn(List.of(alice, bob, carl, ghost));
        handler.formatGlobalList(List.of(survival, lobby), audience);

        verify(builder).with("count", 3);
        verify(builder).with("online", "2");
        verify(builder).with("online", "1");
        verify(localization).getMessage("playerlist.server_bullet_online");
        verify(localization).getMessage("playerlist.server_bullet_offline");

        InOrder order = inOrder(builder);
        order.verify(builder).with("server", "lobby");
        order.verify(builder, atLeastOnce()).with("server", "survival");
    }

    @Test
    void getPlayersOnServerReturnsEmptyWhenServerMissing() {
        when(proxy.getServer("missing")).thenReturn(Optional.empty());
        assertEquals(List.of(), handler.getPlayersOnServer("missing"));
    }

    private static Player player(String name, UUID id, boolean staff) {
        Player player = mock(Player.class);
        when(player.getUsername()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(id);
        when(player.hasPermission("proxyfeatures.feature.playerlist.staff")).thenReturn(staff);
        return player;
    }

    private static RegisteredServer server(String name, List<Player> players, CompletableFuture<ServerPing> ping) {
        RegisteredServer server = mock(RegisteredServer.class);
        when(server.getServerInfo()).thenReturn(new ServerInfo(name, new java.net.InetSocketAddress("127.0.0.1", 25565)));
        when(server.getPlayersConnected()).thenReturn(players);
        when(server.ping()).thenReturn(ping);
        return server;
    }
}
