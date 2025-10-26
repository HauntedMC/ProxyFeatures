package nl.hauntedmc.proxyfeatures.features.sanctions.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class UnbanCommand implements FeatureCommand {

    private final Sanctions feature;

    public UnbanCommand(Sanctions feature) {
        this.feature = feature;
    }


    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] a = inv.arguments();

        if (a.length < 1) {
            sendMsg(src, "sanctions.usage.unban");
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
            changed = feature.getService().deactivateActiveBanForPlayer(target);
        } catch (Throwable t) {
            feature.getLogger().error("[Sanctions] Failed to unban " + target.getUsername() + ": " + t.getMessage());
            sendMsg(src, "sanctions.internal_error");
            return;
        }

        if (!changed) {
            sendMsg(src, "sanctions.not_banned_player");
            return;
        }

        String actorName = (src instanceof Player pl) ? pl.getUsername() : "CONSOLE";

        // Feedback to executor
        sendMsg(src, "sanctions.unbanned", Map.of("target", target.getUsername()));

        // Broadcast to staff
        Map<String, String> ph = Map.of("target", target.getUsername(), "actor", actorName);
        feature.getService().broadcastToStaff(
                "sanctions.announce.unban",
                MessagePlaceholders.of(ph)
        );

        feature.getDiscordService().sendUnban(target, actorName);
    }


    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.sanctions.command.unban");
    }

    public String getName() {
        return "unban";
    }

    public String[] getAliases() {
        return new String[0];
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] a = invocation.arguments();
        String prefix = (a.length >= 1) ? a[0] : "";
        List<String> names = feature.getService()
                .suggestActiveTargetNames(nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionType.BAN, prefix, 20);
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
