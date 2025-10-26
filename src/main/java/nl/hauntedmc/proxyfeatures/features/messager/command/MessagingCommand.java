package nl.hauntedmc.proxyfeatures.features.messager.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.common.util.APIRegistry;
import nl.hauntedmc.proxyfeatures.features.messager.Messenger;
import nl.hauntedmc.proxyfeatures.features.messager.internal.MessagingHandler;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.VanishAPI;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class MessagingCommand implements FeatureCommand {
    private final Messenger feature;
    private final ProxyServer proxy;
    private final MessagingHandler handler;

    public MessagingCommand(Messenger feature) {
        this.feature = feature;
        this.proxy   = feature.getPlugin().getProxy();
        this.handler = feature.getHandler();
    }

    public String getName()    { return "msg"; }
    public String[] getAliases(){ return new String[]{""};     }
    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.messager.command");
    }

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
            case "spy" -> handleSpy(src);
            case "toggle" -> handleToggle(src);
            case "block","unblock" -> handleBlockUnblock(src, sub, args);
            case "reply" -> handleReply(src, args);
            default -> handleDirect(src, args);
        }
    }

    private void handleSpy(CommandSource src) {
        var loc = feature.getLocalizationHandler();
        if (!(src instanceof Player p)) {
            src.sendMessage(loc.getMessage("message.cmd_usage").forAudience(src).build());
            return;
        }
        if (!src.hasPermission("proxyfeatures.feature.messager.command.spy")) {
            src.sendMessage(loc.getMessage("general.no_permission").forAudience(src).build());
            return;
        }
        handler.toggleSpy(p.getUniqueId());
        String key = handler.isSpy(p.getUniqueId()) ? "message.spy.enabled" : "message.spy.disabled";
        p.sendMessage(loc.getMessage(key).forAudience(p).build());
    }

    private void handleToggle(CommandSource src) {
        var loc = feature.getLocalizationHandler();
        if (!(src instanceof Player p)) {
            src.sendMessage(loc.getMessage("message.cmd_usage").forAudience(src).build());
            return;
        }
        if (!src.hasPermission("proxyfeatures.feature.messager.command.toggle")) {
            src.sendMessage(loc.getMessage("general.no_permission").forAudience(src).build());
            return;
        }
        handler.toggleMessaging(p.getUniqueId());
        String key = handler.isMessagingEnabled(p.getUniqueId())
                ? "message.toggle.enabled"
                : "message.toggle.disabled";
        p.sendMessage(loc.getMessage(key).forAudience(p).build());
    }

    private void handleBlockUnblock(CommandSource src, String sub, String[] args) {
        var loc = feature.getLocalizationHandler();
        if (!(src instanceof Player p) || args.length < 2) {
            src.sendMessage(loc.getMessage("message.cmd_usage").forAudience(src).build());
            return;
        }
        if (!src.hasPermission("proxyfeatures.feature.messager.command.block")) {
            src.sendMessage(loc.getMessage("general.no_permission").forAudience(src).build());
            return;
        }
        proxy.getPlayer(args[1]).ifPresentOrElse(target -> {

            if (target.hasPermission("proxyfeatures.feature.messager.command.block.bypass")) {
                p.sendMessage(loc.getMessage("message.block.bypass").forAudience(p).build());
                return;
            }

            UUID them = target.getUniqueId();
            boolean isBlocked = handler.isBlocked(p.getUniqueId(), them);
            if (sub.equals("block")) {
                if (isBlocked) {
                    p.sendMessage(loc.getMessage("message.block.already")
                            .with("player", target.getUsername())
                            .forAudience(p).build());
                } else {
                    handler.block(p.getUniqueId(), them);
                    p.sendMessage(loc.getMessage("message.block.success")
                            .with("player", target.getUsername())
                            .forAudience(p).build());
                }
            } else {
                if (!isBlocked) {
                    p.sendMessage(loc.getMessage("message.unblock.not_blocked")
                            .with("player", target.getUsername())
                            .forAudience(p).build());
                } else {
                    handler.unblock(p.getUniqueId(), them);
                    p.sendMessage(loc.getMessage("message.unblock.success")
                            .with("player", target.getUsername())
                            .forAudience(p).build());
                }
            }
        }, () -> src.sendMessage(loc.getMessage("message.offline").forAudience(src).build()));
    }

    private void handleReply(CommandSource src, String[] args) {
        var loc = feature.getLocalizationHandler();
        if (!(src instanceof Player p)) {
            src.sendMessage(loc.getMessage("message.cmd_usage").forAudience(src).build());
            return;
        }
        if (!src.hasPermission("proxyfeatures.feature.messager.command.reply")) {
            src.sendMessage(loc.getMessage("general.no_permission").forAudience(src).build());
            return;
        }
        if (args.length < 2) {
            p.sendMessage(loc.getMessage("message.cmd_usage").forAudience(p).build());
            return;
        }
        handler.getLastRecipient(p.getUniqueId()).ifPresentOrElse(
                uid -> proxy.getPlayer(uid).ifPresentOrElse(
                        t -> handler.processPrivateMessage(p, t, String.join(" ", Arrays.copyOfRange(args,1,args.length))),
                        () -> p.sendMessage(loc.getMessage("message.offline").forAudience(p).build())
                ),
                () -> p.sendMessage(loc.getMessage("message.reply.no_last").forAudience(p).build())
        );
    }

    private void handleDirect(CommandSource src, String[] args) {
        var loc = feature.getLocalizationHandler();
        if (!(src instanceof Player p) || args.length < 2) {
            src.sendMessage(loc.getMessage("message.cmd_usage").forAudience(src).build());
            return;
        }
        if (!src.hasPermission("proxyfeatures.feature.messager.command.msg")) {
            src.sendMessage(loc.getMessage("general.no_permission").forAudience(src).build());
            return;
        }
        String targetName = args[0];
        proxy.getPlayer(targetName).ifPresentOrElse(
                t -> handler.processPrivateMessage(p, t, String.join(" ", Arrays.copyOfRange(args,1,args.length))),
                () -> p.sendMessage(loc.getMessage("message.offline").forAudience(p).build())
        );
    }

    public CompletableFuture<List<String>> suggestAsync(Invocation inv) {
        String[] args = inv.arguments();
        List<String> subs = List.of("spy","block","unblock","toggle","reply");

        if (args.length == 0 || args[0].isEmpty()) {
            List<String> all = new ArrayList<>(subs);

            all.addAll(APIRegistry.get(VanishAPI.class)
                    .map(VanishAPI::getAdjustedOnlinePlayers)
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .map(Player::getUsername)
                    .toList());

            return CompletableFuture.completedFuture(all);
        }
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> out = subs.stream()
                    .filter(s->s.startsWith(partial)).collect(Collectors.toList());

            APIRegistry.get(VanishAPI.class)
                    .map(VanishAPI::getAdjustedOnlinePlayers)
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .forEach(out::add);

            return CompletableFuture.completedFuture(out);
        }
        if (args.length==2 && List.of("block","unblock").contains(args[0].toLowerCase())) {
            String pfx = args[1].toLowerCase();
            List<String> names = APIRegistry.get(VanishAPI.class)
                    .map(VanishAPI::getAdjustedOnlinePlayers)
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(pfx))
                    .toList();

            return CompletableFuture.completedFuture(names);
        }
        return CompletableFuture.completedFuture(List.of());
    }
}
