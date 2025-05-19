package nl.hauntedmc.proxyfeatures.features.logger;

import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.logger.internal.LogHandler;
import nl.hauntedmc.proxyfeatures.features.logger.listener.ChatListener;
import nl.hauntedmc.proxyfeatures.features.logger.meta.Meta;

public class Logger extends VelocityBaseFeature<Meta> {

    private LogHandler logHandler;

    public Logger(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        return messageMap;
    }

    @Override
    public void initialize() {
        this.logHandler = new LogHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new ChatListener(this));
    }

    @Override
    public void disable() {

    }

    public LogHandler getLogHandler() {
        return logHandler;
    }
}
