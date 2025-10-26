package nl.hauntedmc.proxyfeatures.features.connectioninfo;

import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.connectioninfo.listener.PlayerListener;
import nl.hauntedmc.proxyfeatures.features.connectioninfo.command.ConnectionInfoCommand;
import nl.hauntedmc.proxyfeatures.features.connectioninfo.command.PingCommand;
import nl.hauntedmc.proxyfeatures.features.connectioninfo.internal.SessionHandler;
import nl.hauntedmc.proxyfeatures.features.connectioninfo.meta.Meta;

public class ConnectionInfo extends VelocityBaseFeature<Meta> {

    private SessionHandler sessionHandler;

    public ConnectionInfo(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("ping_threshold_green", 50);
        defaults.put("ping_threshold_yellow", 150);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messages = new MessageMap();
        messages.add("connectioninfo.ping_usage", "&eGebruik: /ping [speler]");
        messages.add("connectioninfo.ping_notFound", "&cSpeler {player} niet gevonden.");
        messages.add("connectioninfo.ping_self", "&7Je ping is {color}{ping} &7ms.");
        messages.add("connectioninfo.ping_other", "&7De ping van &e{player} &7is {color}{ping} &7ms.");
        messages.add("connectioninfo.cmd_usage", "&eGebruik: /connectioninfo [speler]");
        messages.add("connectioninfo.cmd_playerNotFound", "&cSpeler {player} niet gevonden.");
        messages.add("connectioninfo.cmd_header", "&eConnection Info{subject}:");
        messages.add("connectioninfo.cmd_entry", "  &f{setting}: &a{value}");
        return messages;
    }

    @Override
    public void initialize() {
        sessionHandler = new SessionHandler();
        getLifecycleManager().getListenerManager().registerListener(new PlayerListener(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new PingCommand(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new ConnectionInfoCommand(this));
    }

    @Override
    public void disable() {
        // nothing special
    }

    public SessionHandler getSessionHandler() {
        return sessionHandler;
    }
}
