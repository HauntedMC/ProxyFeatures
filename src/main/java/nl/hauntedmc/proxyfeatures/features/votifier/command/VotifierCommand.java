package nl.hauntedmc.proxyfeatures.features.votifier.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;
import nl.hauntedmc.proxyfeatures.features.votifier.internal.VoteLeaderboardEntry;
import nl.hauntedmc.proxyfeatures.features.votifier.internal.VotePlayerStatsView;
import nl.hauntedmc.proxyfeatures.features.votifier.internal.VoteWinnersEntry;
import nl.hauntedmc.proxyfeatures.features.votifier.internal.VotifierService;
import org.jetbrains.annotations.NotNull;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class VotifierCommand implements BrigadierCommand {

    private static final String PERM_STATUS = "proxyfeatures.feature.votifier.command.status";
    private static final String PERM_TOP = "proxyfeatures.feature.votifier.command.top";
    private static final String PERM_DUMP = "proxyfeatures.feature.votifier.command.dump";
    private static final String PERM_STATS = "proxyfeatures.feature.votifier.command.stats";
    private static final String PERM_OTHER = "proxyfeatures.feature.votifier.command.stats.other";
    private static final String PERM_WINNERS = "proxyfeatures.feature.votifier.command.winners";

    private static final DateTimeFormatter DISPLAY_MONTH = DateTimeFormatter.ofPattern("MM-uuuu");
    private static final DateTimeFormatter INPUT_MONTH_EU = DateTimeFormatter.ofPattern("MM-uuuu");

    private final Votifier feature;

    public VotifierCommand(Votifier feature) {
        this.feature = feature;
    }

    @Override
    public @NotNull String name() {
        return "vote";
    }

    @Override
    public String description() {
        return "Vote links + Votifier admin commands.";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSource> buildTree() {
        LiteralArgumentBuilder<CommandSource> root =
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        .executes(ctx -> {
                            sendVoteLink(ctx.getSource());
                            return 1;
                        });

        root.then(LiteralArgumentBuilder.<CommandSource>literal("links")
                .executes(ctx -> {
                    sendVoteLink(ctx.getSource());
                    return 1;
                }));

        root.then(LiteralArgumentBuilder.<CommandSource>literal("leaderboard")
                .executes(ctx -> {
                    sendVoteLeaderboard(ctx.getSource());
                    return 1;
                }));

        root.then(LiteralArgumentBuilder.<CommandSource>literal("remind")
                .executes(ctx -> remindStatus(ctx.getSource()))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> suggestRemindModes(b))
                        .executes(ctx -> {
                            String mode = StringArgumentType.getString(ctx, "mode");
                            return remindSet(ctx.getSource(), mode);
                        })));

        root.then(LiteralArgumentBuilder.<CommandSource>literal("status")
                .requires(src -> src.hasPermission(PERM_STATUS))
                .executes(ctx -> {
                    String status = feature.isRunning() ? "running" : "stopped";
                    String redis = feature.getService() != null && feature.getService().isRedisEnabled() ? "on" : "off";
                    String db = feature.getService() != null && feature.getService().isStatsEnabled() ? "on" : "off";

                    ctx.getSource().sendMessage(feature.getLocalizationHandler()
                            .getMessage("votifier.command.status")
                            .with("status", status)
                            .with("host", feature.currentHost())
                            .with("port", String.valueOf(feature.currentPort()))
                            .with("timeout", String.valueOf(feature.currentTimeoutMs()))
                            .with("keybits", String.valueOf(feature.currentKeyBits()))
                            .with("redis", redis)
                            .with("db", db)
                            .forAudience(ctx.getSource())
                            .build());
                    return 1;
                }));

        root.then(LiteralArgumentBuilder.<CommandSource>literal("top")
                .requires(src -> src.hasPermission(PERM_TOP))
                .executes(ctx -> top(ctx.getSource(), currentYearMonth(), 10))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("month", StringArgumentType.word())
                        .suggests((c, b) -> suggestRelativeMonths(b))
                        .executes(ctx -> {
                            YearMonth ym = parseMonth(StringArgumentType.getString(ctx, "month"));
                            return top(ctx.getSource(), ym, 10);
                        })
                        .then(RequiredArgumentBuilder.<CommandSource, Integer>argument("limit", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> {
                                    YearMonth ym = parseMonth(StringArgumentType.getString(ctx, "month"));
                                    int limit = IntegerArgumentType.getInteger(ctx, "limit");
                                    return top(ctx.getSource(), ym, limit);
                                }))));

        root.then(LiteralArgumentBuilder.<CommandSource>literal("dump")
                .requires(src -> src.hasPermission(PERM_DUMP))
                .executes(ctx -> dump(ctx.getSource(), previousYearMonth()))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("month", StringArgumentType.word())
                        .suggests((c, b) -> suggestRelativeMonths(b))
                        .executes(ctx -> {
                            YearMonth ym = parseMonth(StringArgumentType.getString(ctx, "month"));
                            return dump(ctx.getSource(), ym);
                        })));

        root.then(LiteralArgumentBuilder.<CommandSource>literal("stats")
                .requires(src -> src.hasPermission(PERM_STATS))
                .executes(ctx -> statsSelfOrFail(ctx.getSource()))
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("player", StringArgumentType.word())
                        .suggests((c, b) -> suggestOnlinePlayers(b))
                        .requires(src -> src.hasPermission(PERM_STATS) && src.hasPermission(PERM_OTHER))
                        .executes(ctx -> {
                            String target = StringArgumentType.getString(ctx, "player");
                            return statsFor(ctx.getSource(), target);
                        })));

        root.then(LiteralArgumentBuilder.<CommandSource>literal("winners")
                .requires(src -> src.hasPermission(PERM_WINNERS))
                .executes(ctx -> winners(ctx.getSource(), 10))
                .then(RequiredArgumentBuilder.<CommandSource, Integer>argument("limit", IntegerArgumentType.integer(1, 50))
                        .executes(ctx -> {
                            int limit = IntegerArgumentType.getInteger(ctx, "limit");
                            return winners(ctx.getSource(), limit);
                        })));

        return root.build();
    }

    private int remindStatus(CommandSource src) {
        if (!(src instanceof Player p)) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.remind.player_only")
                    .forAudience(src)
                    .build());
            return 0;
        }

        VotifierService svc = feature.getService();
        if (svc == null) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.remind.unavailable")
                    .forAudience(src)
                    .build());
            return 0;
        }

        Optional<Boolean> opt = svc.getVoteRemindEnabled(p);
        if (opt.isEmpty()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.remind.unavailable")
                    .forAudience(src)
                    .build());
            return 0;
        }

        Component statusComp = feature.getLocalizationHandler()
                .getMessage(opt.get() ? "votifier.command.remind.status.enabled" : "votifier.command.remind.status.disabled")
                .forAudience(src)
                .build();

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("votifier.command.remind.status.current")
                .with("status", statusComp)
                .forAudience(src)
                .build());

        return 1;
    }

    private int remindSet(CommandSource src, String modeRaw) {
        if (!(src instanceof Player p)) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.remind.player_only")
                    .forAudience(src)
                    .build());
            return 0;
        }

        VotifierService svc = feature.getService();
        if (svc == null) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.remind.unavailable")
                    .forAudience(src)
                    .build());
            return 0;
        }

        VotifierService.RemindMode mode = parseRemindMode(modeRaw);
        if (mode == null) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.remind.usage")
                    .forAudience(src)
                    .build());
            return 0;
        }

        Optional<Boolean> updated = svc.setVoteRemind(p, mode);
        if (updated.isEmpty()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.remind.unavailable")
                    .forAudience(src)
                    .build());
            return 0;
        }

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage(updated.get() ? "votifier.command.remind.enabled" : "votifier.command.remind.disabled")
                .forAudience(src)
                .build());

        return 1;
    }

    private static VotifierService.RemindMode parseRemindMode(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "on", "aan", "true", "1" -> VotifierService.RemindMode.ON;
            case "off", "uit", "false", "0" -> VotifierService.RemindMode.OFF;
            case "toggle" -> VotifierService.RemindMode.TOGGLE;
            default -> null;
        };
    }

    private CompletableFuture<Suggestions> suggestRemindModes(SuggestionsBuilder b) {
        b.suggest("on");
        b.suggest("off");
        b.suggest("toggle");
        return b.buildFuture();
    }

    private void sendVoteLink(CommandSource src) {
        String url = feature.getConfigHandler().node("vote").get("url").as(String.class, "");
        if (url == null || url.isBlank()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.vote.not_configured")
                    .forAudience(src)
                    .build());
            return;
        }

        String cleanUrl = url.trim();

        Component urlComp = Component.text(cleanUrl, NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(cleanUrl))
                .hoverEvent(HoverEvent.showText(Component.text("Open vote link", NamedTextColor.GRAY)));

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("votifier.vote.header")
                .forAudience(src)
                .build());

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("votifier.vote.line")
                .with("url", urlComp)
                .forAudience(src)
                .build());
    }

    private void sendVoteLeaderboard(CommandSource src) {
        String url = feature.getConfigHandler().node("vote").get("leaderboard_url").as(String.class, "");
        if (url == null || url.isBlank()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.vote.leaderboard.not_configured")
                    .forAudience(src)
                    .build());
            return;
        }

        String cleanUrl = url.trim();

        Component urlComp = Component.text(cleanUrl, NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(cleanUrl))
                .hoverEvent(HoverEvent.showText(Component.text("Open leaderboard", NamedTextColor.GRAY)));

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("votifier.vote.leaderboard.header")
                .forAudience(src)
                .build());

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("votifier.vote.line")
                .with("url", urlComp)
                .forAudience(src)
                .build());
    }

    private int statsSelfOrFail(CommandSource src) {
        if (!(src instanceof Player p)) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.stats.player_only")
                    .forAudience(src)
                    .build());
            return 0;
        }
        return statsFor(src, p.getUsername());
    }

    private int statsFor(CommandSource src, String username) {
        if (feature.getService() == null || !feature.getService().isStatsEnabled()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.stats.not_found")
                    .with("player", username == null ? "" : username)
                    .forAudience(src)
                    .build());
            return 0;
        }

        Optional<VotePlayerStatsView> opt = feature.getService().getPlayerStats(username);
        if (opt.isEmpty()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.stats.not_found")
                    .with("player", username == null ? "" : username)
                    .forAudience(src)
                    .build());
            return 0;
        }

        var s = opt.get();

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("votifier.command.stats.header")
                .with("player", s.username())
                .forAudience(src)
                .build());

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("votifier.command.stats.line1")
                .with("month_votes", String.valueOf(s.monthVotes()))
                .with("best_month_votes", String.valueOf(s.highestMonthVotes()))
                .with("total_votes", String.valueOf(s.totalVotes()))
                .forAudience(src)
                .build());

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("votifier.command.stats.line2")
                .with("streak", String.valueOf(s.voteStreak()))
                .with("best_streak", String.valueOf(s.bestVoteStreak()))
                .forAudience(src)
                .build());

        return 1;
    }

    private int dump(CommandSource src, YearMonth ym) {
        try {
            if (feature.getService() == null) throw new IllegalStateException("service not running");
            String file = feature.getService().dumpTopForMonth(ym);

            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.dump.ok")
                    .with("file", file)
                    .forAudience(src)
                    .build());
        } catch (Throwable t) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.dump.fail")
                    .with("error", safeMsg(t))
                    .forAudience(src)
                    .build());
        }
        return 1;
    }

    private int top(CommandSource src, YearMonth ym, int limit) {
        if (feature.getService() == null || !feature.getService().isStatsEnabled()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.top.empty")
                    .with("month", formatMonth(ym))
                    .forAudience(src)
                    .build());
            return 1;
        }

        List<VoteLeaderboardEntry> list = feature.getService().topForMonth(ym, limit);
        if (list.isEmpty()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.top.empty")
                    .with("month", formatMonth(ym))
                    .forAudience(src)
                    .build());
            return 1;
        }

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("votifier.command.top.header")
                .with("limit", String.valueOf(limit))
                .with("month", formatMonth(ym))
                .forAudience(src)
                .build());

        int rank = 1;
        for (VoteLeaderboardEntry e : list) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.top.entry")
                    .with("rank", String.valueOf(rank))
                    .with("player", e.username())
                    .with("votes", String.valueOf(e.monthVotes()))
                    .forAudience(src)
                    .build());
            rank++;
        }

        return 1;
    }

    private int winners(CommandSource src, int limit) {
        if (feature.getService() == null || !feature.getService().isStatsEnabled()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.winners.empty")
                    .forAudience(src)
                    .build());
            return 1;
        }

        int lim = Math.max(1, Math.min(50, limit));
        List<VoteWinnersEntry> list = feature.getService().winnersLeaderboard(lim);

        if (list.isEmpty()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.winners.empty")
                    .forAudience(src)
                    .build());
            return 1;
        }

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("votifier.command.winners.header")
                .with("limit", String.valueOf(lim))
                .forAudience(src)
                .build());

        int rank = 1;
        for (VoteWinnersEntry e : list) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.command.winners.entry")
                    .with("rank", String.valueOf(rank))
                    .with("player", e.username())
                    .with("gold", String.valueOf(e.firstPlaces()))
                    .with("silver", String.valueOf(e.secondPlaces()))
                    .with("bronze", String.valueOf(e.thirdPlaces()))
                    .forAudience(src)
                    .build());
            rank++;
        }

        return 1;
    }

    private CompletableFuture<Suggestions> suggestRelativeMonths(SuggestionsBuilder b) {
        b.suggest("current");
        b.suggest("previous");
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestOnlinePlayers(SuggestionsBuilder b) {
        final Locale L = Locale.ROOT;
        String prefix = b.getRemaining().toLowerCase(L);

        ProxyFeatures.getProxyInstance().getAllPlayers().stream()
                .map(Player::getUsername)
                .filter(n -> n != null && n.toLowerCase(L).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(b::suggest);

        return b.buildFuture();
    }

    private YearMonth parseMonth(String raw) {
        YearMonth now = currentYearMonth();

        if (raw == null || raw.isBlank()) return now;

        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.equals("current")) return now;
        if (s.equals("previous")) return now.minusMonths(1);

        try {
            return YearMonth.parse(raw.trim());
        } catch (DateTimeParseException ignored) {
        }

        try {
            return YearMonth.parse(raw.trim(), INPUT_MONTH_EU);
        } catch (DateTimeParseException ignored) {
        }

        return now;
    }

    private YearMonth currentYearMonth() {
        return feature.getService() != null ? feature.getService().currentYearMonth() : YearMonth.now();
    }

    private YearMonth previousYearMonth() {
        YearMonth cur = currentYearMonth();
        return cur.minusMonths(1);
    }

    private static String formatMonth(YearMonth ym) {
        if (ym == null) return "";
        return DISPLAY_MONTH.format(ym);
    }

    private static String safeMsg(Throwable t) {
        return (t == null || t.getMessage() == null) ? "unknown" : t.getMessage();
    }
}