package nl.hauntedmc.proxyfeatures.features.logger;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.BaseFeature;
import nl.hauntedmc.proxyfeatures.features.logger.internal.LogHandler;
import nl.hauntedmc.proxyfeatures.features.logger.listener.ChatListener;
import nl.hauntedmc.proxyfeatures.features.logger.meta.Meta;
import nl.hauntedmc.proxyfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.Map;

public class Logger extends BaseFeature<Meta> {

    private LogHandler logHandler;

    public Logger(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
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
