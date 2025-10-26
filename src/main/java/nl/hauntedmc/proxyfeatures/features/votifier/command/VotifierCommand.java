package nl.hauntedmc.proxyfeatures.features.votifier.command;

import com.velocitypowered.api.command.CommandSource;
import nl.hauntedmc.proxyfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.votifier.Votifier;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class VotifierCommand implements FeatureCommand {

    private final Votifier feature;

    public VotifierCommand(Votifier feature) {
        this.feature = feature;
    }


    public void execute(Invocation inv) {
        CommandSource src = inv.source();
        String[] a = inv.arguments();

        if (!hasPermission(inv)) {
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("general.no_permission")
                    .forAudience(src).build());
            return;
        }

        if (a.length == 0) {
            sendUsage(src);
            return;
        }

        if (a[0].toLowerCase(Locale.ROOT).equals("status")) {
            Map<String, String> ph = Map.of(
                    "status", feature.isRunning() ? "running" : "stopped",
                    "host", feature.currentHost(),
                    "port", String.valueOf(feature.currentPort()),
                    "timeout", String.valueOf(feature.currentTimeoutMs()),
                    "keybits", String.valueOf(feature.currentKeyBits())
            );
            src.sendMessage(feature.getLocalizationHandler()
                    .getMessage("votifier.status")
                    .withPlaceholders(MessagePlaceholders.of(ph))
                    .forAudience(src).build());
        } else {
            sendUsage(src);
        }
    }


    public boolean hasPermission(Invocation inv) {
        return inv.source().hasPermission("proxyfeatures.feature.votifier.command");
    }

    public String getName() {
        return "votifier";
    }

    public String[] getAliases() {
        return new String[0];
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] a = invocation.arguments();
        if (a.length <= 1) {
            return CompletableFuture.completedFuture(List.of("status"));
        }
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    private void sendUsage(CommandSource src) {
        src.sendMessage(feature.getLocalizationHandler()
                .getMessage("votifier.usage")
                .forAudience(src).build());
    }
}
