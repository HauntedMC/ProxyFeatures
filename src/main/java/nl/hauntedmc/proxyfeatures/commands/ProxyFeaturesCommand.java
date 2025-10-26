package nl.hauntedmc.proxyfeatures.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.internal.action.disable.FeatureDisableResponse;
import nl.hauntedmc.proxyfeatures.internal.action.enable.FeatureEnableResponse;
import nl.hauntedmc.proxyfeatures.internal.action.reload.FeatureReloadResponse;
import nl.hauntedmc.proxyfeatures.internal.action.softreload.FeatureSoftReloadResponse;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ProxyFeaturesCommand implements SimpleCommand {

    private final ProxyFeatures plugin;

    public ProxyFeaturesCommand(ProxyFeatures plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(final Invocation invocation) {
        final CommandSource sender = invocation.source();
        final String[] args = invocation.arguments();

        if (args.length == 0) {
            sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("general.usage")
                    .forAudience(sender)
                    .build());
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                if (!has(sender, "proxyfeatures.command.status")) return;
                sendPluginStatus(sender);
            }
            case "list" -> {
                if (!has(sender, "proxyfeatures.command.list")) return;
                listLoadedFeatures(sender);
            }
            case "softreload" -> {
                if (!has(sender, "proxyfeatures.command.reload")) return;
                if (args.length < 2) {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.softreload.usage")
                            .forAudience(sender)
                            .build());
                    return;
                }
                handleSoftReload(sender, args[1]);
            }
            case "reload" -> {
                if (!has(sender, "proxyfeatures.command.reload")) return;
                if (args.length < 2) {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.reload.usage")
                            .forAudience(sender)
                            .build());
                    return;
                }
                handleReload(sender, args[1]);
            }
            case "enable" -> {
                if (!has(sender, "proxyfeatures.command.enable")) return;
                if (args.length < 2) return; // mimic Bukkit behaviour (no explicit usage)
                handleEnable(sender, args[1]);
            }
            case "disable" -> {
                if (!has(sender, "proxyfeatures.command.disable")) return;
                if (args.length < 2) return; // mimic Bukkit behaviour (no explicit usage)
                handleDisable(sender, args[1]);
            }
            case "reloadlocal" -> {
                if (!has(sender, "proxyfeatures.command.reloadlocal")) return;
                try {
                    plugin.getLocalizationHandler().reloadLocalization();
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.reloadlocal.success")
                            .forAudience(sender)
                            .build());
                } catch (Throwable t) {
                    plugin.getLogger().warn("Localization reload failed: {}", t.getMessage());
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.reloadlocal.fail")
                            .forAudience(sender)
                            .build());
                }
            }
            default -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("general.unknown_command")
                    .forAudience(sender)
                    .build());
        }
    }

    private void handleEnable(CommandSource sender, String feature) {
        FeatureEnableResponse resp = plugin.getFeatureLoadManager().enableFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.success")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
            case NOT_FOUND -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.not_found")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
            case ALREADY_LOADED -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.already_loaded")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
            case MISSING_PLUGIN_DEPENDENCY -> {
                String plugins = String.join(", ", resp.missingPlugins());
                sender.sendMessage(plugin.getLocalizationHandler()
                        .getMessage("command.enable.missing_plugin_dependency")
                        .forAudience(sender)
                        .with("feature", feature)
                        .with("plugins", plugins)
                        .build());
            }
            case MISSING_FEATURE_DEPENDENCY -> {
                String deps = String.join(", ", resp.missingFeatures());
                sender.sendMessage(plugin.getLocalizationHandler()
                        .getMessage("command.enable.missing_feature_dependency")
                        .forAudience(sender)
                        .with("feature", feature)
                        .with("features", deps)
                        .build());
            }
            default -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.enable.failed")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
        }
    }

    private void handleDisable(CommandSource sender, String feature) {
        FeatureDisableResponse resp = plugin.getFeatureLoadManager().disableFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> {
                if (!resp.alsoDisabledDependents().isEmpty()) {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.disable.success_with_dependents")
                            .forAudience(sender)
                            .with("feature", feature)
                            .with("dependents", String.join(", ", resp.alsoDisabledDependents()))
                            .build());
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.disable.success")
                            .forAudience(sender)
                            .with("feature", feature)
                            .build());
                }
            }
            case NOT_LOADED -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.disable.not_loaded")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
            default -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.disable.failed")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
        }
    }

    private void handleSoftReload(CommandSource sender, String feature) {
        FeatureSoftReloadResponse resp = plugin.getFeatureLoadManager().softReloadFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.softreload.success")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
            case NOT_LOADED -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.softreload.not_loaded")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
            default -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.softreload.failed")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
        }
    }

    private void handleReload(CommandSource sender, String feature) {
        FeatureReloadResponse resp = plugin.getFeatureLoadManager().reloadFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> {
                if (!resp.reloadedDependents().isEmpty()) {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.reload.success_with_dependents")
                            .forAudience(sender)
                            .with("feature", feature)
                            .with("dependents", String.join(", ", resp.reloadedDependents()))
                            .build());
                } else {
                    sender.sendMessage(plugin.getLocalizationHandler()
                            .getMessage("command.reload.success")
                            .forAudience(sender)
                            .with("feature", feature)
                            .build());
                }
            }
            case NOT_LOADED -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.reload.not_loaded")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
            default -> sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.reload.failed")
                    .forAudience(sender)
                    .with("feature", feature)
                    .build());
        }
    }

    private boolean has(CommandSource sender, String perm) {
        if (!sender.hasPermission(perm)) {
            sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("general.no_permission")
                    .forAudience(sender)
                    .build());
            return false;
        }
        return true;
    }

    private void sendPluginStatus(CommandSource sender) {
        List<VelocityBaseFeature<?>> loadedFeatures = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures();
        List<String> loadedCommands = new ArrayList<>();
        int loadedFeatureCount = loadedFeatures.size();
        int activeTaskCount = 0;
        int registeredListenerCount = 0;
        int registeredCommandCount = 0;
        int activeConnCount = 0;

        for (VelocityBaseFeature<?> feature : loadedFeatures) {
            var cmds = feature.getLifecycleManager().getCommandManager().getRegisteredCommands();
            registeredCommandCount += (cmds != null ? cmds.size() : 0);
            if (cmds != null) {
                loadedCommands.addAll(cmds.keySet());
            }
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
        List<VelocityBaseFeature<?>> loadedFeatures = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures();

        if (loadedFeatures.isEmpty()) {
            sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.list.empty")
                    .forAudience(sender)
                    .build());
            return;
        }

        sender.sendMessage(plugin.getLocalizationHandler()
                .getMessage("command.list.header")
                .forAudience(sender)
                .build());

        for (VelocityBaseFeature<?> feature : loadedFeatures) {
            sender.sendMessage(plugin.getLocalizationHandler()
                    .getMessage("command.list.entry")
                    .forAudience(sender)
                    .with("feature", feature.getFeatureName())
                    .with("version", feature.getFeatureVersion())
                    .build());
        }
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        // Global gate; per-subcommand checks still enforced
        return invocation.source().hasPermission("proxyfeatures.use");
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
        final Locale L = Locale.ROOT;
        final String[] args = invocation.arguments();

        if (args.length == 0) {
            return CompletableFuture.completedFuture(List.of());
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(L);
            List<String> subs = Stream.of("list", "disable", "enable", "reload", "reloadlocal", "softreload", "status")
                    .filter(s -> s.toLowerCase(L).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            return CompletableFuture.completedFuture(subs);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase(L);
            String featurePrefix = args[1].toLowerCase(L);

            List<String> result = switch (sub) {
                case "reload", "softreload", "disable" ->
                        plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                                .map(VelocityBaseFeature::getFeatureName)
                                .filter(Objects::nonNull)
                                .filter(name -> name.toLowerCase(L).startsWith(featurePrefix))
                                .sorted(String.CASE_INSENSITIVE_ORDER)
                                .toList();

                case "enable" -> {
                    Set<String> loadedLower = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                            .map(VelocityBaseFeature::getFeatureName)
                            .filter(Objects::nonNull)
                            .map(s -> s.toLowerCase(L))
                            .collect(Collectors.toSet());

                    yield plugin.getFeatureLoadManager().getFeatureRegistry().getAvailableFeatures().keySet().stream()
                            .filter(Objects::nonNull)
                            .filter(name -> !loadedLower.contains(name.toLowerCase(L)))
                            .filter(name -> name.toLowerCase(L).startsWith(featurePrefix))
                            .sorted(String.CASE_INSENSITIVE_ORDER)
                            .toList();
                }

                default -> Collections.emptyList();
            };

            return CompletableFuture.completedFuture(result);
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}
