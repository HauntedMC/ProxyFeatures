package nl.hauntedmc.proxyfeatures.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;

import nl.hauntedmc.proxyfeatures.internal.action.enable.FeatureEnableResponse;
import nl.hauntedmc.proxyfeatures.internal.action.disable.FeatureDisableResponse;
import nl.hauntedmc.proxyfeatures.internal.action.reload.FeatureReloadResponse;
import nl.hauntedmc.proxyfeatures.internal.action.softreload.FeatureSoftReloadResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
            send("general.usage", sender, Map.of());
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
                if (args.length < 2) { send("command.softreload.usage", sender, Map.of()); return; }
                handleSoftReload(sender, args[1]);
            }
            case "reload" -> {
                if (!has(sender, "proxyfeatures.command.reload")) return;
                if (args.length < 2) { send("command.reload.usage", sender, Map.of()); return; }
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
                    send("command.reloadlocal.success", sender, Map.of());
                } catch (Throwable t) {
                    plugin.getLogger().warn("Localization reload failed: {}", t.getMessage());
                    send("command.reloadlocal.fail", sender, Map.of());
                }
            }
            default -> send("general.unknown_command", sender, Map.of());
        }
    }

    private void handleEnable(CommandSource sender, String feature) {
        FeatureEnableResponse resp = plugin.getFeatureLoadManager().enableFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> send("command.enable.success", sender, Map.of("feature", feature));
            case NOT_FOUND -> send("command.enable.not_found", sender, Map.of("feature", feature));
            case ALREADY_LOADED -> send("command.enable.already_loaded", sender, Map.of("feature", feature));
            case MISSING_PLUGIN_DEPENDENCY -> {
                String plugins = String.join(", ", resp.missingPlugins());
                send("command.enable.missing_plugin_dependency", sender, Map.of("feature", feature, "plugins", plugins));
            }
            case MISSING_FEATURE_DEPENDENCY -> {
                String deps = String.join(", ", resp.missingFeatures());
                send("command.enable.missing_feature_dependency", sender, Map.of("feature", feature, "features", deps));
            }
            default -> send("command.enable.failed", sender, Map.of("feature", feature));
        }
    }

    private void handleDisable(CommandSource sender, String feature) {
        FeatureDisableResponse resp = plugin.getFeatureLoadManager().disableFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> {
                if (!resp.alsoDisabledDependents().isEmpty()) {
                    send("command.disable.success_with_dependents", sender, Map.of(
                            "feature", feature,
                            "dependents", String.join(", ", resp.alsoDisabledDependents())
                    ));
                } else {
                    send("command.disable.success", sender, Map.of("feature", feature));
                }
            }
            case NOT_LOADED -> send("command.disable.not_loaded", sender, Map.of("feature", feature));
            default -> send("command.disable.failed", sender, Map.of("feature", feature));
        }
    }

    private void handleSoftReload(CommandSource sender, String feature) {
        FeatureSoftReloadResponse resp = plugin.getFeatureLoadManager().softReloadFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> send("command.softreload.success", sender, Map.of("feature", feature));
            case NOT_LOADED -> send("command.softreload.not_loaded", sender, Map.of("feature", feature));
            default -> send("command.softreload.failed", sender, Map.of("feature", feature));
        }
    }

    private void handleReload(CommandSource sender, String feature) {
        FeatureReloadResponse resp = plugin.getFeatureLoadManager().reloadFeature(feature);
        switch (resp.result()) {
            case SUCCESS -> {
                if (!resp.reloadedDependents().isEmpty()) {
                    send("command.reload.success_with_dependents", sender, Map.of(
                            "feature", feature,
                            "dependents", String.join(", ", resp.reloadedDependents())
                    ));
                } else {
                    send("command.reload.success", sender, Map.of("feature", feature));
                }
            }
            case NOT_LOADED -> send("command.reload.not_loaded", sender, Map.of("feature", feature));
            default -> send("command.reload.failed", sender, Map.of("feature", feature));
        }
    }

    private boolean has(CommandSource sender, String perm) {
        if (!sender.hasPermission(perm)) {
            send("general.no_permission", sender, Map.of());
            return false;
        }
        return true;
    }

    private void send(String key, CommandSource audience, Map<String, String> placeholders) {
        audience.sendMessage(plugin.getLocalizationHandler()
                .getMessage(key)
                .forAudience(audience)
                .withPlaceholders(placeholders)
                .build());
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
            // Command names: prefer keys to avoid needing a FeatureCommand type import
            Map<String, ?> cmds = feature.getLifecycleManager().getCommandManager().getRegisteredCommands();
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
            send("command.list.empty", sender, Map.of());
            return;
        }

        send("command.list.header", sender, Map.of());
        for (VelocityBaseFeature<?> feature : loadedFeatures) {
            send("command.list.entry", sender, Map.of(
                    "feature", feature.getFeatureName(),
                    "version", feature.getFeatureVersion()
            ));
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
                case "reload", "softreload", "disable" -> plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
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
