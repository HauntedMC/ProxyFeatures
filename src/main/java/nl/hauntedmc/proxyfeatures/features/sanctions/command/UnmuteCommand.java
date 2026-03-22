package nl.hauntedmc.proxyfeatures.features.sanctions.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class UnmuteCommand implements FeatureCommand {

    private final Sanctions feature;

    public UnmuteCommand(Sanctions feature) {
        this.feature = feature;
    }


    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] a = inv.arguments();

        if (a.length < 1) {
            sendMsg(src, "sanctions.usage.unmute");
            return;
        }

        String targetName = a[0];
        var targetOpt = feature.getServiceLookup().byName(targetName);
        if (targetOpt.isEmpty()) {
            sendMsg(src, "sanctions.not_found");
            return;
        }
        PlayerEntity target = targetOpt.get();

        boolean changed;
        try {
            changed = feature.getService().deactivateActiveMuteForPlayer(target);
        } catch (Exception t) {
            feature.getLogger().error("[Sanctions] Failed to unmute " + target.getUsername() + ": " + t.getMessage());
            sendMsg(src, "sanctions.internal_error");
            return;
        }

        if (!changed) {
            sendMsg(src, "sanctions.not_muted");
            return;
        }

        String actorName = (src instanceof Player pl) ? pl.getUsername() : "CONSOLE";

        // Feedback to executor
        sendMsg(src, "sanctions.unmuted", Map.of("target", target.getUsername()));


        // Announce to staff
        Map<String, String> ph = Map.of("target", target.getUsername(), "actor", actorName);
        feature.getService().broadcastToStaff(
                "sanctions.announce.unmute",
                MessagePlaceholders.of(ph)
        );

        // Notify player if online
        ProxyFeatures.getProxyInstance().getPlayer(java.util.UUID.fromString(target.getUuid()))
                .ifPresent(pl -> pl.sendMessage(feature.getLocalizationHandler()
                        .getMessage("sanctions.notify.unmuted")
                        .forAudience(pl).build()));
        feature.getDiscordService().sendUnmute(target, actorName);
    }


    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.sanctions.command.unmute");
    }

    public String getName() {
        return "unmute";
    }

    public String[] getAliases() {
        return new String[0];
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] a = invocation.arguments();
        String prefix = (a.length >= 1) ? a[0] : "";
        List<String> names = feature.getService()
                .suggestActiveTargetNames(SanctionType.MUTE, prefix, 20);
        return CompletableFuture.completedFuture(names);
    }

    // helpers
    private void sendMsg(CommandSource src, String key) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(src).build());
    }

    private void sendMsg(CommandSource src, String key, Map<String, String> ph) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key).withPlaceholders(MessagePlaceholders.of(ph)).forAudience(src).build());
    }
}
