package nl.hauntedmc.proxyfeatures.features.queue.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.queue.Queue;
import nl.hauntedmc.proxyfeatures.features.queue.QueueManager;
import nl.hauntedmc.proxyfeatures.features.queue.model.ServerQueue;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /queue           -> positie in je huidige wachtrij
 * /queue top <srv> -> toon kop van de wachtrij (staff)
 * /queue skip <p>  -> zet speler vooraan in zijn/haar wachtrij (staff)
 */
public class QueueCommand extends FeatureCommand {
    private final Queue feature;
    private final QueueManager manager;

    public QueueCommand(Queue feature, QueueManager manager) {
        this.feature = feature;
        this.manager = manager;
    }

    @Override
    public String getName() { return "queue"; }

    @Override
    public String[] getAliases() { return new String[]{ "q" }; }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.queue.command");
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        List<String> args = List.of(invocation.arguments());

        if (args.isEmpty()) {
            // /queue -> status eigen wachtrij
            if (!(source instanceof Player p)) {
                source.sendMessage(feature.getLocalizationHandler()
                        .getMessage("queue.cmd_notPlayer")
                        .forAudience(source)
                        .build());
                return;
            }
            Optional<String> qServer = manager.findQueueOf(p.getUniqueId());
            if (qServer.isEmpty()) {
                source.sendMessage(feature.getLocalizationHandler()
                        .getMessage("queue.status.none")
                        .forAudience(source)
                        .build());
                return;
            }
            String server = qServer.get();
            ServerQueue q = manager.getQueue(server).orElse(null);
            if (q == null) {
                source.sendMessage(feature.getLocalizationHandler()
                        .getMessage("queue.status.not_enabled")
                        .withPlaceholders(Map.of("server", server))
                        .forAudience(source)
                        .build());
                return;
            }
            int pos = q.positionOf(p.getUniqueId()).orElse(0);
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.status.header")
                    .withPlaceholders(Map.of("server", server))
                    .forAudience(source).build());
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.status.line")
                    .withPlaceholders(Map.of(
                            "server", server,
                            "position", String.valueOf(pos + 1)
                    ))
                    .forAudience(source).build());
            return;
        }

        // Admin subcommands
        String sub = args.get(0).toLowerCase(Locale.ROOT);
        switch (sub) {
            case "top" -> handleTop(invocation);
            case "skip" -> handleSkip(invocation);
            default -> source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.cmd.usage")
                    .forAudience(source)
                    .build());
        }
    }

    private void handleTop(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!source.hasPermission("proxyfeatures.feature.queue.command.top")) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.cmd.no_permission")
                    .forAudience(source).build());
            return;
        }
        if (args.length < 2) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.cmd.usage")
                    .forAudience(source).build());
            return;
        }
        String server = args[1].toLowerCase(Locale.ROOT);
        var opt = manager.getQueue(server);
        if (opt.isEmpty()) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.cmd.target_not_enabled")
                    .withPlaceholders(Map.of("server", server))
                    .forAudience(source).build());
            return;
        }
        var q = opt.get();
        source.sendMessage(feature.getLocalizationHandler()
                .getMessage("queue.cmd.top.header")
                .withPlaceholders(Map.of("server", server))
                .forAudience(source).build());
        final int[] shown = {0};
        q.forEachIndexed((i, e) -> {
            if (shown[0] >= 10) return; // toon top 10
            String name = managerNameOf(e.playerId());
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.cmd.top.entry")
                    .withPlaceholders(Map.of(
                            "idx", String.valueOf(i + 1),
                            "name", name,
                            "priority", String.valueOf(e.priority())
                    )).forAudience(source).build());
            shown[0]++;
        });
        if (shown[0] == 0) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.cmd.top.empty")
                    .forAudience(source).build());
        }
    }

    private void handleSkip(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!source.hasPermission("proxyfeatures.feature.queue.command.skip")) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.cmd.no_permission")
                    .forAudience(source).build());
            return;
        }
        if (args.length < 2) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.cmd.usage")
                    .forAudience(source).build());
            return;
        }
        String targetName = args[1];
        Optional<Player> optP = feature.getPlugin().getProxy().getPlayer(targetName);
        if (optP.isEmpty()) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.cmd.player_not_found")
                    .withPlaceholders(Map.of("player", targetName))
                    .forAudience(source).build());
            return;
        }
        Player target = optP.get();
        Optional<String> srv = manager.findQueueOf(target.getUniqueId());
        if (srv.isEmpty()) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.status.none")
                    .forAudience(source).build());
            return;
        }
        boolean ok = manager.skipToFront(srv.get(), target.getUniqueId());
        if (ok) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.cmd.skip.done")
                    .withPlaceholders(Map.of(
                            "player", target.getUsername(),
                            "server", srv.get()
                    )).forAudience(source).build());
        }
    }

    private String managerNameOf(java.util.UUID id) {
        return feature.getPlugin().getProxy().getPlayer(id).map(Player::getUsername).orElse(id.toString());
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return CompletableFuture.completedFuture(List.of("top", "skip"));
        }
        if (args.length == 1) {
            String first = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            if ("top".startsWith(first)) out.add("top");
            if ("skip".startsWith(first)) out.add("skip");
            return CompletableFuture.completedFuture(out);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            List<String> servers = feature.getPlugin().getProxy().getAllServers().stream()
                    .map(s -> s.getServerInfo().getName())
                    .filter(manager::isServerQueued)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(servers);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("skip")) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            List<String> names = feature.getPlugin().getProxy().getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(names);
        }
        return CompletableFuture.completedFuture(List.of());
    }
}
