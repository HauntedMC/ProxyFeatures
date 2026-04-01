package nl.hauntedmc.proxyfeatures.features.proxyinfo.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.proxyinfo.ProxyInfo;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProxyInfoCommandTest {

    private ProxyInfo feature;
    private ProxyFeatures plugin;
    private ProxyServer proxy;
    private LocalizationHandler localization;
    private LocalizationHandler.MessageBuilder builder;

    @BeforeEach
    void setUp() {
        feature = mock(ProxyInfo.class);
        plugin = mock(ProxyFeatures.class);
        proxy = mock(ProxyServer.class, RETURNS_DEEP_STUBS);
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
    }

    @Test
    void executeWithArgumentsShowsUsage() {
        CommandSource source = mock(CommandSource.class);
        ProxyInfoCommand command = new ProxyInfoCommand(feature);
        command.execute(invocation(source, "unexpected"));

        verify(localization).getMessage("proxyinfo.cmd_usage");
    }

    @Test
    void executeWithoutArgumentsSendsHeaderAndEntries() {
        CommandSource source = mock(CommandSource.class);

        when(proxy.getVersion().getVersion()).thenReturn("3.4.0");
        when(proxy.getBoundAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 25577));
        when(proxy.getAllServers()).thenReturn(List.of(mock(RegisteredServer.class), mock(RegisteredServer.class)));
        when(proxy.getAllPlayers()).thenReturn(List.of(mock(Player.class), mock(Player.class), mock(Player.class)));

        ProxyInfoCommand command = new ProxyInfoCommand(feature);
        command.execute(invocation(source));

        verify(localization).getMessage("proxyinfo.cmd_header");
        verify(localization, atLeast(8)).getMessage("proxyinfo.cmd_entry");
        verify(builder, atLeastOnce()).with("setting", "Connected Clients");
        verify(builder, atLeastOnce()).with("value", "3");
    }

    @Test
    void hasPermissionChecksExpectedNode() {
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("proxyfeatures.feature.proxyinfo.command.proxyinfo")).thenReturn(true);
        ProxyInfoCommand command = new ProxyInfoCommand(feature);
        assertTrue(command.hasPermission(invocation(source)));

        when(source.hasPermission("proxyfeatures.feature.proxyinfo.command.proxyinfo")).thenReturn(false);
        assertFalse(command.hasPermission(invocation(source)));
    }

    private static SimpleCommand.Invocation invocation(CommandSource source, String... args) {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(args);
        return invocation;
    }
}
