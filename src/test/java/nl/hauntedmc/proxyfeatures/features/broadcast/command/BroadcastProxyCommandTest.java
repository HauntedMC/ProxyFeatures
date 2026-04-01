package nl.hauntedmc.proxyfeatures.features.broadcast.command;

import com.mojang.brigadier.CommandDispatcher;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.features.broadcast.Broadcast;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BroadcastProxyCommandTest {

    private Broadcast feature;
    private FeatureConfigHandler config;
    private LocalizationHandler localization;
    private LocalizationHandler.MessageBuilder builder;
    private ProxyServer proxy;
    private ProxyFeatures plugin;

    @BeforeEach
    void setUp() {
        feature = mock(Broadcast.class);
        config = mock(FeatureConfigHandler.class);
        localization = mock(LocalizationHandler.class);
        builder = mock(LocalizationHandler.MessageBuilder.class);
        proxy = mock(ProxyServer.class);
        plugin = mock(ProxyFeatures.class);

        when(feature.getConfigHandler()).thenReturn(config);
        when(feature.getLocalizationHandler()).thenReturn(localization);
        when(feature.getPlugin()).thenReturn(plugin);
        when(plugin.getProxy()).thenReturn(proxy);
        when(localization.getMessage(anyString())).thenReturn(builder);
        when(builder.with(anyString(), anyString())).thenReturn(builder);
        when(builder.with(anyString(), any(Number.class))).thenReturn(builder);
        when(builder.with(anyString(), any(Component.class))).thenReturn(builder);
        when(builder.forAudience(any())).thenReturn(builder);
        when(builder.build()).thenReturn(Component.text("ok"));
    }

    @Test
    void titleModeUsesClampedTimingsFromConfig() throws Exception {
        when(config.node()).thenReturn(ConfigNode.ofRaw(Map.of(
                "title_fade_in", -10,
                "title_stay", 40,
                "title_fade_out", -5
        ), "root"));
        CommandSource source = mock(CommandSource.class);
        Player target = mock(Player.class);
        when(source.hasPermission("proxyfeatures.feature.broadcast.command.broadcastproxy")).thenReturn(true);
        when(proxy.getAllPlayers()).thenReturn(List.of(target));

        BroadcastProxyCommand command = new BroadcastProxyCommand(feature);
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.buildTree());
        dispatcher.execute("broadcastproxy title Welcome", source);

        ArgumentCaptor<Title> captor = ArgumentCaptor.forClass(Title.class);
        verify(target).showTitle(captor.capture());
        Title.Times times = captor.getValue().times();
        assertEquals(Duration.ZERO, times.fadeIn());
        assertEquals(Duration.ofMillis(40L * 50L), times.stay());
        assertEquals(Duration.ZERO, times.fadeOut());
    }

    @Test
    void rootCommandWithoutModeShowsUsage() throws Exception {
        when(config.node()).thenReturn(ConfigNode.ofRaw(Map.of(), "root"));
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("proxyfeatures.feature.broadcast.command.broadcastproxy")).thenReturn(true);

        BroadcastProxyCommand command = new BroadcastProxyCommand(feature);
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.buildTree());
        dispatcher.execute("broadcastproxy", source);

        verify(localization).getMessage("broadcast.usage");
    }

    @Test
    void chatModeBroadcastsToAllPlayersAndAcknowledgesSender() throws Exception {
        when(config.node()).thenReturn(ConfigNode.ofRaw(Map.of(), "root"));
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("proxyfeatures.feature.broadcast.command.broadcastproxy")).thenReturn(true);
        Player first = mock(Player.class);
        Player second = mock(Player.class);
        when(proxy.getAllPlayers()).thenReturn(List.of(first, second));

        BroadcastProxyCommand command = new BroadcastProxyCommand(feature);
        CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.buildTree());
        dispatcher.execute("broadcastproxy chat hello", source);

        verify(first).sendMessage(any(Component.class));
        verify(second).sendMessage(any(Component.class));
        verify(localization).getMessage("broadcast.sent");
    }
}
