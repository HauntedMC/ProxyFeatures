package nl.hauntedmc.proxyfeatures.features.commandhider.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandSource;
import nl.hauntedmc.proxyfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.proxyfeatures.features.commandhider.CommandHider;
import nl.hauntedmc.proxyfeatures.features.commandhider.internal.HiderHandler;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Admin tooling for CommandHider.
 * Uses config batch mutations (same style as AntiVPN whitelist editing).
 * /commandhider list
 * /commandhider add <command>
 * /commandhider remove <command>
 */
public final class CommandHiderCommand implements BrigadierCommand {

    private static final String PERM_BASE = "proxyfeatures.feature.commandhider.command";
    private static final String PERM_LIST = PERM_BASE + ".list";
    private static final String PERM_ADD = PERM_BASE + ".add";
    private static final String PERM_REMOVE = PERM_BASE + ".remove";

    private final CommandHider feature;

    public CommandHiderCommand(CommandHider feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() {
        return "commandhider";
    }

    @Override
    public String description() {
        return "CommandHider admin commands (list/add/remove).";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSource> buildTree() {
        LiteralArgumentBuilder<CommandSource> root =
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        .requires(src -> src.hasPermission(PERM_BASE))
                        .executes(ctx -> {
                            sendUsage(ctx.getSource());
                            return 1;
                        });

        root.then(LiteralArgumentBuilder.<CommandSource>literal("list")
                .requires(src -> src.hasPermission(PERM_LIST))
                .executes(ctx -> {
                    List<String> entries = feature.getHiderHandler().hiddenCommandsList();
                    String joined = entries.isEmpty() ? "-" : String.join(", ", entries);
                    ctx.getSource().sendMessage(feature.getLocalizationHandler()
                            .getMessage("commandhider.command.list")
                            .with("count", String.valueOf(entries.size()))
                            .with("commands", joined)
                            .forAudience(ctx.getSource())
                            .build());
                    return 1;
                }));

        root.then(LiteralArgumentBuilder.<CommandSource>literal("add")
                .requires(src -> src.hasPermission(PERM_ADD))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("command", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "command");
                            addHidden(ctx.getSource(), raw);
                            return 1;
                        })));

        root.then(LiteralArgumentBuilder.<CommandSource>literal("remove")
                .requires(src -> src.hasPermission(PERM_REMOVE))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("command", StringArgumentType.greedyString())
                        .suggests((c, b) -> suggestHidden(b))
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "command");
                            removeHidden(ctx.getSource(), raw);
                            return 1;
                        })));
        return root.build();
    }

    private void sendUsage(CommandSource src) {
        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("commandhider.command.usage")
                .forAudience(src)
                .build());
    }

    private void addHidden(CommandSource src, String raw) {
        String norm = HiderHandler.normalizeCommandLiteral(raw);
        if (norm.isEmpty()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("commandhider.command.invalid")
                    .forAudience(src)
                    .build());
            return;
        }

        List<String> current = readHiddenList();
        for (String e : current) {
            if (e.equalsIgnoreCase(norm)) {
                src.sendMessage(feature.getLocalizationHandler()
                        .getMessage("commandhider.command.already")
                        .with("command", norm)
                        .forAudience(src)
                        .build());
                return;
            }
        }

        List<String> next = new ArrayList<>(current);
        next.add(norm);
        next = HiderHandler.normalizeToUniqueList(next);

        persistHiddenList(next);
        feature.getHiderHandler().refreshFromConfig();

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("commandhider.command.added")
                .with("command", norm)
                .forAudience(src)
                .build());
    }

    private void removeHidden(CommandSource src, String raw) {
        String norm = HiderHandler.normalizeCommandLiteral(raw);
        if (norm.isEmpty()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("commandhider.command.invalid")
                    .forAudience(src)
                    .build());
            return;
        }

        List<String> current = readHiddenList();
        if (current.isEmpty()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("commandhider.command.not_found")
                    .with("command", norm)
                    .forAudience(src)
                    .build());
            return;
        }

        int before = current.size();
        List<String> next = new ArrayList<>();
        for (String e : current) {
            if (!e.equalsIgnoreCase(norm)) {
                next.add(e);
            }
        }

        if (next.size() == before) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("commandhider.command.not_found")
                    .with("command", norm)
                    .forAudience(src)
                    .build());
            return;
        }

        next = HiderHandler.normalizeToUniqueList(next);

        persistHiddenList(next);
        feature.getHiderHandler().refreshFromConfig();

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("commandhider.command.removed")
                .with("command", norm)
                .forAudience(src)
                .build());
    }

    private List<String> readHiddenList() {
        // Typed ConfigView API (no unsafe casts).
        List<String> list = feature.getConfigHandler().getList("hidden-commands", String.class, List.of());
        if (list == null || list.isEmpty()) return List.of();
        return HiderHandler.normalizeToUniqueList(list);
    }

    private void persistHiddenList(List<String> normalized) {
        List<String> finalList = normalized == null ? List.of() : List.copyOf(normalized);

        feature.getConfigHandler().batch(b -> {
            try {
                b.put("hidden-commands", finalList);
            } catch (SerializationException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private CompletableFuture<Suggestions> suggestHidden(SuggestionsBuilder b) {
        for (String s : feature.getHiderHandler().hiddenCommandsList()) {
            b.suggest(s);
        }
        return b.buildFuture();
    }
}
