package nl.hauntedmc.proxyfeatures.features.sanctions.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class WarnCommand implements FeatureCommand {

    private final Sanctions feature;

    public WarnCommand(Sanctions feature) {
        this.feature = feature;
    }


    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] a = inv.arguments();
        if (a.length < 2) {
            sendMsg(src, "sanctions.usage.warn");
            return;
        }

        String targetName = a[0];
        String reason = feature.getService().sanitizeReason(joinAfter(a, 1));

        var targetOpt = feature.getServiceLookup().byName(targetName);
        if (targetOpt.isEmpty()) {
            sendMsg(src, "sanctions.not_found");
            return;
        }
        PlayerEntity target = targetOpt.get();

        if (feature.getService().isTargetExempt(target.getUuid())) {
            sendMsg(src, "sanctions.exempt_target");
            return;
        }

        PlayerEntity actorEnt = (src instanceof Player pl)
                ? feature.getService().getPlayerByUuid(pl.getUniqueId().toString()).orElse(null)
                : null;
        String actorName = (src instanceof Player pl) ? pl.getUsername() : "CONSOLE";

        try {
            feature.getService().createWarn(target, reason, actorEnt, actorName);

            var ph = Map.of("target", target.getUsername(), "reason", reason, "actor", actorName);
            feature.getService().broadcastToStaff("sanctions.announce.warn", MessagePlaceholders.of(ph));

            feature.getPlugin().getProxy().getPlayer(UUID.fromString(target.getUuid()))
                    .ifPresent(pl -> pl.sendMessage(feature.getLocalizationHandler()
                            .getMessage("sanctions.notify.warn")
                            .with("reason", reason)
                            .forAudience(pl).build()));
            feature.getDiscordService().sendWarn(target, reason, actorName);
        } catch (Throwable t) {
            feature.getLogger().error("[Sanctions] Failed to warn " + target.getUsername() + ": " + t.getMessage());
            sendMsg(src, "sanctions.internal_error");
        }
    }


    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.sanctions.command.warn");
    }

    public String getName() {
        return "warn";
    }

    public String[] getAliases() {
        return new String[0];
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] a = invocation.arguments();

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
