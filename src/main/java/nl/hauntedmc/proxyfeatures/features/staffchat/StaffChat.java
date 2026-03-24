package nl.hauntedmc.proxyfeatures.features.staffchat;

import nl.hauntedmc.dataprovider.database.DatabaseProvider;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.messaging.MessagingDataAccess;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.ChatChannelHandler;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.messaging.EventBusHandler;
import nl.hauntedmc.proxyfeatures.features.staffchat.listener.ConnectListener;
import nl.hauntedmc.proxyfeatures.features.staffchat.meta.Meta;

import java.util.Optional;

public class StaffChat extends VelocityBaseFeature<Meta> {

    private ChatChannelHandler chatChannelHandler;
    private EventBusHandler eventBusHandler;

    public StaffChat(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("staff_prefix", "!");
        defaults.put("team_prefix", "@");
        defaults.put("admin_prefix", "#");
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("staffchat.staff_format", "&8[&bStaffChat&68] &7[{server}] &7{player}: &b{message}");
        messageMap.add("staffchat.team_format", "&8[&eTeamChat&6] &7[{server}] &7{player}: &e{message}");
        messageMap.add("staffchat.admin_format", "&8[&cAdminChat&6] &7[{server}] &7{player}: &c{message}");
        messageMap.add("staffchat.staff_join", "&6[&bStaffChat&6] &a{player} &7is gejoined.");
        messageMap.add("staffchat.staff_leave", "&6[&bStaffChat&6] &c{player} &7is geleaved.");
        messageMap.add("staffchat.staff_switch", "&6[&bStaffChat&6] &a{player} &7is geswitched van &c{from}&7 naar &c{to}&7.");
        return messageMap;
    }

    @Override
    public void initialize() {
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

        DatabaseProvider dbp = opt.get();
        Object dataAccess = dbp.getDataAccess();
        if (!(dataAccess instanceof MessagingDataAccess redisBus)) {
            getLogger().warn("Registered 'redis' connection is not a messaging provider; staff chat sync disabled.");
            return;
        }

        eventBusHandler = new EventBusHandler(this, redisBus);
        eventBusHandler.subscribeChannel("proxy.staffchat.message");


        this.chatChannelHandler = new ChatChannelHandler(this);
        chatChannelHandler.initializeViewers(getPlugin().getProxyInstance().getAllPlayers());
        // Register listeners.
        getLifecycleManager().getListenerManager().registerListener(new ConnectListener(this));
    }

    @Override
    public void disable() {
        if (eventBusHandler != null) {
            eventBusHandler.disable();
            eventBusHandler = null;
        }
    }

    public ChatChannelHandler getChatChannelHandler() {
        return chatChannelHandler;
    }

    public EventBusHandler getEventBusHandler() {
        return eventBusHandler;
    }
}
