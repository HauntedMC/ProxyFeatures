package nl.hauntedmc.proxyfeatures.features.broadcast.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import nl.hauntedmc.proxyfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.proxyfeatures.api.util.text.format.ComponentFormatter;
import nl.hauntedmc.proxyfeatures.api.util.text.format.TextFormatter;
import nl.hauntedmc.proxyfeatures.features.broadcast.Broadcast;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * /broadcastproxy <chat|title> <message...>
 * - chat: sends a chat broadcast (supports &-color codes)
 * - title: sends a title; split main/subtitle with a single '|'
 */
public final class BroadcastProxyCommand implements BrigadierCommand {

    private final Broadcast feature;
    private final ProxyServer proxy;

    public BroadcastProxyCommand(Broadcast feature) {
        this.feature = feature;
        this.proxy = feature.getPlugin().getProxy();
    }

    @Override
    public @NotNull String name() {
        return "broadcastproxy";
    }

    @Override
    public String description() {
        return "Broadcast a message to all players (chat or title).";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSource> buildTree() {
        // Root
        LiteralArgumentBuilder<CommandSource> root =
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        .requires(src -> src.hasPermission("proxyfeatures.feature.broadcast.command.broadcastproxy"))
                        .executes(ctx -> {
                            // Show usage when no subcommand given
                            ctx.getSource().sendMessage(feature.getLocalizationHandler()
                                    .getMessage("broadcast.usage")
                                    .forAudience(ctx.getSource())
                                    .build());
                            return 1;
                        });

        // Suggestion providers (modern UX-style hints)
        SuggestionProvider<CommandSource> chatMessageSugg = (c, b) -> suggestChatExamples(b);
        SuggestionProvider<CommandSource> titleMessageSugg = (c, b) -> suggestTitleExamples(b);

        // /broadcastproxy chat <message...>
        root.then(LiteralArgumentBuilder.<CommandSource>literal("chat")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("message", StringArgumentType.greedyString())
                        .suggests(chatMessageSugg)
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            broadcastChat(msg, ctx.getSource());
                            return 1;
                        })));

        // /broadcastproxy title <message...>  (use "|" to separate title|subtitle)
        root.then(LiteralArgumentBuilder.<CommandSource>literal("title")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("message", StringArgumentType.greedyString())
                        .suggests(titleMessageSugg)
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            broadcastTitle(msg, ctx.getSource());
                            return 1;
                        })));

        return root.build();
    }

    /* ============================ Suggestions ============================ */

    private CompletableFuture<Suggestions> suggestChatExamples(SuggestionsBuilder b) {
        // Clean, modern suggestions that show typical admin use-cases.
        b.suggest("&f&l[ANNOUNCEMENT] &r&d");
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestTitleExamples(SuggestionsBuilder b) {
        b.suggest("<title> | <subtitle>");
        b.suggest("&f&l[ANNOUNCEMENT] | ");
        return b.buildFuture();
    }

    /* ============================ Execution ============================ */

    private void broadcastChat(String msg, CommandSource src) {
        Component comp = ComponentFormatter.deserialize(msg)
                .expect(TextFormatter.InputFormat.MIXED_INPUT)
                .features(ComponentFormatter.ALL_DEFAULTS()).autoLinkUrls(true).toComponent();
        proxy.getAllPlayers().forEach(p -> p.sendMessage(comp));
        acknowledge(src);
    }

    private void broadcastTitle(String msg, CommandSource src) {
        String titlePart;
        String subPart;

        int pipe = msg.indexOf('|');
        if (pipe >= 0) {
            titlePart = msg.substring(0, pipe).trim();
            subPart = msg.substring(pipe + 1).trim();
        } else {
            titlePart = msg;
            subPart = "";
        }

        Component titleComp = ComponentFormatter.deserialize(titlePart)
                .expect(TextFormatter.InputFormat.MIXED_INPUT)
                .features(ComponentFormatter.ALL_DEFAULTS()).toComponent();
        Component subComp = ComponentFormatter.deserialize(subPart)
                .expect(TextFormatter.InputFormat.MIXED_INPUT)
                .features(ComponentFormatter.ALL_DEFAULTS()).toComponent();

        int fadeIn = (int) feature.getConfigHandler().get("title_fade_in");
        int stay = (int) feature.getConfigHandler().get("title_stay");
        int fadeOut = (int) feature.getConfigHandler().get("title_fade_out");

        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L));

        Title title = Title.title(titleComp, subComp, times);

        proxy.getAllPlayers().forEach(p -> p.showTitle(title));
        acknowledge(src);
    }

    /* ============================ Helpers ============================ */

    private void acknowledge(CommandSource src) {
        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("broadcast.sent")
                .forAudience(src)
                .build());
    }
}
