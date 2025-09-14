package nl.hauntedmc.proxyfeatures.features.commandlogger;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.commandlogger.entity.CommandExecutionEntity;
import nl.hauntedmc.proxyfeatures.features.commandlogger.internal.LogHandler;
import nl.hauntedmc.proxyfeatures.features.commandlogger.service.CommandLogService;
import nl.hauntedmc.proxyfeatures.features.commandlogger.listener.CommandListener;
import nl.hauntedmc.proxyfeatures.features.commandlogger.meta.Meta;

public class CommandLogger extends VelocityBaseFeature<Meta> {

    private LogHandler logHandler;
    private CommandLogService commandLogService;
    private ORMContext ormContext;

    public CommandLogger(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        // Nothing specific needed here for now, server is always "proxy" on Velocity
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        // Database init and ORM registration
        getLifecycleManager().getDataManager().initDataProvider(getFeatureName());
        getLifecycleManager().getDataManager().registerConnection("ormConnection", DatabaseType.MYSQL, "player_data_rw");
        ormContext = getLifecycleManager().getDataManager().createORMContext(
                "ormConnection",
                PlayerEntity.class,
                CommandExecutionEntity.class
        ).orElseThrow();

        // Services & listeners
        this.commandLogService = new CommandLogService(this);
        this.logHandler = new LogHandler(this);

        getLifecycleManager().getListenerManager().registerListener(new CommandListener(this));
    }

    @Override
    public void disable() {
    }

    public ORMContext getOrmContext() {
        return ormContext;
    }

    public CommandLogService getCommandLogService() {
        return commandLogService;
    }

    public LogHandler getLogHandler() {
        return logHandler;
    }
}
