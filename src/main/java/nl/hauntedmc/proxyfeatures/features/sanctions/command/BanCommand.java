package nl.hauntedmc.proxyfeatures.features.sanctions.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BanCommand extends FeatureCommand {

    private final Sanctions feature;

    public BanCommand(Sanctions feature) { this.feature = feature; }

    @Override
    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] a = inv.arguments();

        if (a.length < 3) { sendMsg(src, "sanctions.usage.ban"); return; }

        String targetName  = a[0];
        String lengthToken = a[1];
        String reason      = joinAfter(a, 2);

        var targetOpt = feature.getServiceLookup().byName(targetName);
        if (targetOpt.isEmpty()) { sendMsg(src, "sanctions.not_found"); return; }
        PlayerEntity target = targetOpt.get();

        if (src instanceof Player p && p.getUniqueId().toString().equals(target.getUuid())) {
            sendMsg(src, "sanctions.self"); return;
        }

        Instant expires;
        try { expires = feature.getService().parseLengthToExpiry(lengthToken); }
        catch (Exception ex) { sendMsg(src, "sanctions.invalid_length"); return; }

        if (expires == null && !src.hasPermission("proxyfeatures.feature.sanctions.command.ban.perm")) {
            sendMsg(src, "sanctions.perm_block");
            return;
        }

        if (feature.getService().findActiveBanByPlayer(target).isPresent()) {
            sendMsg(src, "sanctions.already_banned"); return;
        }

        PlayerEntity actorEnt = (src instanceof Player pl)
                ? feature.getService().getPlayerByUuid(pl.getUniqueId().toString()).orElse(null)
                : null;
        String actorName = (src instanceof Player pl) ? pl.getUsername() : "CONSOLE";

        SanctionEntity s = feature.getService().createBanForPlayer(target, reason, actorEnt, actorName, expires);

        var ph = feature.getService().placeholdersFor(s);
        feature.getService().broadcastToStaff(
                s.isPermanent() ? "sanctions.announce.ban.perm" : "sanctions.announce.ban.temp",
                ph);

        feature.getPlugin().getProxy().getPlayer(UUID.fromString(target.getUuid()))
                .ifPresent(pl -> {
                    String key = s.isPermanent() ? "sanctions.disconnect.banned.perm" : "sanctions.disconnect.banned.temp";
                    pl.disconnect(feature.getLocalizationHandler().getMessage(key)
                            .withPlaceholders(ph).forAudience(pl).build());
                });
        feature.getDiscordService().sendBan(s);
    }

    @Override
    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.sanctions.command.ban");
    }

    @Override public String getName() { return "ban"; }
    @Override public String[] getAliases() { return new String[0]; }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] a = invocation.arguments();
        // common duration suggestions
        List<String> durations = List.of("7d","30d","p");

        if (a.length == 0 || (a.length == 1 && a[0].isEmpty())) {
            List<String> names = feature.getPlugin().getProxy().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(names);
        }
        if (a.length == 1) {
            String partial = a[0].toLowerCase(Locale.ROOT);
            List<String> names = feature.getPlugin().getProxy().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(names);
        }
        if (a.length == 2) {
            String partial = a[1].toLowerCase(Locale.ROOT);
            List<String> sugg = durations.stream()
                    .filter(d -> d.startsWith(partial))
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(sugg);
        }
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    private void sendMsg(CommandSource src, String key) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(src).build());
    }
    private String joinAfter(String[] arr, int idx) {
        StringBuilder sb = new StringBuilder();
        for (int i = idx; i < arr.length; i++) { if (i > idx) sb.append(' '); sb.append(arr[i]); }
        return sb.toString();
    }
}
