package nl.hauntedmc.proxyfeatures.features.hub;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.BaseFeature;
import nl.hauntedmc.proxyfeatures.features.hub.command.HubCommand;
import nl.hauntedmc.proxyfeatures.features.hub.meta.Meta;
import nl.hauntedmc.proxyfeatures.localization.MessageMap;

import java.util.HashMap;
import java.util.Map;

public class Hub extends BaseFeature<Meta> {

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
