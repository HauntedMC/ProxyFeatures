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
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import nl.hauntedmc.proxyfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.proxyfeatures.features.broadcast.Broadcast;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * /broadcastproxy <chat|title> <message...>
 *  - chat: sends a chat broadcast (supports &-color codes)
 *  - title: sends a title; split main/subtitle with a single '|'
 */
public final class BroadcastProxyCommand implements BrigadierCommand {

    private final Broadcast feature;
    private final ProxyServer proxy;
    private final LegacyComponentSerializer legacyAmp = LegacyComponentSerializer.legacyAmpersand();

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
        b.suggest("&aServer &frestart &fover &c5 &fminuten");
        b.suggest("&eNieuwe &aupdate&f: &b/perks &fvoor info");
        b.suggest("&dEvent &fin &a/spawn &fbegint nu!");
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestTitleExamples(SuggestionsBuilder b) {
        b.suggest("&6Welkom op HauntedMC | &7Veel plezier!");
        b.suggest("&cRestart inkomend | &7Sla veilig uit en reconnect zo");
        b.suggest("&aDubbele XP! | &7Alle minigames dit uur");
        return b.buildFuture();
    }

    /* ============================ Execution ============================ */

    private void broadcastChat(String msg, CommandSource src) {
        Component comp = legacyAmp.deserialize(msg);
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

        Component titleComp = legacyAmp.deserialize(titlePart);
        Component subComp = legacyAmp.deserialize(subPart);

        int fadeIn = (int) feature.getConfigHandler().getSetting("title_fade_in");
        int stay = (int) feature.getConfigHandler().getSetting("title_stay");
        int fadeOut = (int) feature.getConfigHandler().getSetting("title_fade_out");

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
