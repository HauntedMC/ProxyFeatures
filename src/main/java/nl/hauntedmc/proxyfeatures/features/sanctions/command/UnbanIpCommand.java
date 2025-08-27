package nl.hauntedmc.proxyfeatures.features.sanctions.command;

import com.velocitypowered.api.command.CommandSource;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class UnbanIpCommand extends FeatureCommand {

    private final Sanctions feature;

    public UnbanIpCommand(Sanctions feature) { this.feature = feature; }

    @Override
    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] a = inv.arguments();

        if (a.length < 1) { sendMsg(src, "sanctions.usage.unbanip"); return; }

        String ipInput = a[0];
        String ip = normalizeIp(ipInput);
        if (ip == null) { sendMsg(src, "sanctions.ip_invalid"); return; }

        boolean changed;
        try {
            changed = feature.getService().deactivateActiveBanByIp(ip);
        } catch (Throwable t) {
            feature.getLogger().error("[Sanctions] Failed to unban IP "+ip+": "+ t.getMessage());
            sendMsg(src, "sanctions.internal_error");
            return;
        }

        if (!changed) {
            sendMsg(src, "sanctions.not_banned_ip");
            return;
        }

        String actorName = (inv.source() instanceof com.velocitypowered.api.proxy.Player pl) ? pl.getUsername() : "CONSOLE";

        // Feedback to executor
        sendMsg(src, "sanctions.unbanned_ip", Map.of("ip", ip));

        // Broadcast & Discord
        feature.getService().broadcastToStaff("sanctions.announce.unbanip",
                Map.of("ip", ip, "actor", actorName));
    }

    @Override
    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.sanctions.command.unbanip");
    }

    @Override public String getName() { return "unbanip"; }
    @Override public String[] getAliases() { return new String[0]; }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] a = invocation.arguments();
        String prefix = (a.length >= 1) ? a[0] : "";
        List<String> ips = feature.getService().suggestActiveBannedIps(prefix, 20);
        return CompletableFuture.completedFuture(ips);
    }

    // helpers
    private String normalizeIp(String ip) {
        try { return InetAddress.getByName(ip).getHostAddress(); } catch (Exception e) { return null; }
    }
    private void sendMsg(CommandSource src, String key) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key).forAudience(src).build());
    }
    private void sendMsg(CommandSource src, String key, Map<String, String> ph) {
        src.sendMessage(feature.getLocalizationHandler().getMessage(key).withPlaceholders(ph).forAudience(src).build());
    }
}
