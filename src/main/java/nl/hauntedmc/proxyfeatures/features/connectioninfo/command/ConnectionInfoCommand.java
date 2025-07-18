package nl.hauntedmc.proxyfeatures.features.connectioninfo.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.connectioninfo.ConnectionInfo;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ConnectionInfoCommand extends FeatureCommand {
    private final ConnectionInfo feature;

    public ConnectionInfoCommand(ConnectionInfo feature) {
        this.feature = feature;
    }

    @Override
    public String getName() {
        return "connectioninfo";
    }

    @Override
    public String[] getAliases() {
        return new String[]{""};
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return invocation.source()
                    .hasPermission("proxyfeatures.feature.connectioninfo.command.connectioninfo");
        }
        return invocation.source()
                .hasPermission("proxyfeatures.feature.connectioninfo.command.connectioninfo.other");
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (args.length > 1) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("connectioninfo.cmd_usage")
                    .forAudience(src)
                    .build());
            return;
        }

        boolean other = args.length == 1;
        String targetName;
        if (other) {
            targetName = args[0];
        } else {
            if (!(src instanceof Player)) {
                src.sendMessage(feature.getLocalizationHandler()
                        .getMessage("connectioninfo.cmd_usage")
                        .forAudience(src)
                        .build());
                return;
            }
            targetName = ((Player) src).getUsername();
        }

        Optional<Player> optTarget = feature.getPlugin()
                .getProxy()
                .getPlayer(targetName);

        if (optTarget.isEmpty()) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("connectioninfo.cmd_playerNotFound")
                    .withPlaceholders(Map.of("player", targetName))
                    .forAudience(src)
                    .build());
            return;
        }

        Player target = optTarget.get();

        // gather data
        int ping = Math.toIntExact(target.getPing());
        ProtocolVersion proto = target.getProtocolVersion();
        String protoDesc = proto.name() + " (" + proto.getMostRecentSupportedVersion() + ")";
        InetSocketAddress remote = target.getRemoteAddress();
        String remoteAddr = remote.getAddress().getHostAddress() + ":" + remote.getPort();
        String virtualHost = target.getVirtualHost()
                .map(vh -> vh.getHostString() + ":" + vh.getPort())
                .orElse("N/A");

        // compute session duration
        String sessionDuration = feature.getSessionHandler()
                .getJoinTime(target.getUniqueId())
                .map(join -> {
                    Duration d = Duration.between(join, Instant.now());
                    long h = d.toHours();
                    long m = d.toMinutes() % 60;
                    long s = d.getSeconds() % 60;
                    return String.format("%02d:%02d:%02d", h, m, s);
                })
                .orElse("N/A");

        // header
        String subject = other ? " van " + target.getUsername() : "";
        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("connectioninfo.cmd_header")
                .withPlaceholders(Map.of("subject", subject))
                .forAudience(src)
                .build());

        // entries
        sendEntry(src, "Ping", ping + " ms");
        sendEntry(src, "Protocol", protoDesc);
        sendEntry(src, "Remote Address", remoteAddr);
        sendEntry(src, "Virtual Host", virtualHost);
        sendEntry(src, "Sessieduur", sessionDuration);
    }

    private void sendEntry(CommandSource src, String setting, String value) {
        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("connectioninfo.cmd_entry")
                .withPlaceholders(Map.of(
                        "setting", setting,
                        "value", value
                ))
                .forAudience(src)
                .build());
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0 || args[0].isEmpty()) {
            List<String> names = feature.getPlugin()
                    .getProxy()
                    .getAllPlayers()
                    .stream()
                    .map(Player::getUsername)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(names);
        }
        String partial = args[0].toLowerCase();
        List<String> matching = feature.getPlugin()
                .getProxy()
                .getAllPlayers()
                .stream()
                .map(Player::getUsername)
                .filter(n -> n.toLowerCase().startsWith(partial))
                .collect(Collectors.toList());
        return CompletableFuture.completedFuture(matching);
    }
}
