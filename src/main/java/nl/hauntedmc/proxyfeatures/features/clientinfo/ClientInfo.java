package nl.hauntedmc.proxyfeatures.features.clientinfo;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.clientinfo.command.ClientInfoCommand;
import nl.hauntedmc.proxyfeatures.features.clientinfo.listener.PlayerListener;
import nl.hauntedmc.proxyfeatures.features.clientinfo.meta.Meta;

public class ClientInfo extends VelocityBaseFeature<Meta> {


    public ClientInfo(ProxyFeatures plugin) {
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
        MessageMap messages = new MessageMap();
        messages.add("clientinfo.header", "&eEr zijn aanbevelingen voor je client instellingen:");
        messages.add("clientinfo.recommendation", "  &f{setting_name}: &c{setting_found} &7-> &a{setting_recommended}");
        messages.add("clientinfo.cmd_usage", "&eGebruik: /clientinfo <speler>");
        messages.add("clientinfo.cmd_header", "&eClient Instellingen van {player}:");
        messages.add("clientinfo.cmd_entry", "  &f{setting}: {value}");
        messages.add("clientinfo.cmd_playerNotFound", "&cSpeler {player} niet gevonden.");
        return messages;
    }

    @Override
    public void initialize() {
        getLifecycleManager().getListenerManager().registerListener(new PlayerListener(this));
        getLifecycleManager().getCommandManager().registerFeatureCommand(new ClientInfoCommand(this));
    }

    @Override
    public void disable() {
    }

}