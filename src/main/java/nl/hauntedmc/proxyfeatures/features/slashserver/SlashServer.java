package nl.hauntedmc.proxyfeatures.features.slashserver;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.slashserver.command.SlashServerAdminCommand;
import nl.hauntedmc.proxyfeatures.features.slashserver.command.SlashServerCommand;
import nl.hauntedmc.proxyfeatures.features.slashserver.meta.Meta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SlashServer extends VelocityBaseFeature<Meta> {

    private final Set<String> registeredShorthandCommands = new LinkedHashSet<>();

    public SlashServer(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);
        defaults.put("servers", Map.of());
        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap messageMap = new MessageMap();
        messageMap.add("slash.not_available", "&cOp dit moment is &7{server} &cniet beschikbaar.");
        messageMap.add("slash.disabled", "&cDe shorthand voor &7{server} &cstaat momenteel uit.");
        messageMap.add("slash.offline", "&c&7{server} &cis momenteel offline. Probeer het later opnieuw.");
        messageMap.add("slash.already_connected", "&cJe bent al verbonden met deze server.");
        messageMap.add("slash.connection_success", "&aJe wordt verbonden met &7{server}&a.");
        messageMap.add("slash.connection_failure", "&cJe kon helaas niet worden verbonden met &7{server}&c: &f{reason}");
        messageMap.add("slash.admin.usage", "&7Gebruik: &f/slashserver <list|status|enable|disable> [server]");
        messageMap.add("slash.admin.no_permission", "&cJe hebt geen permissie om dit te doen.");
        messageMap.add("slash.admin.unknown_server", "&cServer &7{server} &cwerd niet gevonden.");
        messageMap.add("slash.admin.list.header", "&eSlashServer servers &7({count})&e:");
        messageMap.add("slash.admin.list.entry", "&8- &7{server}&8: {status}");
        messageMap.add("slash.admin.status", "&7{server}&8: {status}");
        messageMap.add("slash.admin.enabled", "&aenabled");
        messageMap.add("slash.admin.disabled", "&cdisabled");
        messageMap.add("slash.admin.already_enabled", "&7{server} &astaat al enabled.");
        messageMap.add("slash.admin.already_disabled", "&7{server} &cstaat al disabled.");
        messageMap.add("slash.admin.set_enabled", "&aShorthand voor &7{server} &ais enabled.");
        messageMap.add("slash.admin.set_disabled", "&cShorthand voor &7{server} &cis disabled.");
        return messageMap;
    }

    @Override
    public void initialize() {
        getLifecycleManager().getCommandManager().registerFeatureCommand(new SlashServerAdminCommand(this));

        syncServersWithConfig();
        for (Map.Entry<String, Boolean> entry : getConfiguredServers().entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                registerShorthandCommand(entry.getKey());
            }
        }
    }

    @Override
    public void disable() {
    }

    public Map<String, Boolean> getConfiguredServers() {
        ConfigNode node = getConfigHandler().node("servers");
        Map<String, Boolean> servers = new LinkedHashMap<>();

        for (String key : node.keys().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()) {
            servers.put(normalizeServerName(key), node.get(key).as(Boolean.class, true));
        }
        return servers;
    }

    public List<String> getConfiguredServerNames() {
        return new ArrayList<>(getConfiguredServers().keySet());
    }

    public boolean hasConfiguredServer(String serverName) {
        return getConfiguredServers().containsKey(normalizeServerName(serverName));
    }

    public boolean isServerEnabled(String serverName) {
        return getConfiguredServers().getOrDefault(normalizeServerName(serverName), false);
    }

    public boolean setServerEnabled(String serverName, boolean enabled) {
        String normalized = normalizeServerName(serverName);
        getConfigHandler().put("servers." + normalized, enabled);

        if (enabled) {
            registerShorthandCommand(normalized);
        } else {
            unregisterShorthandCommand(normalized);
        }
        return true;
    }

    public Optional<RegisteredServer> findServer(String serverName) {
        return getPlugin().getProxyInstance().getServer(normalizeServerName(serverName));
    }

    public String normalizeServerName(String serverName) {
        return serverName == null ? "" : serverName.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isShorthandRegistered(String serverName) {
        return registeredShorthandCommands.contains(normalizeServerName(serverName));
    }

    private void syncServersWithConfig() {
        Set<String> liveServers = new LinkedHashSet<>();
        for (RegisteredServer server : getPlugin().getProxyInstance().getAllServers()) {
            liveServers.add(normalizeServerName(server.getServerInfo().getName()));
        }

        Map<String, Boolean> configuredServers = getConfiguredServers();
        getConfigHandler().batch(batch -> {
            try {
                for (String serverName : liveServers) {
                    if (!configuredServers.containsKey(serverName)) {
                        batch.put("servers." + serverName, true);
                        getLogger().info("Added SlashServer config entry for '" + serverName + "' (default enabled)");
                    }
                }

                for (String configuredName : configuredServers.keySet()) {
                    if (!liveServers.contains(configuredName)) {
                        batch.remove("servers." + configuredName);
                        getLogger().info("Removed stale SlashServer config entry for '" + configuredName + "'");
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to sync SlashServer config", e);
            }
        });
    }

    private void registerShorthandCommand(String serverName) {
        String normalized = normalizeServerName(serverName);
        if (normalized.isBlank() || registeredShorthandCommands.contains(normalized)) {
            return;
        }

        SlashServerCommand command = new SlashServerCommand(this, normalized);
        getLifecycleManager().getCommandManager().registerFeatureCommand(command);
        registeredShorthandCommands.add(normalized);
        getLogger().info("Registered shorthand command: /" + normalized);
    }

    private void unregisterShorthandCommand(String serverName) {
        String normalized = normalizeServerName(serverName);
        if (!registeredShorthandCommands.remove(normalized)) {
            return;
        }

        getLifecycleManager().getCommandManager().unregisterCommand(normalized);
        getLogger().info("Unregistered shorthand command: /" + normalized);
    }
}
