package nl.hauntedmc.proxyfeatures.features.slashserver;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import nl.hauntedmc.commonlib.config.ConfigMap;
import nl.hauntedmc.commonlib.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.slashserver.command.SlashServerCommand;
import nl.hauntedmc.proxyfeatures.features.slashserver.meta.Meta;

public class SlashServer extends VelocityBaseFeature<Meta> {

    public SlashServer(ProxyFeatures plugin) {
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
        MessageMap messageMap = new MessageMap();
        messageMap.add("slash.not_available", "&cOp dit moment is &7{server} &cniet beschikbaar.");
        messageMap.add("slash.already_connected", "&cJe bent al verbonden met deze server.");
        messageMap.add("slash.connection_success", "&aJe wordt verbonden met &7{server}&a.");
        messageMap.add("slash.connection_failure", "&cJe kon helaas niet worden verbonden met &7{server}&c: &f{reason}");
        messageMap.add("slash.unknown_failure_reason", "&cEr is een onbekende fout opgetreden.");
        return messageMap;
    }

    @Override
    public void initialize() {
        for (RegisteredServer server : getPlugin().getProxy().getAllServers()) {
            String serverName = server.getServerInfo().getName().toLowerCase();
            SlashServerCommand command = new SlashServerCommand(this, serverName);
            getLifecycleManager().getCommandManager().registerFeatureCommand(command);
            getPlugin().getLogger().info("Registered shorthand command: /{}", serverName);
        }
    }

    @Override
    public void disable() {
    }

}
