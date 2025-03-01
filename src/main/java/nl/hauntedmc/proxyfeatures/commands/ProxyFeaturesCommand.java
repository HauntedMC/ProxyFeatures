package nl.hauntedmc.proxyfeatures.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.BaseFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ProxyFeaturesCommand implements SimpleCommand {

    private final ProxyFeatures plugin;

    public ProxyFeaturesCommand(ProxyFeatures plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(final Invocation invocation) {
        CommandSource sender = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.usage"));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "status":
                if (!sender.hasPermission("proxyfeatures.command.status")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission"));
                    return;
                }
                sendPluginStatus(sender);
                break;

            case "list":
                if (!sender.hasPermission("proxyfeatures.command.list")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission"));
                    return;
                }
                listLoadedFeatures(sender);
                break;

            case "reload":
                if (!sender.hasPermission("proxyfeatures.command.reload")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission"));
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.reload.usage"));
                    return;
                }
                if (plugin.getFeatureLoadManager().reloadFeature(args[1])) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.reload.success", Map.of("feature", args[1])));
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.reload.fail"));
                }
                break;

            case "enable":
                if (!sender.hasPermission("proxyfeatures.command.enable")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission"));
                    return;
                }
                if (args.length < 2) return;
                if (plugin.getFeatureLoadManager().enableFeature(args[1])) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.enable.success", Map.of("feature", args[1])));
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.enable.fail"));
                }
                break;

            case "disable":
                if (!sender.hasPermission("proxyfeatures.command.disable")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission"));
                    return;
                }
                if (args.length < 2) return;
                if (plugin.getFeatureLoadManager().disableFeature(args[1])) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.disable.success", Map.of("feature", args[1])));
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.disable.fail"));
                }
                break;

            case "reloadlocal":
                if (!sender.hasPermission("proxyfeatures.command.reloadlocal")) {
                    sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.no_permission"));
                    return;
                }
                plugin.getLocalizationHandler().reloadLocalization();
                sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.reloadlocal.success"));
                break;

            default:
                sender.sendMessage(plugin.getLocalizationHandler().getMessage("general.unknown_command"));
                break;
        }
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.use");
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("list");
            completions.add("reload");
            completions.add("reloadlocal");
            completions.add("enable");
            completions.add("disable");
            completions.add("status");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "reload":
                case "disable":
                    completions.addAll(plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                            .map(BaseFeature::getFeatureName)
                            .toList());
                    break;

                case "enable":
                    completions.addAll(plugin.getFeatureLoadManager().getFeatureRegistry().getAvailableFeatures().keySet().stream()
                            .filter(feature -> plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                                    .noneMatch(loadedFeature -> loadedFeature.getFeatureName().equalsIgnoreCase(feature)))
                            .toList());
                    break;
            }
        }
        return CompletableFuture.completedFuture(completions);
    }


    private void sendPluginStatus(CommandSource sender) {
        List<BaseFeature<?>> loadedFeatures = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures();
        List<String> loadedCommands = new ArrayList<>();
        int loadedFeatureCount = loadedFeatures.size();
        int activeTaskCount = 0;
        int registeredListenerCount = 0;
        int registeredCommandCount = 0;
        int activeConnCount = 0;

        for (BaseFeature<?> feature : loadedFeatures) {
            registeredCommandCount += feature.getLifecycleManager().getCommandManager().getRegisteredCommandCount();
            loadedCommands.addAll(feature.getLifecycleManager().getCommandManager()
                    .getRegisteredCommands()
                    .values()
                    .stream()
                    .map(FeatureCommand::getName)
                    .toList());
            activeTaskCount += feature.getLifecycleManager().getTaskManager().getActiveTaskCount();
            registeredListenerCount += feature.getLifecycleManager().getListenerManager().getRegisteredListenerCount();
            activeConnCount += feature.getLifecycleManager().getDataManager().getActiveConnCount();
        }

        sender.sendMessage(Component.text("ProxyFeatures Status:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("- Number of loaded features: " + loadedFeatureCount, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of active database connections: " + activeConnCount, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of active tasks: " + activeTaskCount, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of registered listeners: " + registeredListenerCount, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of registered commands: " + registeredCommandCount, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Registered commands: " + loadedCommands, NamedTextColor.WHITE));
    }

    private void listLoadedFeatures(CommandSource sender) {
        List<BaseFeature<?>> loadedFeatures = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures();

        if (loadedFeatures.isEmpty()) {
            sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.list.empty"));
            return;
        }

        sender.sendMessage(plugin.getLocalizationHandler().getMessage("command.list.header"));
        for (BaseFeature<?> feature : loadedFeatures) {
            sender.sendMessage(plugin.getLocalizationHandler().getMessage(
                    "command.list.entry",
                    Map.of("feature", feature.getFeatureName(), "version", feature.getFeatureVersion())
            ));
        }
    }
}


