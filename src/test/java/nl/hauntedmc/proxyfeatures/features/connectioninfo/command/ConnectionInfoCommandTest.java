package nl.hauntedmc.proxyfeatures.features.connectioninfo.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.connectioninfo.ConnectionInfo;
import nl.hauntedmc.proxyfeatures.features.connectioninfo.internal.SessionHandler;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionInfoCommandTest {

    private ConnectionInfo feature;
    private ProxyFeatures plugin;
    private ProxyServer proxy;
    private SessionHandler sessionHandler;
    private LocalizationHandler localization;
    private LocalizationHandler.MessageBuilder builder;
    private ConnectionInfoCommand command;

    @BeforeEach
    void setUp() {
        feature = mock(ConnectionInfo.class);
        plugin = mock(ProxyFeatures.class);
        proxy = mock(ProxyServer.class);
        sessionHandler = mock(SessionHandler.class);
        localization = mock(LocalizationHandler.class);
        builder = mock(LocalizationHandler.MessageBuilder.class);

        when(feature.getPlugin()).thenReturn(plugin);
        when(feature.getSessionHandler()).thenReturn(sessionHandler);
        when(feature.getLocalizationHandler()).thenReturn(localization);
        when(plugin.getProxy()).thenReturn(proxy);

        when(localization.getMessage(anyString())).thenReturn(builder);
        when(builder.forAudience(any())).thenReturn(builder);
        when(builder.with(anyString(), anyString())).thenReturn(builder);
        when(builder.with(anyString(), any(Number.class))).thenReturn(builder);
        when(builder.with(anyString(), any(Component.class))).thenReturn(builder);
        when(builder.build()).thenReturn(Component.text("ok"));

        command = new ConnectionInfoCommand(feature);
    }

    @Test
    void metadataAndPermissionNodesAreCorrect() {
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("proxyfeatures.feature.connectioninfo.command.connectioninfo")).thenReturn(true);
        when(source.hasPermission("proxyfeatures.feature.connectioninfo.command.connectioninfo.other")).thenReturn(true);

        assertEquals("connectioninfo", command.getName());
        assertTrue(command.hasPermission(invocation(source)));
        assertTrue(command.hasPermission(invocation(source, "Remy")));
    }

    @Test
    void executeShowsUsageWhenArgumentCountIsInvalid() {
        CommandSource source = mock(CommandSource.class);

        command.execute(invocation(source, "one", "two"));

        verify(localization).getMessage("connectioninfo.cmd_usage");
    }

    @Test
    void executeWithoutArgsRequiresPlayerSource() {
        CommandSource source = mock(CommandSource.class);

        command.execute(invocation(source));

        verify(localization).getMessage("connectioninfo.cmd_usage");
    }

    @Test
    void executeShowsPlayerNotFoundWhenTargetIsMissing() {
        CommandSource source = mock(CommandSource.class);
        when(proxy.getPlayer("Remy")).thenReturn(Optional.empty());

        command.execute(invocation(source, "Remy"));

        verify(localization).getMessage("connectioninfo.cmd_playerNotFound");
        verify(builder).with("player", "Remy");
    }

    @Test
    void executeShowsConnectionDetailsForResolvedTarget() {
        CommandSource source = mock(CommandSource.class);
        Player target = mock(Player.class);
        UUID id = UUID.randomUUID();

        ProtocolVersion version = mock(ProtocolVersion.class);
        when(version.name()).thenReturn("TEST");
        when(version.getMostRecentSupportedVersion()).thenReturn("765");

        when(target.getUniqueId()).thenReturn(id);
        when(target.getUsername()).thenReturn("Remy");
        when(target.getPing()).thenReturn(42L);
        when(target.getProtocolVersion()).thenReturn(version);
        when(target.getRemoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 25565));
        when(target.getVirtualHost()).thenReturn(Optional.of(InetSocketAddress.createUnresolved("play.hauntedmc.nl", 25577)));
        when(sessionHandler.getJoinTime(id)).thenReturn(Optional.of(Instant.now().minusSeconds(5)));
        when(proxy.getPlayer("Remy")).thenReturn(Optional.of(target));

        command.execute(invocation(source, "Remy"));

        verify(localization).getMessage("connectioninfo.cmd_header");
        verify(localization, times(5)).getMessage("connectioninfo.cmd_entry");
        verify(builder).with("subject", " van Remy");
        verify(source, times(6)).sendMessage(any(Component.class));
    }

    @Test
    void suggestAsyncReturnsAllNamesOrFilteredMatches() {
        Player remy = mock(Player.class);
        when(remy.getUsername()).thenReturn("Remy");
        Player alex = mock(Player.class);
        when(alex.getUsername()).thenReturn("Alex");
        Player rex = mock(Player.class);
        when(rex.getUsername()).thenReturn("Rex");
        when(proxy.getAllPlayers()).thenReturn(List.of(remy, alex, rex));

        List<String> all = command.suggestAsync(invocation(mock(CommandSource.class))).join();
        List<String> filtered = command.suggestAsync(invocation(mock(CommandSource.class), "re")).join();

        assertEquals(List.of("Remy", "Alex", "Rex"), all);
        assertEquals(List.of("Remy", "Rex"), filtered);
    }

    private static SimpleCommand.Invocation invocation(CommandSource source, String... args) {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(args);
        return invocation;
    }
}
