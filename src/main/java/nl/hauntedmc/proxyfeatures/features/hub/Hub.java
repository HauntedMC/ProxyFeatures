package nl.hauntedmc.proxyfeatures.features.hub;

import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.hub.command.HubCommand;
import nl.hauntedmc.proxyfeatures.features.hub.meta.Meta;

import java.util.HashMap;
import java.util.Map;

public class Hub extends VelocityBaseFeature<Meta> {

    public Hub(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("enabled", false);
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("hub.not_available", "&cOp dit moment is de Lobby server niet beschikbaar.");
        messageMap.add("hub.already_connected", "&cJe bent al verbonden met deze server.");
        messageMap.add("hub.connection_success", "&aJe wordt verbonden met &7{server}&a.");
        messageMap.add("hub.connection_failure", "&cJe kon helaas niet worden verbonden met &7{server}&c: &f{reason}");
        messageMap.add("hub.unknown_failure_reason", "&cEr is een onbekende fout opgetreden.");
        return messageMap;
    }

    @Override
    public void initialize() {
        getLifecycleManager().getCommandManager().registerFeatureCommand(new HubCommand(this));
    }

    @Override
    public void disable() {

    }

}
