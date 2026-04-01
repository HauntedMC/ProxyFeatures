package nl.hauntedmc.proxyfeatures.features.commandrelay.internal;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.commandrelay.CommandRelay;
import nl.hauntedmc.proxyfeatures.features.commandrelay.internal.messaging.CommandRelayMessage;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.proxyfeatures.framework.lifecycle.FeatureTaskManager;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EventBusHandlerTest {

    @Test
    void subscribeAndHandleIncomingDispatchesWhitelistedCommand() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        FeatureTaskManager tasks = feature.getLifecycleManager().getTaskManager();

        ProxyServer proxy = feature.getPlugin().getProxy();
        ConsoleCommandSource console = mock(ConsoleCommandSource.class);
        CommandManager commandManager = mock(CommandManager.class);
        when(proxy.getConsoleCommandSource()).thenReturn(console);
        when(proxy.getCommandManager()).thenReturn(commandManager);
        when(commandManager.executeAsync(console, "say hello"))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(tasks.scheduleTask(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return mock(ScheduledTask.class);
        });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<CommandRelayMessage>> captor = ArgumentCaptor.forClass(Consumer.class);
        when(redis.subscribe(eq("proxy.relay"), eq(CommandRelayMessage.class), captor.capture()))
                .thenReturn(mock(Subscription.class));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribe("proxy.relay");
        captor.getValue().accept(new CommandRelayMessage("/say hello", "hub-1"));

        verify(commandManager).executeAsync(console, "say hello");
        verify(feature.getLogger()).info(any(Component.class));
    }

    @Test
    void incomingForbiddenCommandIsIgnoredAndWarned() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        FeatureTaskManager tasks = feature.getLifecycleManager().getTaskManager();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<CommandRelayMessage>> captor = ArgumentCaptor.forClass(Consumer.class);
        when(redis.subscribe(eq("proxy.relay"), eq(CommandRelayMessage.class), captor.capture()))
                .thenReturn(mock(Subscription.class));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribe("proxy.relay");
        captor.getValue().accept(new CommandRelayMessage("/stop now", "hub-2"));

        verify(tasks, never()).scheduleTask(any(Runnable.class));
        verify(feature.getLogger()).warn(any(Component.class));
    }

    @Test
    void incomingMessageMissingRequiredFieldsIsIgnored() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        FeatureTaskManager tasks = feature.getLifecycleManager().getTaskManager();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<CommandRelayMessage>> captor = ArgumentCaptor.forClass(Consumer.class);
        when(redis.subscribe(eq("proxy.relay"), eq(CommandRelayMessage.class), captor.capture()))
                .thenReturn(mock(Subscription.class));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribe("proxy.relay");
        captor.getValue().accept(new CommandRelayMessage(null, "hub-2"));
        captor.getValue().accept(new CommandRelayMessage("say hi", null));

        verify(tasks, never()).scheduleTask(any(Runnable.class));
    }

    @Test
    void dispatchFailureIsLogged() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        FeatureTaskManager tasks = feature.getLifecycleManager().getTaskManager();

        ProxyServer proxy = feature.getPlugin().getProxy();
        ConsoleCommandSource console = mock(ConsoleCommandSource.class);
        CommandManager commandManager = mock(CommandManager.class);
        when(proxy.getConsoleCommandSource()).thenReturn(console);
        when(proxy.getCommandManager()).thenReturn(commandManager);
        when(commandManager.executeAsync(console, "say hello"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("dispatch-failed")));
        when(tasks.scheduleTask(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return mock(ScheduledTask.class);
        });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<CommandRelayMessage>> captor = ArgumentCaptor.forClass(Consumer.class);
        when(redis.subscribe(eq("proxy.relay"), eq(CommandRelayMessage.class), captor.capture()))
                .thenReturn(mock(Subscription.class));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribe("proxy.relay");
        captor.getValue().accept(new CommandRelayMessage("/say hello", "hub-1"));

        verify(feature.getLogger()).error(any(Component.class));
    }

    @Test
    void disableUnsubscribesOnce() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        Subscription subscription = mock(Subscription.class);
        when(redis.subscribe(eq("proxy.relay"), eq(CommandRelayMessage.class), any()))
                .thenReturn(subscription);

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribe("proxy.relay");
        handler.disable();
        handler.disable();

        verify(subscription, times(1)).unsubscribe();
    }

    @Test
    void subscribeFailureIsLogged() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        when(redis.subscribe(eq("proxy.relay"), eq(CommandRelayMessage.class), any()))
                .thenThrow(new RuntimeException("boom"));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.subscribe("proxy.relay");

        verify(feature.getLogger()).error(any(Component.class));
    }

    @Test
    void publishFailureIsLogged() {
        MessagingDataAccess redis = mock(MessagingDataAccess.class);
        CommandRelay feature = featureWithWhitelist(List.of("say"));
        when(redis.publish(eq("proxy.relay"), any(CommandRelayMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("down")));

        EventBusHandler handler = new EventBusHandler(feature, redis);
        handler.publish("proxy.relay", "say hi");

        ArgumentCaptor<CommandRelayMessage> captor = ArgumentCaptor.forClass(CommandRelayMessage.class);
        verify(redis).publish(eq("proxy.relay"), captor.capture());
        assertEquals("say hi", captor.getValue().getCommand());
        assertEquals("proxy", captor.getValue().getOriginServer());
        verify(feature.getLogger()).error(any(Component.class));
    }

    private static CommandRelay featureWithWhitelist(List<String> whitelist) {
        CommandRelay feature = mock(CommandRelay.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        FeatureLifecycleManager lifecycle = mock(FeatureLifecycleManager.class);
        FeatureTaskManager tasks = mock(FeatureTaskManager.class);
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        ProxyServer proxy = mock(ProxyServer.class);

        when(feature.getConfigHandler()).thenReturn(cfg);
        when(feature.getLogger()).thenReturn(logger);
        when(feature.getLifecycleManager()).thenReturn(lifecycle);
        when(feature.getPlugin()).thenReturn(plugin);
        when(lifecycle.getTaskManager()).thenReturn(tasks);
        when(cfg.get("command_whitelist")).thenReturn(whitelist);
        when(plugin.getProxy()).thenReturn(proxy);

        return feature;
    }
}
