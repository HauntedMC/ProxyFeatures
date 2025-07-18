package nl.hauntedmc.proxyfeatures.features.resourcepack.command;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.resourcepack.ResourcePack;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.proxyfeatures.features.resourcepack.util.ResourceUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class ResourcePackCommand extends FeatureCommand {

    private final ResourcePack feature;
    private final ProxyServer proxy;

    public ResourcePackCommand(ResourcePack feature) {
        this.feature = feature;
        this.proxy = feature.getPlugin().getProxy();
    }

    @Override
    public String getName() {
        return "resourcepack";
    }

    @Override
    public String[] getAliases() {
        return new String[]{""};
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.resourcepack.command");
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        List<String> args = List.of(invocation.arguments());

        if (args.isEmpty() || !args.getFirst().equalsIgnoreCase("list")) {
            source.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("resourcepack.cmd_usage")
                            .forAudience(source)
                            .build()
            );
            return;
        }

        // If a target player is specified, ensure the executor has "other" permission
        if (args.size() > 1 && !source.hasPermission("proxyfeatures.feature.resourcepack.command.other")) {
            source.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("general.no_permission")
                            .forAudience(source)
                            .build()
            );
            return;
        }

        // list status for self or target
        Player targetPlayer;
        if (args.size() == 1) {
            if (!(source instanceof Player)) {
                source.sendMessage(
                        feature.getLocalizationHandler()
                                .getMessage("resourcepack.cmd_notPlayer")
                                .forAudience(source)
                                .build()
                );
                return;
            }
            targetPlayer = (Player) source;
        } else {
            String name = args.get(1);
            Optional<Player> opt = proxy.getPlayer(name);
            if (opt.isEmpty()) {
                source.sendMessage(
                        feature.getLocalizationHandler()
                                .getMessage("resourcepack.cmd_playerNotFound")
                                .withPlaceholders(Map.of("player", name))
                                .forAudience(source)
                                .build()
                );
                return;
            }
            targetPlayer = opt.get();
        }

        source.sendMessage(
                feature.getLocalizationHandler()
                        .getMessage("resourcepack.cmd_header")
                        .forAudience(source)
                        .withPlaceholders(Map.of("player", targetPlayer.getUsername()))
                        .build()
        );

        Collection<ResourcePackInfo> applied = targetPlayer.getAppliedResourcePacks();
        Collection<ResourcePackInfo> pending = targetPlayer.getPendingResourcePacks();

        // List applied packs
        for (ResourcePackInfo info : applied) {
            String packName = ResourceUtils.getResourcePackName(info.getUrl());
            source.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("resourcepack.cmd_entry")
                            .withPlaceholders(Map.of(
                                    "pack", packName,
                                    "status", "Applied"
                            ))
                            .forAudience(source)
                            .build()
            );
        }

        // List pending packs
        for (ResourcePackInfo info : pending) {
            String packName = ResourceUtils.getResourcePackName(info.getUrl());
            source.sendMessage(
                    feature.getLocalizationHandler()
                            .getMessage("resourcepack.cmd_entry")
                            .withPlaceholders(Map.of(
                                    "pack", packName,
                                    "status", "Pending"
                            ))
                            .forAudience(source)
                            .build()
            );
        }
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        List<String> args = List.of(invocation.arguments());
        if (args.size() == 1) {
            return CompletableFuture.completedFuture(List.of("list"));
        }
        if (args.size() == 2 && args.get(0).equalsIgnoreCase("list")) {
            String partial = args.get(1).toLowerCase();
            return CompletableFuture.completedFuture(
                    proxy.getAllPlayers().stream()
                            .map(Player::getUsername)
                            .filter(n -> n.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList())
            );
        }
        return CompletableFuture.completedFuture(List.of());
    }
}