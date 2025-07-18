package nl.hauntedmc.proxyfeatures.features.connectioninfo.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.connectioninfo.ConnectionInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class PingCommand extends FeatureCommand {
    private final ConnectionInfo feature;
    private final int greenThreshold;
    private final int yellowThreshold;

    public PingCommand(ConnectionInfo feature) {
        this.feature = feature;
        greenThreshold = (int) feature.getConfigHandler().getSetting("ping_threshold_green");
        yellowThreshold = (int) feature.getConfigHandler().getSetting("ping_threshold_yellow");
    }

    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String[] getAliases() {
        return new String[]{""};
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        if (invocation.arguments().length == 0) {
            return invocation.source().hasPermission("proxyfeatures.feature.connectioninfo.command.ping");
        }

        return invocation.source().hasPermission("proxyfeatures.feature.connectioninfo.command.ping.other");
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        // too many args?
        if (args.length > 1) {
            src.sendMessage(feature.getLocalizationHandler().getMessage("connectioninfo.ping_usage").forAudience(src).build());
            return;
        }

        boolean other = args.length == 1;
        String targetName;
        Optional<Player> optTarget;

        if (other) {
            targetName = args[0];
            optTarget = feature.getPlugin().getProxy().getPlayer(targetName);
        } else {
            if (!(src instanceof Player)) {
                src.sendMessage(feature.getLocalizationHandler().getMessage("connectioninfo.ping_usage").forAudience(src).build());
                return;
            }
            targetName = ((Player) src).getUsername();
            optTarget = Optional.of((Player) src);
        }

        if (optTarget.isEmpty()) {
            src.sendMessage(feature.getLocalizationHandler().getMessage("connectioninfo.ping_notFound").withPlaceholders(Map.of("player", targetName)).forAudience(src).build());
            return;
        }

        Player target = optTarget.get();
        int ping = Math.toIntExact(target.getPing());

        // determine color code
        String color;
        if (ping <= greenThreshold) {
            color = "&a";
        } else if (ping <= yellowThreshold) {
            color = "&e";
        } else {
            color = "&c";
        }

        // pick message key and placeholders
        String key = other ? "connectioninfo.ping_other" : "connectioninfo.ping_self";

        Map<String, String> ph = new HashMap<>();
        ph.put("color", color);
        ph.put("ping", String.valueOf(ping));

        if (other) {
            ph.put("player", target.getUsername());
        }

        src.sendMessage(feature.getLocalizationHandler().getMessage(key).withPlaceholders(ph).forAudience(src).build());
    }


    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();

        // If no argument or empty, suggest all online players
        if (args.length == 0 || args[0].isEmpty()) {
            List<String> allNames = feature.getPlugin().getProxy().getAllPlayers().stream().map(Player::getUsername).collect(Collectors.toList());
            return CompletableFuture.completedFuture(allNames);
        }

        // Otherwise, filter by what they've started typing
        String partial = args[0].toLowerCase();
        List<String> matching = feature.getPlugin().getProxy().getAllPlayers().stream().map(Player::getUsername).filter(name -> name.toLowerCase().startsWith(partial)).collect(Collectors.toList());
        return CompletableFuture.completedFuture(matching);
    }

}
