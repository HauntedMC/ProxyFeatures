package nl.hauntedmc.proxyfeatures.features.messager.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.messager.Messenger;
import nl.hauntedmc.proxyfeatures.features.messager.internal.MessagingHandler;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ReplyCommand implements FeatureCommand {

    private final Messenger feature;
    private final ProxyServer proxy;
    private final MessagingHandler handler;

    public ReplyCommand(Messenger feature) {
        this.feature = feature;
        this.proxy = feature.getPlugin().getProxy();
        this.handler = feature.getHandler();
    }


    public String getName() {
        return "reply";
    }


    public String[] getAliases() {
        return new String[]{"r"};
    }


    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.messager.command.reply");
    }


    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] args = inv.arguments();
        var loc = feature.getLocalizationHandler();

        if (!(src instanceof Player player)) {
            src.sendMessage(loc.getMessage("message.cmd_usage").forAudience(src).build());
            return;
        }
        if (!src.hasPermission("proxyfeatures.feature.messager.command.reply")) {
            src.sendMessage(loc.getMessage("general.no_permission").forAudience(src).build());
            return;
        }
        if (args.length < 1) {
            player.sendMessage(loc.getMessage("message.cmd_usage").forAudience(player).build());
            return;
        }
        handler.getLastRecipient(player.getUniqueId()).ifPresentOrElse(
                uid -> proxy.getPlayer(uid).ifPresentOrElse(
                        target -> {
                            String msg = String.join(" ", Arrays.copyOfRange(args, 0, args.length));
                            feature.getHandler().processPrivateMessage(player, target, msg);
                        },
                        () -> player.sendMessage(loc.getMessage("message.offline").forAudience(player).build())
                ),
                () -> player.sendMessage(loc.getMessage("message.reply.no_last").forAudience(player).build())
        );
    }

    public CompletableFuture<List<String>> suggestAsync(Invocation inv) {
        return CompletableFuture.completedFuture(List.of());
    }
}
