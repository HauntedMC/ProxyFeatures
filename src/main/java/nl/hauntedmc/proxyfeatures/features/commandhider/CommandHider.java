package nl.hauntedmc.proxyfeatures.features.commandhider;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigMap;
import nl.hauntedmc.proxyfeatures.api.io.localization.MessageMap;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.features.commandhider.command.CommandHiderCommand;
import nl.hauntedmc.proxyfeatures.features.commandhider.internal.HiderHandler;
import nl.hauntedmc.proxyfeatures.features.commandhider.listener.AvailableCommandListener;
import nl.hauntedmc.proxyfeatures.features.commandhider.meta.Meta;

import java.util.List;

public final class CommandHider extends VelocityBaseFeature<Meta> {

    private HiderHandler hiderHandler;

    public CommandHider(ProxyFeatures plugin) {
        super(plugin, new Meta());
    }

    @Override
    public ConfigMap getDefaultConfig() {
        ConfigMap defaults = new ConfigMap();
        defaults.put("enabled", false);

        // Root command literals to hide from the client's available command tree.
        // Note: normalization is applied (trim, strip leading '/', lowercase).
        defaults.put("hidden-commands", List.of("velocity"));

        return defaults;
    }

    @Override
    public MessageMap getDefaultMessages() {
        MessageMap m = new MessageMap();

        m.add("commandhider.command.usage",
                "&7Usage: &f/commandhider <list|add|remove>");

        m.add("commandhider.command.list",
                "&7Hidden commands (&f{count}&7): &f{commands}");

        m.add("commandhider.command.added",
                "&aAdded hidden command: &f{command}");

        m.add("commandhider.command.removed",
                "&aRemoved hidden command: &f{command}");

        m.add("commandhider.command.already",
                "&eAlready hidden: &f{command}");

        m.add("commandhider.command.not_found",
                "&cNot hidden: &f{command}");

        m.add("commandhider.command.invalid",
                "&cInvalid command name.");

        return m;
    }

    @Override
    public void initialize() {
        this.hiderHandler = new HiderHandler(this);
        this.hiderHandler.refreshFromConfig();

        getLifecycleManager().getListenerManager().registerListener(new AvailableCommandListener(this));
        getLifecycleManager().getCommandManager().registerBrigadierCommand(new CommandHiderCommand(this));
    }

    @Override
    public void disable() {
        // No resources to close.
    }

    public HiderHandler getHiderHandler() {
        return hiderHandler;
    }
}
