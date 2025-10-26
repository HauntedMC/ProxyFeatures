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
import nl.hauntedmc.proxyfeatures.framework.loader.disable.FeatureDisableResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.enable.FeatureEnableResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.reload.FeatureReloadResponse;
import nl.hauntedmc.proxyfeatures.framework.loader.softreload.FeatureSoftReloadResponse;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Framework root command defined as a Brigadier command.
 * Root: /proxyfeatures
 * <p>
 * Subcommands:
 * - status
 * - list
 * - enable <feature>
 * - disable <feature>
 * - reload <feature>
 * - softreload <feature>
 * - reloadlocal
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
        // Root node with global gate, shows usage when executed bare
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

        // /proxyfeatures list
        root.then(LiteralArgumentBuilder.<CommandSource>literal("list")
                .requires(src -> src.hasPermission("proxyfeatures.command.list"))
                .executes(ctx -> {
                    listLoadedFeatures(ctx.getSource());
                    return 1;
                })
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
                    } catch (Throwable t) {
                        plugin.getLogger().warn("Localization reload failed: {}", t.getMessage());
                        sender.sendMessage(plugin.getLocalizationHandler()
                                .getMessage("command.reloadlocal.fail")
                                .forAudience(sender)
                                .build());
                    }
                    return 1;
                })
        );

        // Argument suggestions used by multiple subs
        SuggestionProvider<CommandSource> featureSuggestLoaded = (ctx, builder) -> suggestLoadedFeatures(builder);
        SuggestionProvider<CommandSource> featureSuggestEnable = (ctx, builder) -> suggestEnableCandidates(builder);

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

    // Optional: description exposed to some registrars/clients
    @Override
    public String description() {
        return "ProxyFeatures framework command (status, list, enable/disable, reload, softreload, reloadlocal).";
    }
}
