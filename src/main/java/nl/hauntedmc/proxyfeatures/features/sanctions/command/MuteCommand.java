package nl.hauntedmc.proxyfeatures.features.sanctions.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class MuteCommand implements FeatureCommand {

    private final Sanctions feature;

    public MuteCommand(Sanctions feature) {
        this.feature = feature;
    }


    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] a = inv.arguments();
        if (a.length < 3) {
            sendMsg(src, "sanctions.usage.mute");
            return;
        }

        String targetName = a[0];
        String length = a[1];
        String reason = feature.getService().sanitizeReason(joinAfter(a, 2));

        var targetOpt = feature.getServiceLookup().byName(targetName);
        if (targetOpt.isEmpty()) {
            sendMsg(src, "sanctions.not_found");
            return;
        }
        PlayerEntity target = targetOpt.get();

        if (src instanceof Player p && p.getUniqueId().toString().equals(target.getUuid())) {
            sendMsg(src, "sanctions.self");
            return;
        }
        if (feature.getService().findActiveMuteByPlayer(target).isPresent()) {
            sendMsg(src, "sanctions.already_muted");
            return;
        }

        // Exempt protection (if online and has exempt permission)
        if (feature.getService().isTargetExempt(target.getUuid())) {
            sendMsg(src, "sanctions.exempt_target");
            return;
        }

        Instant expires;
        try {
            expires = feature.getService().parseLengthToExpiry(length);
        } catch (Exception e) {
            sendMsg(src, "sanctions.invalid_length");
            return;
        }

        if (expires == null && !src.hasPermission("proxyfeatures.feature.sanctions.command.mute.perm")) {
            sendMsg(src, "sanctions.perm_block");
            return;
        }

        PlayerEntity actorEnt = (src instanceof Player pl)
                ? feature.getService().getPlayerByUuid(pl.getUniqueId().toString()).orElse(null)
                : null;
        String actorName = (src instanceof Player pl) ? pl.getUsername() : "CONSOLE";

        try {
            SanctionEntity s = feature.getService().createMute(target, reason, actorEnt, actorName, expires);

            var ph = feature.getService().placeholdersFor(s);
            feature.getService().broadcastToStaff(
                    s.isPermanent() ? "sanctions.announce.mute.perm" : "sanctions.announce.mute.temp",
                    ph);

            // Disconnect the player immediately with a dedicated mute screen (multi-line)
            ProxyFeatures.getProxyInstance().getPlayer(UUID.fromString(target.getUuid()))
                    .ifPresent(pl -> {
                        String key = s.isPermanent()
                                ? "sanctions.disconnect.muted.perm"
                                : "sanctions.disconnect.muted.temp";
                        pl.disconnect(feature.getLocalizationHandler().getMessage(key)
                                .withPlaceholders(ph)
                                .forAudience(pl).build());
                    });

            feature.getDiscordService().sendMute(s);
        } catch (IllegalStateException dup) {
            sendMsg(src, "sanctions.already_muted");
        } catch (Exception t) {
            feature.getLogger().error("[Sanctions] Failed to create mute for " + target.getUsername() + ": " + t.getMessage());
            sendMsg(src, "sanctions.internal_error");
        }
    }


    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.sanctions.command.mute");
    }

    public String getName() {
        return "mute";
    }

    public String[] getAliases() {
        return new String[0];
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] a = invocation.arguments();
        // Keep these short, staff can still type custom lengths
        List<String> durations = List.of("7d", "30d", "p");

        if (a.length == 0 || (a.length == 1 && a[0].isEmpty())) {
            List<String> names = ProxyFeatures.getProxyInstance().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(names);
        }
        if (a.length == 1) {
            String partial = a[0].toLowerCase(Locale.ROOT);
            List<String> names = ProxyFeatures.getProxyInstance().getAllPlayers().stream()
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
        for (int i = idx; i < arr.length; i++) {
            if (i > idx) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }
}
