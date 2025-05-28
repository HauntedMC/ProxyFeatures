package nl.hauntedmc.proxyfeatures.features.messager.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.messager.Messenger;
import nl.hauntedmc.proxyfeatures.features.messager.internal.MessagingHandler;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class MessagingCommand extends FeatureCommand {

    private final Messenger feature;
    private final ProxyServer proxy;
    private final MessagingHandler handler;

    public MessagingCommand(Messenger feature) {
        this.feature = feature;
        this.proxy   = feature.getPlugin().getProxy();
        this.handler = feature.getHandler();
    }

    @Override
    public String getName() {
        return "msg";
    }

    @Override
    public String getAliases() {
        return "";
    }

    @Override
    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.messager.command");
    }

    @Override
    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] args    = inv.arguments();
        var loc          = feature.getLocalizationHandler();

        if (args.length == 0) {
            src.sendMessage(loc.getMessage("message.cmd_usage").forAudience(src).build());
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "spy" -> {
                if (!(src instanceof Player p)) {
                    src.sendMessage(loc.getMessage("message.cmd_usage").forAudience(src).build());
                    return;
                }
                if (!src.hasPermission("proxyfeatures.feature.messager.command.spy")) {
                    src.sendMessage(loc.getMessage("global.no_permission").forAudience(src).build());
                    return;
                }
                handler.toggleSpy(p.getUniqueId());
                String key = handler.isSpy(p.getUniqueId()) ? "message.spy.enabled" : "message.spy.disabled";
                p.sendMessage(loc.getMessage(key).forAudience(p).build());
            }
            case "block", "unblock" -> {
                if (!(src instanceof Player player) || args.length < 2) {
                    src.sendMessage(loc.getMessage("message.cmd_usage").forAudience(src).build());
                    return;
                }
                if (!src.hasPermission("proxyfeatures.feature.messager.command.block")) {
                    src.sendMessage(loc.getMessage("global.no_permission").forAudience(src).build());
                    return;
                }
                proxy.getPlayer(args[1]).ifPresentOrElse(target -> {
                    UUID them = target.getUniqueId();
                    if (sub.equals("block")) {
                        if (handler.isBlocked(player.getUniqueId(), them)) {
                            player.sendMessage(loc.getMessage("message.block.already")
                                    .withPlaceholders(Map.of("player", target.getUsername()))
                                    .forAudience(player).build());
                        } else {
                            handler.block(player.getUniqueId(), them);
                            player.sendMessage(loc.getMessage("message.block.success")
                                    .withPlaceholders(Map.of("player", target.getUsername()))
                                    .forAudience(player).build());
                        }
                    } else { // unblock
                        if (!handler.isBlocked(player.getUniqueId(), them)) {
                            player.sendMessage(loc.getMessage("message.unblock.not_blocked")
                                    .withPlaceholders(Map.of("player", target.getUsername()))
                                    .forAudience(player).build());
                        } else {
                            handler.unblock(player.getUniqueId(), them);
                            player.sendMessage(loc.getMessage("message.unblock.success")
                                    .withPlaceholders(Map.of("player", target.getUsername()))
                                    .forAudience(player).build());
                        }
                    }
                }, () -> {
                    player.sendMessage(loc.getMessage("message.offline").forAudience(player).build());
                });
            }
            case "toggle" -> {
                if (!(src instanceof Player p)) {
                    src.sendMessage(loc.getMessage("message.cmd_usage").forAudience(src).build());
                    return;
                }
                if (!src.hasPermission("proxyfeatures.feature.messager.command.toggle")) {
                    src.sendMessage(loc.getMessage("global.no_permission").forAudience(src).build());
                    return;
                }
                handler.toggleMessaging(p.getUniqueId());
                String key = handler.isMessagingEnabled(p.getUniqueId())
                        ? "message.toggle.enabled"
                        : "message.toggle.disabled";
                p.sendMessage(loc.getMessage(key).forAudience(p).build());
            }
            case "reply" -> {
                if (!(src instanceof Player player)) {
                    src.sendMessage(loc.getMessage("message.cmd_usage").forAudience(src).build());
                    return;
                }
                if (!src.hasPermission("proxyfeatures.feature.messager.command.reply")) {
                    src.sendMessage(loc.getMessage("global.no_permission").forAudience(src).build());
                    return;
                }
                if (args.length < 2) {
                    player.sendMessage(loc.getMessage("message.cmd_usage").forAudience(player).build());
                    return;
                }
                handler.getLastRecipient(player.getUniqueId()).ifPresentOrElse(
                        uid -> proxy.getPlayer(uid).ifPresentOrElse(
                                target -> {
                                    String msg = String.join(" ", Arrays.copyOfRange(args,1,args.length));
                                    feature.getHandler().processPrivateMessage(player, target, msg);
                                },
                                () -> player.sendMessage(loc.getMessage("message.offline").forAudience(player).build())
                        ),
                        () -> player.sendMessage(loc.getMessage("message.reply.no_last").forAudience(player).build())
                );
            }
            default -> {
                // direct /msg <name> <msg>
                if (!(src instanceof Player player) || args.length < 2) {
                    src.sendMessage(loc.getMessage("message.cmd_usage").forAudience(src).build());
                    return;
                }
                if (!src.hasPermission("proxyfeatures.feature.messager.command.msg")) {
                    src.sendMessage(loc.getMessage("global.no_permission").forAudience(src).build());
                    return;
                }
                proxy.getPlayer(sub).ifPresentOrElse(
                        target -> {
                            String msg = String.join(" ", Arrays.copyOfRange(args,1,args.length));
                            feature.getHandler().processPrivateMessage(player, target, msg);
                        },
                        () -> player.sendMessage(loc.getMessage("message.offline").forAudience(player).build())
                );
            }
        }
    }


    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation inv) {
        String[] args = inv.arguments();

        // list of subcommands
        List<String> subs = List.of("spy","block","unblock","toggle","reply");

        // No args or empty first arg: suggest all subcommands + all players
        if (args.length == 0 || args[0].isEmpty()) {
            List<String> suggestions = new ArrayList<>(subs);
            suggestions.addAll(proxy.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .toList());
            return CompletableFuture.completedFuture(suggestions);
        }

        // Typing the first token: filter subcommands + players
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> suggestions = new ArrayList<>();
            // filter subs
            suggestions.addAll(subs.stream()
                    .filter(s -> s.startsWith(partial))
                    .toList());
            // filter players
            suggestions.addAll(proxy.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .toList());
            return CompletableFuture.completedFuture(suggestions);
        }

        // After subcommands that expect a player name
        String sub = args[0].toLowerCase();
        if (args.length == 2 && List.of("block","unblock").contains(sub)) {
            String partial = args[1].toLowerCase();
            List<String> matching = proxy.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(matching);
        }

        return CompletableFuture.completedFuture(List.of());
    }
}

