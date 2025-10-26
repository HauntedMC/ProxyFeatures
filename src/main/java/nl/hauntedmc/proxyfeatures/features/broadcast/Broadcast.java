package nl.hauntedmc.proxyfeatures.features.broadcast;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.broadcast.command.BroadcastProxyCommand;
import nl.hauntedmc.proxyfeatures.features.broadcast.meta.Meta;

public class Broadcast extends VelocityBaseFeature<Meta> {

    public Broadcast(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap cfg = new ConfigMap();
        cfg.put("enabled", false);
        cfg.put("title_fade_in", 20);
        cfg.put("title_stay", 100);
        cfg.put("title_fade_out", 20);
        return cfg;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();
        m.add("broadcast.usage", "&eGebruik: /broadcastproxy <title|chat> <bericht>");
        m.add("broadcast.sent", "&aBroadcast verstuurd.");
        m.add("broadcast.noMode", "&cOngeldige optie. Gebruik 'title' of 'chat'.");
        return m;
    }

    @Override
    public void initialize() {
        // Register as Brigadier command (Velocity)
        getLifecycleManager()
                .getCommandManager()
                .registerBrigadierCommand(new BroadcastProxyCommand(this));
    }

    @Override
    public void disable() {
    }
}
