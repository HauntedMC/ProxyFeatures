package nl.hauntedmc.proxyfeatures.features.versioncheck;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.versioncheck.internal.VersionHandler;
import nl.hauntedmc.proxyfeatures.features.versioncheck.listener.ConnectionListener;
import nl.hauntedmc.proxyfeatures.features.versioncheck.meta.Meta;

public class VersionCheck extends VelocityBaseFeature<Meta> {

    private VersionHandler versionHandler;

    public VersionCheck(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("minimum_protocol_version", 763);
        defaults.put("friendly_protocol_name", "1.21");
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("versioncheck.unsupported_version", "&cOp HauntedMC kun je spelen op versie {friendly_protocol_name} of hoger.");
        return messageMap;
    }

    @Override
    public void initialize() {
        versionHandler = new VersionHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new ConnectionListener(this));
    }

    @Override
    public void disable() {

    }

    public VersionHandler getVersionHandler() {
        return versionHandler;
    }

}
