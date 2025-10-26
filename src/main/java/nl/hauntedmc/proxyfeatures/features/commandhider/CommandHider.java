package nl.hauntedmc.proxyfeatures.features.commandhider;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.commandhider.internal.HiderHandler;
import nl.hauntedmc.proxyfeatures.features.commandhider.listener.AvailableCommandListener;
import nl.hauntedmc.proxyfeatures.features.commandhider.meta.Meta;

import java.util.List;


public class CommandHider extends VelocityBaseFeature<Meta> {


    private HiderHandler hiderHandler;

    public CommandHider(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("hidden-commands", List.of("velocity"));
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        return new MessageMap();
    }

    @Override
    public void initialize() {
        this.hiderHandler = new HiderHandler(this);
        getLifecycleManager().getListenerManager().registerListener(new AvailableCommandListener(this));
    }


    @Override
    public void disable() {
    }

    public HiderHandler getHiderHandler() {
        return hiderHandler;
    }
}
