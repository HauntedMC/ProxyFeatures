package nl.hauntedmc.proxyfeatures.framework.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.proxyfeatures.features.VelocityBaseFeature;
import nl.hauntedmc.proxyfeatures.framework.loader.FeatureDescriptor;
import nl.hauntedmc.proxyfeatures.framework.loader.disable.FeatureDisableResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.enable.FeatureEnableResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.reload.FeatureReloadResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.softreload.FeatureSoftReloadResponse;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * ProxyFeatures framework command (status, list, info, enable/disable, reload, softreload, reloadlocal).
 */
public final class ProxyFeaturesCommand implements BrigadierCommand {

    private final ProxyFeatures plugin;

    public ProxyFeaturesCommand(ProxyFeatures plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String name() {
        return "proxyfeatures";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSource> buildTree() {
        LiteralArgumentBuilder<CommandSource> root =
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        .requires(src -> src.hasPermission("proxyfeatures.use"))
                        .executes(ctx -> {
                            CommandSource sender = ctx.getSource();
                            sender.sendMessage(plugin.getLocalizationHandler()
                                    .getMessage("general.usage")
                                    .forAudience(sender)
                                    .build());
                            return 1;
                        });

        // /proxyfeatures status
        root.then(LiteralArgumentBuilder.<CommandSource>literal("status")
                .requires(src -> src.hasPermission("proxyfeatures.command.status"))
                .executes(ctx -> {
                    sendPluginStatus(ctx.getSource());
                    return 1;
                })
        );

        // /proxyfeatures list  (compact one-liner) + flag "--version"
        root.then(LiteralArgumentBuilder.<CommandSource>literal("list")
                .requires(src -> src.hasPermission("proxyfeatures.command.list"))
                .then(LiteralArgumentBuilder.<CommandSource>literal("--version")
                        .executes(ctx -> {
                            listLoadedFeaturesOneLine(ctx.getSource(), true);
                            return 1;
                        }))
                .executes(ctx -> {
                    listLoadedFeaturesOneLine(ctx.getSource(), false);
                    return 1;
                })
        );

        // /proxyfeatures info <feature>
        root.then(LiteralArgumentBuilder.<CommandSource>literal("info")
                .requires(src -> src.hasPermission("proxyfeatures.command.info"))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("feature", StringArgumentType.word())
                        .suggests((ctx, b) -> suggestAnyFeature(b))
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "feature");
                            handleInfo(ctx.getSource(), name);
                            return 1;
                        }))
        );

        // /proxyfeatures reloadlocal
        root.then(LiteralArgumentBuilder.<CommandSource>literal("reloadlocal")
                .requires(src -> src.hasPermission("proxyfeatures.command.reloadlocal"))
                .executes(ctx -> {
                    CommandSource sender = ctx.getSource();
                    try {
                        plugin.getLocalizationHandler().reloadLocalization();
                        sender.sendMessage(plugin.getLocalizationHandler()
                                .getMessage("command.reloadlocal.success")
                                .forAudience(sender)
                                .build());
                    } catch (Exception t) {
                        plugin.getLogger().warn("Localization reload failed: {}", t.getMessage());
                        sender.sendMessage(plugin.getLocalizationHandler()
                                .getMessage("command.reloadlocal.fail")
                                .forAudience(sender)
                                .build());
                    }
                    return 1;
                })
        );

        // Shared suggestion providers
        SuggestionProvider<CommandSource> featureSuggestLoaded = (ctx, b) -> suggestLoadedFeatures(b);
        SuggestionProvider<CommandSource> featureSuggestEnable = (ctx, b) -> suggestEnableCandidates(b);

        // /proxyfeatures softreload <feature>
        root.then(LiteralArgumentBuilder.<CommandSource>literal("softreload")
                .requires(src -> src.hasPermission("proxyfeatures.command.reload"))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("feature", StringArgumentType.word())
                        .suggests(featureSuggestLoaded)
                        .executes(ctx -> {
                            String feature = StringArgumentType.getString(ctx, "feature");
                            handleSoftReload(ctx.getSource(), feature);
                            return 1;
                        }))
        );

        // /proxyfeatures reload <feature>
        root.then(LiteralArgumentBuilder.<CommandSource>literal("reload")
                .requires(src -> src.hasPermission("proxyfeatures.command.reload"))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("feature", StringArgumentType.word())
                        .suggests(featureSuggestLoaded)
                        .executes(ctx -> {
                            String feature = StringArgumentType.getString(ctx, "feature");
                            handleReload(ctx.getSource(), feature);
                            return 1;
                        }))
        );

        // /proxyfeatures enable <feature>
        root.then(LiteralArgumentBuilder.<CommandSource>literal("enable")
                .requires(src -> src.hasPermission("proxyfeatures.command.enable"))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("feature", StringArgumentType.word())
                        .suggests(featureSuggestEnable)
                        .executes(ctx -> {
                            String feature = StringArgumentType.getString(ctx, "feature");
                            handleEnable(ctx.getSource(), feature);
                            return 1;
                        }))
        );

        // /proxyfeatures disable <feature>
        root.then(LiteralArgumentBuilder.<CommandSource>literal("disable")
                .requires(src -> src.hasPermission("proxyfeatures.command.disable"))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("feature", StringArgumentType.word())
                        .suggests(featureSuggestLoaded)
                        .executes(ctx -> {
                            String feature = StringArgumentType.getString(ctx, "feature");
                            handleDisable(ctx.getSource(), feature);
                            return 1;
                        }))
        );

        return root.build();
    }

    /* ============================ Handlers ============================ */

    private void handleInfo(CommandSource sender, String featureName) {
        if (featureName == null || featureName.isBlank()) {
            sender.sendMessage(Component.text("Please provide a feature name.", NamedTextColor.RED));
            return;
        }

        var reg = plugin.getFeatureLoadManager().getFeatureRegistry();

        // Direct lookup (exact) among loaded
        VelocityBaseFeature<?> loaded = reg.getLoadedFeature(featureName);

        // Case-insensitive fallback among loaded
        if (loaded == null) {
            loaded = reg.getLoadedFeatures().stream()
                    .filter(f -> featureName.equalsIgnoreCase(f.getFeatureName()))
                    .findFirst().orElse(null);
        }

        if (loaded != null) {
            String name = Objects.toString(loaded.getFeatureName(), featureName);
            String version = Objects.toString(loaded.getFeatureVersion(), "?");
            List<String> pluginDeps = Optional.ofNullable(loaded.getPluginDependencies()).orElseGet(List::of);
            List<String> featureDeps = Optional.ofNullable(loaded.getDependencies()).orElseGet(List::of);

            sendFeatureInfo(sender, name, true, version, pluginDeps, featureDeps);
            return;
        }

        // Not loaded: resolve against available feature keys (case-insensitive, aliases)
        String availableKey = plugin.getFeatureLoadManager().resolveFeatureKey(featureName);

        if (availableKey != null) {
            FeatureDescriptor descriptor = reg.getAvailableFeature(availableKey);
            if (descriptor == null) {
                sendFeatureInfo(sender, availableKey, false, "?", List.of(), List.of());
                return;
            }

            sendFeatureInfo(
                    sender,
                    descriptor.featureName(),
                    false,
                    descriptor.featureVersion(),
                    List.copyOf(descriptor.pluginDependencies()),
                    List.copyOf(descriptor.featureDependencies())
            );
        } else {
            sender.sendMessage(Component.text("Feature not found: ", NamedTextColor.RED)
                    .append(Component.text(featureName, NamedTextColor.WHITE)));
        }
    }

    private void sendFeatureInfo(CommandSource sender,
                                 String name,
                                 boolean enabled,
                                 String version,
                                 List<String> pluginDeps,
                                 List<String> featureDeps) {

        Component msg = Component.text("Feature: ", NamedTextColor.GOLD)
                .append(Component.text(name, NamedTextColor.YELLOW))
                .append(Component.text("\n  • Status: ", NamedTextColor.GRAY))
                .append(Component.text(enabled ? "enabled" : "disabled",
                        enabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.text("\n  • Version: ", NamedTextColor.GRAY))
                .append(Component.text(version == null ? "?" : "v" + version, NamedTextColor.WHITE))
                .append(Component.text("\n  • Plugin deps: ", NamedTextColor.GRAY))
                .append(renderCsvColored(pluginDeps, NamedTextColor.AQUA, NamedTextColor.DARK_GRAY, true))
                .append(Component.text("\n  • Feature deps: ", NamedTextColor.GRAY))
                .append(renderCsvColored(featureDeps, NamedTextColor.GREEN, NamedTextColor.DARK_GRAY, true));

        sender.sendMessage(msg);
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

    private void sendPluginStatus(CommandSource sender) {
        List<VelocityBaseFeature<?>> loaded = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures();
        List<String> cmds = new ArrayList<>();
        int loadedCount = loaded.size();
        int tasks = 0, listeners = 0, commands = 0, conns = 0;

        for (VelocityBaseFeature<?> f : loaded) {
            var registered = f.getLifecycleManager().getCommandManager().getRegisteredCommands();
            commands += (registered != null ? registered.size() : 0);
            if (registered != null) cmds.addAll(registered.keySet());
            tasks += f.getLifecycleManager().getTaskManager().getActiveTaskCount();
            listeners += f.getLifecycleManager().getListenerManager().getRegisteredListenerCount();
            conns += f.getLifecycleManager().getDataManager().getActiveConnCount();
        }

        sender.sendMessage(Component.text("ProxyFeatures Status:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("- Number of loaded features: " + loadedCount, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of active database connections: " + conns, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of active tasks: " + tasks, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of registered listeners: " + listeners, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Number of registered commands: " + commands, NamedTextColor.WHITE));
        sender.sendMessage(Component.text("- Registered commands: " + cmds, NamedTextColor.WHITE));
    }

    /* ============================ Lists & Rendering ============================ */

    private void listLoadedFeaturesOneLine(CommandSource sender, boolean withVersion) {
        List<VelocityBaseFeature<?>> loaded = new ArrayList<>(plugin.getFeatureLoadManager()
                .getFeatureRegistry().getLoadedFeatures());

        // Alphabetize by feature name (case-insensitive, null-safe)
        loaded.sort(Comparator.comparing(
                f -> Optional.ofNullable(f.getFeatureName()).orElse(""),
                String.CASE_INSENSITIVE_ORDER
        ));

        int n = loaded.size();
        Component header = Component.text("Enabled Features (", NamedTextColor.YELLOW)
                .append(Component.text(n, NamedTextColor.AQUA))
                .append(Component.text("): ", NamedTextColor.YELLOW));

        Component list = Component.empty();
        for (int i = 0; i < loaded.size(); i++) {
            VelocityBaseFeature<?> f = loaded.get(i);
            String name = Objects.toString(f.getFeatureName(), "?");
            String version = Objects.toString(f.getFeatureVersion(), "?");

            if (i > 0) list = list.append(Component.text(", ", NamedTextColor.DARK_GRAY));

            Component entry = Component.text(name, NamedTextColor.GREEN);
            if (withVersion) {
                entry = entry.append(Component.text(" (", NamedTextColor.DARK_GRAY))
                        .append(Component.text("v" + version, NamedTextColor.WHITE))
                        .append(Component.text(")", NamedTextColor.DARK_GRAY));
            }
            list = list.append(entry);
        }

        sender.sendMessage(header.append(list));
    }

    private Component renderCsvColored(List<String> items, NamedTextColor itemColor, NamedTextColor commaColor, boolean showNone) {
        if (items == null || items.isEmpty()) {
            return Component.text(showNone ? "none" : "", NamedTextColor.DARK_GRAY);
        }
        Component out = Component.empty();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) out = out.append(Component.text(", ", commaColor));
            out = out.append(Component.text(items.get(i), itemColor));
        }
        return out;
    }

    /* ============================ Suggestions ============================ */

    private CompletableFuture<Suggestions> suggestLoadedFeatures(SuggestionsBuilder builder) {
        final Locale L = Locale.ROOT;
        String prefix = builder.getRemaining().toLowerCase(L);

        var names = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                .map(VelocityBaseFeature::getFeatureName)
                .filter(Objects::nonNull)
                .filter(n -> n.toLowerCase(L).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        for (String n : names) builder.suggest(n);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestEnableCandidates(SuggestionsBuilder builder) {
        final Locale L = Locale.ROOT;
        String prefix = builder.getRemaining().toLowerCase(L);

        Set<String> loadedLower = plugin.getFeatureLoadManager().getFeatureRegistry().getLoadedFeatures().stream()
                .map(VelocityBaseFeature::getFeatureName)
                .filter(Objects::nonNull)
                .map(s -> s.toLowerCase(L))
                .collect(Collectors.toSet());

        var names = plugin.getFeatureLoadManager().getFeatureRegistry().getAvailableFeatures().keySet().stream()
                .filter(Objects::nonNull)
                .filter(name -> !loadedLower.contains(name.toLowerCase(L)))
                .filter(name -> name.toLowerCase(L).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        for (String n : names) builder.suggest(n);
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestAnyFeature(SuggestionsBuilder builder) {
        final Locale L = Locale.ROOT;
        String prefix = builder.getRemaining().toLowerCase(L);

        var reg = plugin.getFeatureLoadManager().getFeatureRegistry();

        // Enabled
        Set<String> suggested = new HashSet<>();
        reg.getLoadedFeatures().forEach(f -> {
            String name = f.getFeatureName();
            if (name == null) return;
            if (name.toLowerCase(L).startsWith(prefix)) {
                builder.suggest(name); // plain suggestion (Velocity lacks Paper's tooltip serializer)
                suggested.add(name.toLowerCase(L));
            }
        });

        // Disabled
        for (String name : reg.getAvailableFeatures().keySet()) {
            if (name == null) continue;
            String low = name.toLowerCase(L);
            if (!low.startsWith(prefix)) continue;
            if (suggested.contains(low)) continue;
            builder.suggest(name);
        }
        return builder.buildFuture();
    }

    @Override
    public String description() {
        return "ProxyFeatures framework command (status, list, info, enable/disable, reload, softreload, reloadlocal).";
    }
}
