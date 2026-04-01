package nl.hauntedmc.proxyfeatures.features.hub.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.hub.Hub;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HubCommandTest {

    private Hub feature;
    private ProxyFeatures plugin;
    private ProxyServer proxy;
    private LocalizationHandler localization;
    private LocalizationHandler.MessageBuilder builder;
    private HubCommand command;

    @BeforeEach
    void setUp() {
        feature = mock(Hub.class);
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

        command = new HubCommand(feature);
    }

    @Test
    void executeRejectsNonPlayerSource() {
        CommandSource source = mock(CommandSource.class);

        command.execute(invocation(source));

        verify(localization).getMessage("general.player_command");
    }

    @Test
    void executeReportsUnavailableWhenLobbyMissing() {
        Player player = mock(Player.class);
        when(proxy.getServer("lobby")).thenReturn(Optional.empty());

        command.execute(invocation(player));

        verify(localization).getMessage("hub.not_available");
    }

    @Test
    void executeReportsAlreadyConnectedWhenPlayerIsOnLobby() {
        Player player = mock(Player.class);
        RegisteredServer lobby = mock(RegisteredServer.class);
        when(proxy.getServer("lobby")).thenReturn(Optional.of(lobby));
        when(lobby.getPlayersConnected()).thenReturn(List.of(player));

        command.execute(invocation(player));

        verify(localization).getMessage("hub.already_connected");
    }

    @Test
    void executeReportsOfflineWhenLobbyPingFails() {
        Player player = mock(Player.class);
        RegisteredServer lobby = mock(RegisteredServer.class);
        when(proxy.getServer("lobby")).thenReturn(Optional.of(lobby));
        when(lobby.getPlayersConnected()).thenReturn(List.of());
        CompletableFuture<ServerPing> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("offline"));
        when(lobby.ping()).thenReturn(failed);

        command.execute(invocation(player));

        verify(localization).getMessage("hub.offline");
    }

    @Test
    void executeConnectsAndSendsSuccessWhenLobbyIsReachable() {
        Player player = mock(Player.class);
        RegisteredServer lobby = mock(RegisteredServer.class);
        ConnectionRequestBuilder request = mock(ConnectionRequestBuilder.class);
        ConnectionRequestBuilder.Result result = mock(ConnectionRequestBuilder.Result.class);

        when(proxy.getServer("lobby")).thenReturn(Optional.of(lobby));
        when(lobby.getPlayersConnected()).thenReturn(List.of());
        when(lobby.ping()).thenReturn(CompletableFuture.completedFuture(mock(ServerPing.class)));
        when(player.createConnectionRequest(lobby)).thenReturn(request);
        when(request.connect()).thenReturn(CompletableFuture.completedFuture(result));
        when(result.isSuccessful()).thenReturn(true);

        command.execute(invocation(player));

        verify(localization).getMessage("hub.connection_success");
    }

    @Test
    void hasPermissionChecksExpectedNode() {
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("proxyfeatures.feature.hub.use")).thenReturn(true);

        assertTrue(command.hasPermission(invocation(source)));
    }

    private static SimpleCommand.Invocation invocation(CommandSource source, String... args) {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(args);
        return invocation;
    }
}
