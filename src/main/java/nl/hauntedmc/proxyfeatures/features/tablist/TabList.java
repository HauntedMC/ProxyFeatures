package nl.hauntedmc.proxyfeatures.features.tablist;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.tablist.listener.JoinListener;
import nl.hauntedmc.proxyfeatures.features.tablist.meta.Meta;

public class TabList extends VelocityBaseFeature<Meta> {

    public TabList(ProxyFeatures plugin) {
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
        getLifecycleManager().getListenerManager().registerListener(new JoinListener());
    }

    @Override
    public void disable() {
    }

}