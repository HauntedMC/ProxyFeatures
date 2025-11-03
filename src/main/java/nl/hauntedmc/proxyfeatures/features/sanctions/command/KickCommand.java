package nl.hauntedmc.proxyfeatures.features.sanctions.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class KickCommand implements FeatureCommand {

    private final Sanctions feature;

    public KickCommand(Sanctions feature) {
        this.feature = feature;
    }


    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] a = inv.arguments();
        if (a.length < 2) {
            sendMsg(src, "sanctions.usage.kick");
            return;
        }

        String targetName = a[0];
        String reason = feature.getService().sanitizeReason(joinAfter(a, 1));

        var targetOnline = feature.getPlugin().getProxyInstance().getPlayer(targetName).orElse(null);
        PlayerEntity target = (targetOnline != null)
                ? feature.getService().getPlayerByUuid(targetOnline.getUniqueId().toString()).orElse(null)
                : feature.getServiceLookup().byName(targetName).orElse(null);

        if (target == null) {
            sendMsg(src, "sanctions.not_found");
            return;
        }

        // Exempt protection if online & exempt
        if (feature.getService().isTargetExempt(target.getUuid())) {
            sendMsg(src, "sanctions.exempt_target");
            return;
        }

        PlayerEntity actorEnt = (src instanceof Player pl)
                ? feature.getService().getPlayerByUuid(pl.getUniqueId().toString()).orElse(null)
                : null;
        String actorName = (src instanceof Player pl) ? pl.getUsername() : "CONSOLE";

        try {
            feature.getService().createKick(target, reason, actorEnt, actorName);

            Map<String, String> ph = Map.of("target", target.getUsername(), "reason", reason, "actor", actorName);
            feature.getService().broadcastToStaff("sanctions.announce.kick",
                    MessagePlaceholders.of(ph));

            if (targetOnline != null) {
                targetOnline.disconnect(feature.getLocalizationHandler().getMessage("sanctions.notify.kick")
                        .with("reason", reason)
                        .forAudience(targetOnline).build());
            }
            feature.getDiscordService().sendKick(target, reason, actorName);
        } catch (Throwable t) {
            feature.getLogger().error("[Sanctions] Failed to kick " + target.getUsername() + ": " + t.getMessage());
            sendMsg(src, "sanctions.internal_error");
        }
    }


    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.sanctions.command.kick");
    }

    public String getName() {
        return "kick";
    }

    public String[] getAliases() {
        return new String[0];
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] a = invocation.arguments();

        if (a.length == 0 || (a.length == 1 && a[0].isEmpty())) {
            List<String> names = feature.getPlugin().getProxyInstance().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(names);
        }
        if (a.length == 1) {
            String partial = a[0].toLowerCase(Locale.ROOT);
            List<String> names = feature.getPlugin().getProxyInstance().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(names);
        }
        // reason is free text; no suggestions
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
