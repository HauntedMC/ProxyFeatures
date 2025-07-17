package nl.hauntedmc.proxyfeatures.features.broadcast.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.broadcast.Broadcast;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BroadcastProxyCommand extends FeatureCommand {

    private final Broadcast feature;
    private final ProxyServer proxy;
    private final LegacyComponentSerializer legacyAmp = LegacyComponentSerializer.legacyAmpersand();

    public BroadcastProxyCommand(Broadcast feature) {
        this.feature = feature;
        this.proxy   = feature.getPlugin().getProxy();
    }

    @Override public String getName()    { return "broadcastproxy"; }
    @Override public String getAliases() { return ""; }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(
                "proxyfeatures.feature.broadcast.command.broadcastproxy");
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args     = invocation.arguments();

        if (args.length < 2) {
            sendUsage(src);
            return;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        switch (mode) {
            case "chat"  -> broadcastChat(message, src);
            case "title" -> broadcastTitle(message, src);
            default      -> {
                src.sendMessage(feature.getLocalizationHandler()
                        .getMessage("broadcast.noMode")
                        .forAudience(src)
                        .build());
                sendUsage(src);
            }
        }
    }

    private void broadcastChat(String msg, CommandSource src) {
        Component comp = legacyAmp.deserialize(msg);
        proxy.getAllPlayers().forEach(p -> p.sendMessage(comp));
        acknowledge(src);
    }

    private void broadcastTitle(String msg, CommandSource src) {
        String titlePart;
        String subPart;

        if (msg.contains("|")) {
            String[] split = msg.split("\\|", 2);
            titlePart = split[0].trim();
            subPart   = split[1].trim();
        } else {
            titlePart = msg;
            subPart   = "";
        }

        Component titleComp = legacyAmp.deserialize(titlePart);
        Component subComp   = legacyAmp.deserialize(subPart);

        int fadeIn  = (int) feature.getConfigHandler().getSetting("title_fade_in");
        int stay    = (int) feature.getConfigHandler().getSetting("title_stay");
        int fadeOut = (int) feature.getConfigHandler().getSetting("title_fade_out");

        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay    * 50L),
                Duration.ofMillis(fadeOut * 50L));

        Title title = Title.title(titleComp, subComp, times);

        proxy.getAllPlayers().forEach(p -> p.showTitle(title));

        acknowledge(src);
    }

    private void sendUsage(CommandSource src) {
        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("broadcast.usage")
                .forAudience(src)
                .build());
    }

    private void acknowledge(CommandSource src) {
        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("broadcast.sent")
                .forAudience(src)
                .build());
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();

        if (args.length == 0 || args[0].isEmpty()) {
            return CompletableFuture.completedFuture(List.of("chat", "title"));
        }
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            List<String> modes = Stream.of("chat", "title")
                    .filter(m -> m.startsWith(partial))
                    .collect(Collectors.toList());
            return CompletableFuture.completedFuture(modes);
        }
        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}
