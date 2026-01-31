package nl.hauntedmc.proxyfeatures.features.antivpn.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.command.brigadier.BrigadierCommand;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.AntiVPNService;
import nl.hauntedmc.proxyfeatures.features.antivpn.internal.IpWhitelist;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.configurate.serialize.SerializationException;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /antivpn stats
 * /antivpn check <ip|player>
 * /antivpn whitelist <list|add|remove> [entry]
 * /antivpn cache <stats|clear>
 * Includes live config mutation for whitelist entries (IP/CIDR) + immediate in-memory refresh.
 */
public final class AntiVPNCommand implements BrigadierCommand {

    private final AntiVPN feature;
    private final ProxyServer proxy;

    public AntiVPNCommand(AntiVPN feature) {
        this.feature = feature;
        this.proxy = ProxyFeatures.getProxyInstance();
    }

    @Override
    public @NotNull String name() {
        return "antivpn";
    }

    @Override
    public String description() {
        return "AntiVPN admin commands (stats/check/whitelist/cache).";
    }

    @Override
    public @NotNull LiteralCommandNode<CommandSource> buildTree() {
        LiteralArgumentBuilder<CommandSource> root =
                LiteralArgumentBuilder.<CommandSource>literal(name())
                        .requires(src -> src.hasPermission("proxyfeatures.feature.antivpn.command"))
                        .executes(ctx -> {
                            ctx.getSource().sendMessage(feature.getLocalizationHandler()
                                    .getMessage("antivpn.command.usage")
                                    .forAudience(ctx.getSource())
                                    .build());
                            return 1;
                        });

        root.then(LiteralArgumentBuilder.<CommandSource>literal("stats")
                .executes(ctx -> {
                    var s = feature.getMetrics().snapshot();
                    ctx.getSource().sendMessage(feature.getLocalizationHandler()
                            .getMessage("antivpn.command.stats")
                            .with("checks", String.valueOf(s.checks()))
                            .with("allowed", String.valueOf(s.allowed()))
                            .with("denied_region", String.valueOf(s.deniedRegion()))
                            .with("denied_vpn", String.valueOf(s.deniedVpn()))
                            .with("errors", String.valueOf(s.errors()))
                            .with("cache_mem_hits", String.valueOf(s.cacheMemHits()))
                            .with("cache_disk_hits", String.valueOf(s.cacheDiskHits()))
                            .forAudience(ctx.getSource())
                            .build());
                    return 1;
                }));

        root.then(LiteralArgumentBuilder.<CommandSource>literal("check")
                .then(RequiredArgumentBuilder.<CommandSource, String>argument("target", StringArgumentType.greedyString())
                        .suggests((c, b) -> suggestPlayersOrIp(b))
                        .executes(ctx -> {
                            String target = StringArgumentType.getString(ctx, "target").trim();
                            handleCheck(ctx.getSource(), target);
                            return 1;
                        })));

        // whitelist
        root.then(LiteralArgumentBuilder.<CommandSource>literal("whitelist")
                .executes(ctx -> {
                    ctx.getSource().sendMessage(feature.getLocalizationHandler()
                            .getMessage("antivpn.command.usage")
                            .forAudience(ctx.getSource()).build());
                    return 1;
                })
                .then(LiteralArgumentBuilder.<CommandSource>literal("list")
                        .executes(ctx -> {
                            List<String> entries = feature.getService().getWhitelist().entries();
                            String joined = entries.isEmpty() ? "-" : String.join(", ", entries);
                            ctx.getSource().sendMessage(feature.getLocalizationHandler()
                                    .getMessage("antivpn.command.whitelist.list")
                                    .with("count", String.valueOf(entries.size()))
                                    .with("entries", joined)
                                    .forAudience(ctx.getSource())
                                    .build());
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<CommandSource>literal("add")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("entry", StringArgumentType.greedyString())
                                .suggests((c, b) -> suggestWhitelistExamples(b))
                                .executes(ctx -> {
                                    String entry = StringArgumentType.getString(ctx, "entry");
                                    whitelistAdd(ctx.getSource(), entry);
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<CommandSource>literal("remove")
                        .then(RequiredArgumentBuilder.<CommandSource, String>argument("entry", StringArgumentType.greedyString())
                                .suggests((c, b) -> suggestExistingWhitelist(b))
                                .executes(ctx -> {
                                    String entry = StringArgumentType.getString(ctx, "entry");
                                    whitelistRemove(ctx.getSource(), entry);
                                    return 1;
                                }))));

        // cache
        root.then(LiteralArgumentBuilder.<CommandSource>literal("cache")
                .then(LiteralArgumentBuilder.<CommandSource>literal("stats")
                        .executes(ctx -> {
                            var cache = feature.getService().getCache();
                            ctx.getSource().sendMessage(feature.getLocalizationHandler()
                                    .getMessage("antivpn.command.cache.stats")
                                    .with("mem_size", String.valueOf(cache.memEstimatedSize()))
                                    .with("disk_entries", String.valueOf(cache.diskEntryCount()))
                                    .with("inflight", String.valueOf(cache.inflightCount()))
                                    .forAudience(ctx.getSource())
                                    .build());
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<CommandSource>literal("clear")
                        .executes(ctx -> {
                            feature.getService().getCache().clearAll();
                            ctx.getSource().sendMessage(feature.getLocalizationHandler()
                                    .getMessage("antivpn.command.cache.cleared")
                                    .forAudience(ctx.getSource())
                                    .build());
                            return 1;
                        })));

        return root.build();
    }

    /* ============================ Suggestions ============================ */

    private CompletableFuture<Suggestions> suggestWhitelistExamples(SuggestionsBuilder b) {
        b.suggest("1.2.3.4");
        b.suggest("1.2.3.0/24");
        b.suggest("2001:db8::/32");
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestExistingWhitelist(SuggestionsBuilder b) {
        for (String e : feature.getService().getWhitelist().entries()) {
            b.suggest(e);
        }
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPlayersOrIp(SuggestionsBuilder b) {
        for (Player p : proxy.getAllPlayers()) b.suggest(p.getUsername());
        b.suggest("1.2.3.4");
        b.suggest("1.2.3.0/24");
        return b.buildFuture();
    }

    /* ============================ Actions ============================ */

    private void handleCheck(CommandSource src, String target) {
        String ip = resolveToIp(target);

        if (ip == null) {
            src.sendMessage(Component.text("Unknown player or invalid IP."));
            return;
        }

        AntiVPNService svc = feature.getService();
        svc.debugLookup(ip, true, true)
                .thenAccept(debug -> {
                    String country = debug.result().countryUpper();
                    String vpn = debug.result().vpn() == null ? "unknown" : String.valueOf(debug.result().vpn());
                    String provider = debug.result().providerId() == null ? "-" : debug.result().providerId();
                    String cache = debug.cacheSource();

                    src.sendMessage(feature.getLocalizationHandler()
                            .getMessage("antivpn.command.check.result")
                            .with("ip", ip)
                            .with("country", country.isBlank() ? "unknown" : country)
                            .with("vpn", vpn)
                            .with("provider", provider)
                            .with("cache", cache)
                            .forAudience(src)
                            .build());
                })
                .exceptionally(ex -> {
                    src.sendMessage(feature.getLocalizationHandler()
                            .getMessage("antivpn.command.check.error")
                            .with("error", ex.getMessage() == null ? "unknown" : ex.getMessage())
                            .forAudience(src)
                            .build());
                    return null;
                });
    }

    /**
     * Live config mutation (Fix requested): add IP/CIDR to whitelist and refresh immediately.
     */
    private void whitelistAdd(CommandSource src, String rawEntry) {
        var normOpt = IpWhitelist.normalizeEntry(rawEntry);
        if (normOpt.isEmpty()) {
            src.sendMessage(Component.text("Invalid entry. Use an IP or CIDR (e.g. 1.2.3.0/24)."));
            return;
        }
        String norm = normOpt.get();

        List<String> current = readWhitelistList();
        for (String e : current) {
            if (e.equalsIgnoreCase(norm)) {
                src.sendMessage(feature.getLocalizationHandler()
                        .getMessage("antivpn.command.whitelist.already")
                        .with("entry", norm)
                        .forAudience(src)
                        .build());
                return;
            }
        }

        current = new ArrayList<>(current);
        current.add(norm);

        // Persist to BOTH new + legacy key for backward compatibility.
        List<String> finalCurrent = current;
        feature.getConfigHandler().batch(b -> {
            try {
                b.put("whitelist.entries", finalCurrent);
            } catch (SerializationException e) {
                throw new RuntimeException(e);
            }
        });

        feature.getService().refreshWhitelistFromConfig();

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("antivpn.command.whitelist.added")
                .with("entry", norm)
                .forAudience(src)
                .build());
    }

    /**
     * Live config mutation: remove IP/CIDR from whitelist and refresh immediately.
     */
    private void whitelistRemove(CommandSource src, String rawEntry) {
        var normOpt = IpWhitelist.normalizeEntry(rawEntry);
        if (normOpt.isEmpty()) {
            src.sendMessage(Component.text("Invalid entry."));
            return;
        }
        String norm = normOpt.get();

        List<String> current = readWhitelistList();
        int before = current.size();

        List<String> next = new ArrayList<>();
        for (String e : current) {
            if (!e.equalsIgnoreCase(norm)) next.add(e);
        }

        if (next.size() == before) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("antivpn.command.whitelist.not_found")
                    .with("entry", norm)
                    .forAudience(src)
                    .build());
            return;
        }

        feature.getConfigHandler().batch(b -> {
            try {
                b.put("whitelist.entries", next);
            } catch (SerializationException e) {
                throw new RuntimeException(e);
            }
        });

        feature.getService().refreshWhitelistFromConfig();

        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("antivpn.command.whitelist.removed")
                .with("entry", norm)
                .forAudience(src)
                .build());
    }

    private List<String> readWhitelistList() {
        List<String> list = feature.getConfigHandler().node("whitelist").get("entries").listOf(String.class);
        if (list == null) list = new ArrayList<>();

        // de-dup preserve order
        var set = new java.util.LinkedHashSet<String>();
        for (String s : list) if (s != null && !s.isBlank()) set.add(s.trim());
        return new ArrayList<>(set);
    }

    private String resolveToIp(String target) {
        if (target == null || target.isBlank()) return null;

        // If it's an online player, resolve to their remote IP
        var pOpt = proxy.getPlayer(target);
        if (pOpt.isPresent()) {
            InetSocketAddress a = pOpt.get().getRemoteAddress();
            return a.getAddress().getHostAddress();
        }

        // Otherwise treat as ip/cidr literal (we only need IP here)
        String t = target.trim();
        if (t.contains("/")) {
            // For /check, we expect a single IP; reject CIDR here.
            return null;
        }

        var norm = IpWhitelist.normalizeEntry(t);
        return norm.orElse(null);
    }
}
