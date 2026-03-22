package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.api.command.brigadier.BrigadierCommand;

import java.util.*;

/**
 * Registers/unregisters SimpleCommand and Brigadier commands at runtime.
 */
public class FeatureCommandManager {

    private final ProxyFeatures plugin;
    private final CommandManager commandManager;

    // Simple (Velocity SimpleCommand) commands
    private final Map<String, FeatureCommand> registeredCommands = new HashMap<>();

    // Brigadier root commands (our API interface -> Meta for clean unregister)
    private final Map<String, BrigadierCommand> registeredBrigadierCommands = new HashMap<>();
    private final Map<String, CommandMeta> brigadierMetas = new HashMap<>();

    public FeatureCommandManager(ProxyFeatures plugin) {
        this.plugin = plugin;
        this.commandManager = plugin.getCommandManager();
    }

    /* ========================== SimpleCommand ========================== */

    /**
     * Registers a SimpleCommand dynamically at runtime.
     */
    public void registerFeatureCommand(FeatureCommand command) {
        final String commandName = command.getName();

        if (registeredCommands.containsKey(commandName)) {
            plugin.getLogger().warn("Command {} is already registered.", commandName);
            return;
        }
        try {
            String[] aliases = sanitizeAliases(command.getAliases(), commandName);
            CommandMeta meta = commandManager.metaBuilder(commandName)
                    .aliases(aliases)
                    .plugin(plugin)
                    .build();

            commandManager.register(meta, command);
            registeredCommands.put(commandName, command);

            plugin.getLogger().info("Registered command: {}", commandName);
        } catch (Exception t) {
            plugin.getLogger().warn("Failed to register command {}: {}", commandName, t.getMessage());
        }
    }

    /**
     * Unregisters a SimpleCommand dynamically.
     */
    public void unregisterCommand(String commandName) {
        if (!registeredCommands.containsKey(commandName)) {
            plugin.getLogger().warn("Command {} is not registered.", commandName);
            return;
        }
        try {
            commandManager.unregister(commandName);
        } catch (Exception t) {
            plugin.getLogger().warn("Failed to unregister {}: {}", commandName, t.getMessage());
        } finally {
            registeredCommands.remove(commandName);
            plugin.getLogger().info("Unregistered command: {}", commandName);
        }
    }

    /**
     * Unregisters all SimpleCommand registrations safely.
     */
    public void unregisterAllCommands() {
        List<String> names = new ArrayList<>(registeredCommands.keySet());
        for (String name : names) {
            unregisterCommand(name);
        }
    }

    /* ============================ Brigadier ============================ */

    /**
     * Registers a Brigadier root command dynamically.
     * The command tree is provided by our API interface and wrapped into Velocity's BrigadierCommand.
     */
    public void registerBrigadierCommand(BrigadierCommand command) {
        final String key = command.name();

        if (registeredBrigadierCommands.containsKey(key)) {
            plugin.getLogger().warn("[Brigadier] Already registered: {}", key);
            return;
        }
        if (commandManager.hasCommand(key)) {
            plugin.getLogger().warn("[Brigadier] Alias '{}' is already in use by another command. Skipping.", key);
            return;
        }

        try {
            // Build the literal node from the feature and wrap it for Velocity
            LiteralCommandNode<CommandSource> node = command.buildTree();
            com.velocitypowered.api.command.BrigadierCommand velocityCmd =
                    new com.velocitypowered.api.command.BrigadierCommand(node);

            // Use the Brigadier-aware metaBuilder; add aliases + plugin for proper ownership
            CommandMeta meta = commandManager.metaBuilder(velocityCmd)
                    .aliases(command.aliases().toArray(String[]::new))
                    .plugin(plugin)
                    .build();

            commandManager.register(meta, velocityCmd);

            registeredBrigadierCommands.put(key, command);
            brigadierMetas.put(key, meta);

            plugin.getLogger().info("[Brigadier] Registered /{} ({} aliases)", key, command.aliases().size());
        } catch (Exception t) {
            plugin.getLogger().warn("[Brigadier] Failed to register /{}: {}", key, t.getMessage());
        }
    }

    /**
     * Unregister a single Brigadier root command by name.
     */
    public void unregisterBrigadierCommand(String name) {
        BrigadierCommand removed = registeredBrigadierCommands.remove(name);
        if (removed == null) {
            plugin.getLogger().warn("[Brigadier] Not registered: {}", name);
            return;
        }
        CommandMeta meta = brigadierMetas.remove(name);
        try {
            if (meta != null) {
                commandManager.unregister(meta);
            } else {
                // Fallback: try by alias (primary)
                commandManager.unregister(name);
                for (String a : removed.aliases()) {
                    commandManager.unregister(a);
                }
            }
            plugin.getLogger().info("[Brigadier] Unregistered /{}", name);
        } catch (Exception t) {
            plugin.getLogger().warn("[Brigadier] detach failed for /{}: {}", name, t.getMessage());
        }
    }

    /**
     * HARD-unregister all Brigadier root commands owned by this feature.
     */
    public void unregisterAllBrigadierCommands() {
        if (registeredBrigadierCommands.isEmpty()) return;

        List<String> snapshot = new ArrayList<>(registeredBrigadierCommands.keySet());
        for (String name : snapshot) {
            unregisterBrigadierCommand(name);
        }
    }

    /* ========================== Combined helpers ========================= */

    public Map<String, FeatureCommand> getRegisteredCommands() {
        return Collections.unmodifiableMap(registeredCommands);
    }

    public int getRegisteredCommandCount() {
        return registeredCommands.size();
    }

    public Map<String, BrigadierCommand> getRegisteredBrigadierCommands() {
        return Collections.unmodifiableMap(registeredBrigadierCommands);
    }

    public int getRegisteredBrigadierCommandCount() {
        return registeredBrigadierCommands.size();
    }

    public int getTotalRegisteredCommandCount() {
        return registeredCommands.size() + registeredBrigadierCommands.size();
    }

    public Set<String> getAllRegisteredCommandNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.addAll(registeredCommands.keySet());
        names.addAll(registeredBrigadierCommands.keySet());
        return Collections.unmodifiableSet(names);
    }

    private static String[] sanitizeAliases(String[] aliases, String commandName) {
        if (aliases == null || aliases.length == 0) {
            return new String[0];
        }
        List<String> out = new ArrayList<>();
        for (String alias : aliases) {
            if (alias == null) {
                continue;
            }
            String trimmed = alias.trim();
            if (trimmed.isEmpty() || trimmed.equalsIgnoreCase(commandName)) {
                continue;
            }
            if (!out.contains(trimmed)) {
                out.add(trimmed);
            }
        }
        return out.toArray(String[]::new);
    }
}
