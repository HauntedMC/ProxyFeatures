package nl.hauntedmc.proxyfeatures.features.hlink.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.hlink.HLink;
import nl.hauntedmc.proxyfeatures.features.hlink.internal.HLinkHandler;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class HLinkCommand implements FeatureCommand {

    private final HLink feature;
    private final HLinkHandler handler;

    public HLinkCommand(HLink feature) {
        this.feature = feature;
        this.handler = feature.getHLinkHandler();
    }


    public String getName() {
        return "hlink";
    }


    public String[] getAliases() {
        return new String[]{""};
    }


    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.hlink.command.sync");
    }


    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        List<String> args = List.of(invocation.arguments());

        // 1) Usage check
        if (args.size() != 2 || !args.get(0).equalsIgnoreCase("sync")) {
            source.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("hlink.syncUsage")
                            .forAudience(source)
                            .build()
            );
            return;
        }

        // 2) Find the target player
        String targetName = args.get(1);
        Optional<Player> playerOpt = ProxyFeatures.getProxyInstance().getPlayer(targetName);
        if (playerOpt.isEmpty()) {
            source.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("hlink.syncNotOnline")
                            .with("player", targetName)
                            .forAudience(source)
                            .build()
            );
            return;
        }

        // 3) Actually sync
        Player target = playerOpt.get();
        String targetUsername = target.getUsername();
        handler.updatePlayerDataAsync(target).whenComplete((ok, err) ->
                feature.getLifecycleManager().getTaskManager().scheduleTask(() -> {
                    if (err != null || !Boolean.TRUE.equals(ok)) {
                        source.sendMessage(feature.getLocalizationHandler()
                                .getMessage("hlink.errorCreatingKey")
                                .forAudience(source)
                                .build());
                        return;
                    }
                    source.sendMessage(
                            feature.getLocalizationHandler()
                                    .getMessage("hlink.syncSuccess")
                                    .with("player", targetUsername)
                                    .forAudience(source)
                                    .build()
                    );
                })
        );
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        List<String> args = List.of(invocation.arguments());

        if (args.size() == 1) {
            return CompletableFuture.completedFuture(List.of("sync"));
        }

        if (args.size() == 2 && args.get(0).equalsIgnoreCase("sync")) {
            String partial = args.get(1).toLowerCase();
            return CompletableFuture.completedFuture(
                    feature.getPlugin()
                            .getProxyInstance()
                            .getAllPlayers()
                            .stream()
                            .map(Player::getUsername)
                            .filter(name -> name.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList())
            );
        }

        return CompletableFuture.completedFuture(List.of());
    }
}
