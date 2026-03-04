package nl.hauntedmc.proxyfeatures.features.commandrelay.internal;

import com.velocitypowered.api.proxy.ConsoleCommandSource;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.Subscription;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.util.type.CastUtils;
import nl.hauntedmc.proxyfeatures.features.commandrelay.CommandRelay;
import nl.hauntedmc.proxyfeatures.features.commandrelay.internal.messaging.CommandRelayMessage;

import java.util.List;

public class EventBusHandler {

    private final MessagingDataAccess redisBus;
    private final CommandRelay feature;
    private Subscription subscription;

    public EventBusHandler(CommandRelay feature, MessagingDataAccess redisBus) {
        this.feature = feature;
        this.redisBus = redisBus;
    }

    /**
     * Subscribe to the given Redis channel and handle incoming CommandRelayMessage.
     */
    public void subscribe(String channel) {
        try {
            this.subscription = redisBus.subscribe(
                    channel,
                    CommandRelayMessage.class,
                    this::handleIncoming
            );
        } catch (Exception ex) {
            feature.getLogger().error(Component.text("CommandRelay: failed to subscribe to “" + channel + "”"));
        }
    }

    private void handleIncoming(CommandRelayMessage msg) {
        if (msg.getCommand() == null || msg.getOriginServer() == null) {
            return;
        }

        String origin = msg.getOriginServer();
        String full = msg.getCommand().trim();
        if (full.startsWith("/")) {
            full = full.substring(1);
        }
        String main = full.contains(" ")
                ? full.substring(0, full.indexOf(' '))
                : full;

        // Validate against whitelist
        List<String> whitelist =
                CastUtils.safeCastToList(
                        feature.getConfigHandler().get("command_whitelist"),
                        String.class
                );

        if (!whitelist.stream().map(String::toLowerCase).toList()
                .contains(main.toLowerCase())) {
            feature.getLogger().warn(Component.text("CommandRelay: received forbidden “" + main + "” from " + origin + " – ignoring"));
            return;
        }

        final String sendingCommand = full;
        // Execute the command in console in sync thread
        feature.getLifecycleManager().getTaskManager().scheduleTask(() -> {
            ConsoleCommandSource console = ProxyFeatures.getProxyInstance().getConsoleCommandSource();
            ProxyFeatures.getProxyInstance().getCommandManager()
                    .executeAsync(console, sendingCommand)
                    .whenComplete((success, ex) -> {
                        if (ex != null) {
                            feature.getLogger().error(Component.text("CommandRelay: error dispatching “" + sendingCommand + "” from " + origin));
                        } else {
                            feature.getLogger().info(Component.text("CommandRelay: dispatched “/" + sendingCommand + "” from " + origin + ": success=" + success));
                        }
                    });
        });

    }

    /**
     * Unsubscribe when feature is disabled.
     */
    public void disable() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    /**
     * Publish a command to a remote server, attaching this server as origin.
     */
    public void publish(String channel, String command) {
        redisBus.publish(channel, new CommandRelayMessage(command, "proxy"))
                .exceptionally(ex -> {
                    feature.getLogger().error(Component.text("CommandRelay: failed to publish to “" + channel + "”"));
                    return null;
                });
    }
}
