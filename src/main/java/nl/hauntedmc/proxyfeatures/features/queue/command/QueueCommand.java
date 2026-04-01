package nl.hauntedmc.proxyfeatures.features.queue.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.queue.Queue;
import nl.hauntedmc.proxyfeatures.features.queue.QueueManager;
import nl.hauntedmc.proxyfeatures.features.queue.model.ServerQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /queue            -> positie in je huidige wachtrij
 * /queue leave      -> verlaat je huidige wachtrij
 * /queue info <srv>  -> toon kop van de wachtrij (staff)
 */
public class QueueCommand implements FeatureCommand {
    private final Queue feature;
    private final QueueManager manager;

    public QueueCommand(Queue feature, QueueManager manager) {
        this.feature = feature;
        this.manager = manager;
    }


    public String getName() {
        return "queue";
    }


    public String[] getAliases() {
        return new String[]{"q"};
    }


    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.queue.command");
    }


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
                        .with("server", server)
                        .forAudience(source)
                        .build());
                return;
            }
            int pos = q.positionOf(p.getUniqueId()).orElse(0);
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.status.header")
                    .with("server", server)
                    .forAudience(source).build());
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.status.line")
                    .with("server", server)
                    .with("position", pos + 1)
                    .forAudience(source).build());
            return;
        }

        // Admin/Player subcommands
        String sub = args.getFirst().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "leave" -> handleLeave(invocation);
            case "info" -> handleInfo(invocation);
            default -> source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.cmd.usage")
                    .forAudience(source)
                    .build());
        }
    }

    private void handleLeave(Invocation invocation) {
        CommandSource source = invocation.source();
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
        var opt = manager.getQueue(server);
        if (opt.isEmpty()) {
            // Shouldn't happen if findQueueOf returned this, but handle gracefully
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.status.not_enabled")
                    .with("server", server)
                    .forAudience(source)
                    .build());
            return;
        }

        // Clear both queue entry and any grace reservation
        opt.get().clearReservation(p.getUniqueId());

        source.sendMessage(feature.getLocalizationHandler()
                .getMessage("queue.cmd.leave.done")
                .with("server", server)
                .forAudience(source)
                .build());
    }

    private void handleInfo(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        if (!source.hasPermission("proxyfeatures.feature.queue.command.info")) {
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
                    .with("server", server)
                    .forAudience(source).build());
            return;
        }
        var q = opt.get();
        source.sendMessage(feature.getLocalizationHandler()
                .getMessage("queue.cmd.info.header")
                .with("server", server)
                .forAudience(source).build());
        final int[] shown = {0};
        q.forEachIndexed((i, e) -> {
            if (shown[0] >= 10) return; // toon top 10
            String name = managerNameOf(e.playerId());
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.cmd.info.entry")
                    .with("idx", i + 1)
                    .with("name", name)
                    .with("priority", e.priority())
                    .forAudience(source).build());
            shown[0]++;
        });
        if (shown[0] == 0) {
            source.sendMessage(feature.getLocalizationHandler()
                    .getMessage("queue.cmd.info.empty")
                    .forAudience(source).build());
        }
    }

    private String managerNameOf(java.util.UUID id) {
        return feature.getPlugin().getProxy().getPlayer(id).map(Player::getUsername).orElse(id.toString());
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return CompletableFuture.completedFuture(List.of("leave", "info"));
        }
        if (args.length == 1) {
            String first = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            if ("leave".startsWith(first)) out.add("leave");
            if ("info".startsWith(first)) out.add("info");
            return CompletableFuture.completedFuture(out);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            List<String> servers = feature.getPlugin().getProxy().getAllServers().stream()
                    .map(s -> s.getServerInfo().getName())
                    .filter(manager::isServerQueued)
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(servers);
        }
        return CompletableFuture.completedFuture(List.of());
    }
}
