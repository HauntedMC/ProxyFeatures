package nl.hauntedmc.proxyfeatures.features.clientinfo.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.proxyfeatures.features.clientinfo.ClientInfo;
import nl.hauntedmc.proxyfeatures.features.clientinfo.internal.ClientInfoAdvisor;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ClientInfoBrigadierCommand implements BrigadierCommand {

    // Base and subcommand permissions
    private static final String PERM_BASE = "proxyfeatures.feature.clientinfo.command";
    private static final String PERM_INFO = "proxyfeatures.feature.clientinfo.command.info";
    private static final String PERM_RECOMMEND = "proxyfeatures.feature.clientinfo.command.recommend";
    private static final String PERM_TOGGLE = "proxyfeatures.feature.clientinfo.command.toggle";
    private static final String PERM_HELP = "proxyfeatures.feature.clientinfo.command.help";

    // Viewing other players (applies to any <player> usage)
    private static final String PERM_OTHER = "proxyfeatures.feature.clientinfo.command.other";

    private final ClientInfo feature;
    private final ClientInfoAdvisor advisor;

    public ClientInfoBrigadierCommand(ClientInfo feature, ClientInfoAdvisor advisor) {
        this.feature = feature;
        this.advisor = advisor;
    }

    @Override
    public @NotNull String name() {
        return "clientinfo";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSource> buildTree() {
        SuggestionProvider<CommandSource> playerSuggest = (ctx, b) -> suggestOnlinePlayers(b);

        LiteralArgumentBuilder<CommandSource> root =
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        .requires(src -> src.hasPermission(PERM_BASE))
                        .executes(ctx -> {
                            CommandSource src = ctx.getSource();
                            if (src instanceof Player p) {
                                p.sendMessage(advisor.buildFullView(p, p));
                            } else {
                                src.sendMessage(feature.getLocalizationHandler()
                                        .getMessage("clientinfo.cmd_usage")
                                        .forAudience(src)
                                        .build());
                            }
                            return 1;
                        });

        // /clientinfo <player> (requires base + other)
        root.then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                .suggests(playerSuggest)
                .requires(src -> src.hasPermission(PERM_BASE) && src.hasPermission(PERM_OTHER))
                .executes(ctx -> {
                    CommandSource src = ctx.getSource();
                    String target = StringArgumentType.getString(ctx, "player");
                    return advisor.sendFullViewOther(src, target);
                }));

        // /clientinfo info (same as /clientinfo)
        // /clientinfo info <player> (requires info + other)
        root.then(LiteralArgumentBuilder.<CommandSource>literal("info")
                .requires(src -> src.hasPermission(PERM_INFO))
                .executes(ctx -> {
                    CommandSource src = ctx.getSource();
                    if (src instanceof Player p) {
                        p.sendMessage(advisor.buildFullView(p, p));
                    } else {
                        src.sendMessage(feature.getLocalizationHandler()
                                .getMessage("clientinfo.cmd_usage")
                                .forAudience(src)
                                .build());
                    }
                    return 1;
                })
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .suggests(playerSuggest)
                        .requires(src -> src.hasPermission(PERM_INFO) && src.hasPermission(PERM_OTHER))
                        .executes(ctx -> {
                            CommandSource src = ctx.getSource();
                            String target = StringArgumentType.getString(ctx, "player");
                            return advisor.sendFullViewOther(src, target);
                        }))
        );

        // /clientinfo recommend
        // /clientinfo recommend <player> (requires recommend + other)
        root.then(LiteralArgumentBuilder.<CommandSource>literal("recommend")
                .requires(src -> src.hasPermission(PERM_RECOMMEND))
                .executes(ctx -> {
                    CommandSource src = ctx.getSource();
                    if (src instanceof Player p) {
                        p.sendMessage(advisor.buildRecommendationsOnly(p, p));
                    } else {
                        src.sendMessage(feature.getLocalizationHandler()
                                .getMessage("clientinfo.cmd_usage")
                                .forAudience(src)
                                .build());
                    }
                    return 1;
                })
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .suggests(playerSuggest)
                        .requires(src -> src.hasPermission(PERM_RECOMMEND) && src.hasPermission(PERM_OTHER))
                        .executes(ctx -> {
                            CommandSource src = ctx.getSource();
                            String target = StringArgumentType.getString(ctx, "player");
                            return advisor.sendRecommendationsOnlyOther(src, target);
                        }))
        );

        // /clientinfo toggle
        root.then(LiteralArgumentBuilder.<CommandSource>literal("toggle")
                .requires(src -> src.hasPermission(PERM_TOGGLE))
                .executes(ctx -> {
                    CommandSource src = ctx.getSource();
                    if (src instanceof Player p) {
                        advisor.toggleNotifications(p);
                    } else {
                        src.sendMessage(feature.getLocalizationHandler()
                                .getMessage("general.player_command")
                                .forAudience(src)
                                .build());
                    }
                    return 1;
                })
        );

        // /clientinfo help
        root.then(LiteralArgumentBuilder.<CommandSource>literal("help")
                .requires(src -> src.hasPermission(PERM_HELP))
                .executes(ctx -> {
                    CommandSource src = ctx.getSource();
                    src.sendMessage(advisor.buildHelp(src));
                    return 1;
                })
        );

        return root.build();
    }

    private CompletableFuture<Suggestions> suggestOnlinePlayers(SuggestionsBuilder builder) {
        final Locale L = Locale.ROOT;
        String prefix = builder.getRemaining().toLowerCase(L);

        ProxyFeatures.getProxyInstance().getAllPlayers().stream()
                .map(Player::getUsername)
                .filter(n -> n != null && n.toLowerCase(L).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(builder::suggest);

        return builder.buildFuture();
    }
}
