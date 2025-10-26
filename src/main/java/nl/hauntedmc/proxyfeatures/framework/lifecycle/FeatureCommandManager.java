package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeatureCommandManager {

    private final ProxyFeatures plugin;
    private final CommandManager commandManager;
    private final Map<String, FeatureCommand> registeredCommands = new HashMap<>();

    public FeatureCommandManager(ProxyFeatures plugin) {
        this.plugin = plugin;
        this.commandManager = plugin.getCommandManager();
    }


    /**
     * Registers a command dynamically at runtime with an optional tab completer.
     */
    public void registerFeatureCommand(FeatureCommand command) {
        String commandName = command.getName();

        if (registeredCommands.containsKey(commandName)) {
            plugin.getLogger().warn("Command {} is already registered.", commandName);
            return;
        }

        CommandMeta meta = commandManager.metaBuilder(commandName)
                .aliases(command.getAliases())
                .plugin(plugin)
                .build();

        commandManager.register(meta, command);
        registeredCommands.put(commandName, command);

        plugin.getLogger().info("Registered command: {}", commandName);
    }

    /**
     * Unregisters a command dynamically.
     */
    public void unregisterCommand(String commandName) {
        if (!registeredCommands.containsKey(commandName)) {
            plugin.getLogger().warn("Command {} is not registered.", commandName);
            return;
        }

        commandManager.unregister(commandName);
        registeredCommands.remove(commandName);

        plugin.getLogger().info("Unregistered command: {}", commandName);
    }

    /**
     * Unregisters all dynamically registered commands safely.
     */
    public void unregisterAllCommands() {
        List<String> commandNames = new ArrayList<>(registeredCommands.keySet());
        for (String commandName : commandNames) {
            unregisterCommand(commandName);
        }
    }

    /**
     * Get all registered commands of the feature.
     */
    public Map<String, FeatureCommand> getRegisteredCommands() {
        return registeredCommands;
    }

    public int getRegisteredCommandCount() {
        return registeredCommands.size();
    }
}
