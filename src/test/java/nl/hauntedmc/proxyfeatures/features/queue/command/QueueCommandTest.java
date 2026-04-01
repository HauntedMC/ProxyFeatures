package nl.hauntedmc.proxyfeatures.features.queue.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.queue.Queue;
import nl.hauntedmc.proxyfeatures.features.queue.QueueManager;
import nl.hauntedmc.proxyfeatures.features.queue.model.ServerQueue;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueueCommandTest {

    private Queue feature;
    private ProxyFeatures plugin;
    private ProxyServer proxy;
    private QueueManager manager;
    private LocalizationHandler localization;
    private LocalizationHandler.MessageBuilder builder;
    private QueueCommand command;

    @BeforeEach
    void setUp() {
        feature = mock(Queue.class);
        plugin = mock(ProxyFeatures.class);
        proxy = mock(ProxyServer.class);
        manager = mock(QueueManager.class);
        localization = mock(LocalizationHandler.class);
        builder = mock(LocalizationHandler.MessageBuilder.class);

        when(feature.getPlugin()).thenReturn(plugin);
        when(plugin.getProxy()).thenReturn(proxy);
        when(feature.getLocalizationHandler()).thenReturn(localization);
        when(localization.getMessage(anyString())).thenReturn(builder);
        when(builder.forAudience(any())).thenReturn(builder);
        when(builder.with(anyString(), anyString())).thenReturn(builder);
        when(builder.with(anyString(), any(Number.class))).thenReturn(builder);
        when(builder.with(anyString(), any(Component.class))).thenReturn(builder);
        when(builder.build()).thenReturn(Component.text("ok"));

        command = new QueueCommand(feature, manager);
    }

    @Test
    void metadataAndPermissionNodeAreCorrect() {
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("proxyfeatures.feature.queue.command")).thenReturn(true);

        assertEquals("queue", command.getName());
        assertArrayEquals(new String[]{"q"}, command.getAliases());
        assertTrue(command.hasPermission(invocation(source)));
    }

    @Test
    void executeWithoutArgsRejectsNonPlayers() {
        CommandSource source = mock(CommandSource.class);

        command.execute(invocation(source));

        verify(localization).getMessage("queue.cmd_notPlayer");
    }

    @Test
    void executeWithoutArgsShowsNoneWhenPlayerIsNotQueued() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(manager.findQueueOf(id)).thenReturn(Optional.empty());

        command.execute(invocation(player));

        verify(localization).getMessage("queue.status.none");
    }

    @Test
    void executeWithoutArgsShowsNotEnabledWhenQueueIsMissing() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(manager.findQueueOf(id)).thenReturn(Optional.of("survival"));
        when(manager.getQueue("survival")).thenReturn(Optional.empty());

        command.execute(invocation(player));

        verify(localization).getMessage("queue.status.not_enabled");
        verify(builder).with("server", "survival");
    }

    @Test
    void executeWithoutArgsShowsQueuePosition() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        ServerQueue queue = mock(ServerQueue.class);
        when(player.getUniqueId()).thenReturn(id);
        when(manager.findQueueOf(id)).thenReturn(Optional.of("survival"));
        when(manager.getQueue("survival")).thenReturn(Optional.of(queue));
        when(queue.positionOf(id)).thenReturn(Optional.of(2));

        command.execute(invocation(player));

        verify(localization).getMessage("queue.status.header");
        verify(localization).getMessage("queue.status.line");
        verify(builder).with("position", 3);
    }

    @Test
    void leaveSubcommandClearsReservationAndConfirms() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        ServerQueue queue = mock(ServerQueue.class);
        when(player.getUniqueId()).thenReturn(id);
        when(manager.findQueueOf(id)).thenReturn(Optional.of("survival"));
        when(manager.getQueue("survival")).thenReturn(Optional.of(queue));

        command.execute(invocation(player, "leave"));

        verify(queue).clearReservation(id);
        verify(localization).getMessage("queue.cmd.leave.done");
    }

    @Test
    void infoSubcommandHandlesPermissionAndEmptyQueue() {
        CommandSource source = mock(CommandSource.class);
        when(source.hasPermission("proxyfeatures.feature.queue.command.info")).thenReturn(false, true);

        command.execute(invocation(source, "info", "survival"));
        verify(localization).getMessage("queue.cmd.no_permission");

        ServerQueue queue = mock(ServerQueue.class);
        when(manager.getQueue("survival")).thenReturn(Optional.of(queue));
        command.execute(invocation(source, "info", "survival"));

        verify(localization).getMessage("queue.cmd.info.header");
        verify(localization).getMessage("queue.cmd.info.empty");
    }

    @Test
    void executeUnknownSubcommandShowsUsage() {
        CommandSource source = mock(CommandSource.class);

        command.execute(invocation(source, "wat"));

        verify(localization).getMessage("queue.cmd.usage");
    }

    @Test
    void suggestAsyncCoversBaseAndInfoServerSuggestions() {
        CommandSource source = mock(CommandSource.class);

        assertEquals(List.of("leave", "info"), command.suggestAsync(invocation(source)).join());
        assertEquals(List.of("leave"), command.suggestAsync(invocation(source, "le")).join());

        RegisteredServer survival = mock(RegisteredServer.class);
        RegisteredServer lobby = mock(RegisteredServer.class);
        when(survival.getServerInfo()).thenReturn(new ServerInfo("survival", new InetSocketAddress("127.0.0.1", 25565)));
        when(lobby.getServerInfo()).thenReturn(new ServerInfo("lobby", new InetSocketAddress("127.0.0.1", 25566)));
        when(proxy.getAllServers()).thenReturn(List.of(survival, lobby));
        when(manager.isServerQueued("survival")).thenReturn(true);
        when(manager.isServerQueued("lobby")).thenReturn(false);

        List<String> out = command.suggestAsync(invocation(source, "info", "")).join();
        assertEquals(List.of("survival"), out);
    }

    private static SimpleCommand.Invocation invocation(CommandSource source, String... args) {
        SimpleCommand.Invocation invocation = mock(SimpleCommand.Invocation.class);
        when(invocation.source()).thenReturn(source);
        when(invocation.arguments()).thenReturn(args);
        return invocation;
    }
}
