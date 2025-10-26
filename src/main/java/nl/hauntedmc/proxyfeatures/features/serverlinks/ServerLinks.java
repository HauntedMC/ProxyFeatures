package nl.hauntedmc.proxyfeatures.features.serverlinks;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.serverlinks.internal.ServerLinksHandler;
import nl.hauntedmc.proxyfeatures.features.serverlinks.listener.JoinListener;
import nl.hauntedmc.proxyfeatures.features.serverlinks.meta.Meta;

public class ServerLinks extends VelocityBaseFeature<Meta> {


    private ServerLinksHandler serverLinksHandler;

    public ServerLinks(ProxyFeatures plugin) {
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
        serverLinksHandler = new ServerLinksHandler();
        getLifecycleManager().getListenerManager().registerListener(new JoinListener(this));
    }

    @Override
    public void disable() {
    }

    public ServerLinksHandler getServerLinksHandler() {
        return serverLinksHandler;
    }
}