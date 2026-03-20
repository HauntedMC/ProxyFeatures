package nl.hauntedmc.proxyfeatures.features.commandrelay;

import net.kyori.adventure.text.Component;
import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.MessageRegistry;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.commandrelay.internal.EventBusHandler;
import nl.hauntedmc.proxyfeatures.features.commandrelay.internal.messaging.CommandRelayMessage;
import nl.hauntedmc.proxyfeatures.features.commandrelay.meta.Meta;

import java.util.List;
import java.util.Optional;

public class CommandRelay extends VelocityBaseFeature<Meta> {

    private EventBusHandler eventBusHandler;

    public CommandRelay(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("listening", false);
        defaults.put("command_whitelist", List.of());
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        // Init Redis messaging
        getLifecycleManager()
                .getDataManager()
                .initDataProvider(getFeatureName());

        Optional<DatabaseProvider> opt = getLifecycleManager()
                .getDataManager()
                .registerConnection(
                        "redis",
                        DatabaseType.REDIS_MESSAGING,
                        "default"
                );

        if (opt.isEmpty()) {
            return;
        }

        // Obtain the Redis bus
        DatabaseProvider dbp = opt.get();
        MessagingDataAccess redisBus;
        try {
            redisBus = (MessagingDataAccess) dbp.getDataAccess();
        } catch (ClassCastException e) {
            return;
        }

        // Register the message type
        MessageRegistry.register("commandrelay", CommandRelayMessage.class);

        // Create the handler
        this.eventBusHandler = new EventBusHandler(this, redisBus);

        // Fetch settings
        boolean listen = getConfigHandler().get("listening", Boolean.class, false);

        // If listening, subscribe to incoming commands for this server
        if (listen) {
            String channel = "proxy.commandrelay.command";
            eventBusHandler.subscribe(channel);
            getLogger().info(Component.text("CommandRelay: listening on Redis channel “" + channel + "”"));
        }

    }

    @Override
    public void disable() {
        if (eventBusHandler != null) {
            eventBusHandler.disable();
        }
    }

    public EventBusHandler getEventBusHandler() {
        return eventBusHandler;
    }
}
