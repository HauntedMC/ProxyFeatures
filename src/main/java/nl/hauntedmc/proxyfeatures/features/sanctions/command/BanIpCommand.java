package nl.hauntedmc.proxyfeatures.features.sanctions.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;

import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BanIpCommand extends FeatureCommand {

    private final Sanctions feature;
    public BanIpCommand(Sanctions feature) { this.feature = feature; }

    @Override
    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] a = inv.arguments();
        if (a.length < 3) { sendMsg(src, "sanctions.usage.banip"); return; }

        String ip = a[0];
        if (!isValidIp(ip)) { sendMsg(src, "sanctions.ip_invalid"); return; }

        Instant expires;
        try { expires = feature.getService().parseLengthToExpiry(a[1]); }
        catch (Exception e) { sendMsg(src, "sanctions.invalid_length"); return; }

        String reason = joinAfter(a, 2);

        if (feature.getService().findActiveBanByIp(ip).isPresent()) {
            sendMsg(src, "sanctions.already_banned"); return;
        }

        PlayerEntity actorEnt = (src instanceof Player pl)
                ? feature.getService().getPlayerByUuid(pl.getUniqueId().toString()).orElse(null)
                : null;
        String actorName = (src instanceof Player pl) ? pl.getUsername() : "CONSOLE";

        SanctionEntity s = feature.getService().createBanForIp(ip, reason, actorEnt, actorName, expires);

        var ph = feature.getService().placeholdersFor(s);
        feature.getService().broadcastToStaff(
                s.isPermanent() ? "sanctions.announce.banip.perm" : "sanctions.announce.banip.temp",
                ph);
    }

    @Override
    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.sanctions.command.banip");
    }

    @Override public String getName() { return "banip"; }
    @Override public String[] getAliases() { return new String[0]; }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] a = invocation.arguments();
        List<String> durations = List.of("7d","30d", "p");

        if (a.length == 0 || (a.length == 1 && a[0].isEmpty())) {
            // we don't try to guess IPs; no suggestions for the IP slot
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        if (a.length == 1) {
            // still the IP slot; no suggestions
            return CompletableFuture.completedFuture(Collections.emptyList());
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

    private boolean isValidIp(String ip) {
        try { InetAddress.getByName(ip); return true; } catch (Exception e) { return false; }
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
