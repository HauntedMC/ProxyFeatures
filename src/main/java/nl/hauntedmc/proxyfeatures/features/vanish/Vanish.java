package nl.hauntedmc.proxyfeatures.features.vanish;

import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.dataprovider.database.messaging.api.MessageRegistry;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.APIRegistry;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.VanishAPI;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.VanishRegistry;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.messaging.EventBusHandler;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.messaging.VanishStateMessage;
import nl.hauntedmc.proxyfeatures.features.vanish.listener.ConnectListener;
import nl.hauntedmc.proxyfeatures.features.vanish.meta.Meta;

import java.util.Optional;

public class Vanish extends VelocityBaseFeature<Meta> {

    private VanishRegistry vanishRegistry;
    private EventBusHandler eventBusHandler;
    private VanishAPI api;

    public Vanish(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        // Init provider scope
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());

        // Prepare registry & API
        this.vanishRegistry = new VanishRegistry(this);
        this.api = new VanishAPI(this);

        APIRegistry.register(VanishAPI.class, this.api);

        // Optional Redis setup
        Optional<DatabaseProvider> opt = getLifecycleManager()
                .getDataManager()
                .registerConnection("redis", DatabaseType.REDIS_MESSAGING, "default");

        if (opt.isEmpty()) {
            getLogger().warn("Redis messaging connection 'redis' not available. Vanish feature will still run but won't receive updates.");
        } else {
            MessagingDataAccess redisBus;
            try {
                redisBus = (MessagingDataAccess) opt.get().getDataAccess();
            } catch (ClassCastException e) {
                getLogger().warn("Registered 'redis' connection is not a messaging provider; skipping vanish Redis subscription.");
                redisBus = null;
            }

            if (redisBus != null) {
                MessageRegistry.register("vanish_update", VanishStateMessage.class);
                this.eventBusHandler = new EventBusHandler(this, redisBus);
                // Subscribe to the bukkit-side channel
                eventBusHandler.subscribeChannel("proxy.vanish.update");
                getLogger().info("Subscribed to Redis channel 'proxy.vanish.update'.");
            }
        }

        // Listener to keep registry tidy on disconnects
        getLifecycleManager().getListenerManager().registerListener(new ConnectListener(this));
    }

    @Override
    public void disable() {
        if (eventBusHandler != null) {
            eventBusHandler.disable();
            eventBusHandler = null;
        }
        if (vanishRegistry != null) {
            vanishRegistry.clear();
        }

        APIRegistry.unregister(VanishAPI.class);
    }

    public VanishRegistry getVanishRegistry() {
        return vanishRegistry;
    }

    public EventBusHandler getEventBusHandler() {
        return eventBusHandler;
    }

    public VanishAPI getVanishAPI() {
        return api;
    }
}
