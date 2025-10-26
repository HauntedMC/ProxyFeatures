package nl.hauntedmc.proxyfeatures.features.restart.command;

import com.velocitypowered.api.command.CommandSource;
import nl.hauntedmc.proxyfeatures.api.command.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.restart.Restart;
import nl.hauntedmc.proxyfeatures.features.restart.internal.RestartHandler;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProxyRestartCommand implements FeatureCommand {

    private static final String BASE_PERMISSION = "proxyfeatures.feature.restart.command.proxyrestart";
    private static final String FORCE_PERMISSION = "proxyfeatures.feature.restart.command.proxyrestart.force";

    private final Restart feature;
    private final RestartHandler handler;

    public ProxyRestartCommand(Restart feature) {
        this.feature = feature;
        this.handler = feature.getHandler();
    }


    public String getName() {
        return "proxyrestart";
    }


    public String[] getAliases() {
        return new String[]{""};
    }


    public boolean hasPermission(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length > 0 && "force".equalsIgnoreCase(args[0])) {
            return invocation.source().hasPermission(FORCE_PERMISSION);
        }
        return invocation.source().hasPermission(BASE_PERMISSION);
    }


    public void execute(Invocation invocation) {
        CommandSource src = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            boolean started = handler.startCountdown();
            if (started) {
                src.sendMessage(feature.getLocalizationHandler().getMessage("restart.started").forAudience(src).build());
            } else {
                src.sendMessage(feature.getLocalizationHandler().getMessage("restart.already_running").forAudience(src).build());
            }
            return;
        }

        if (args.length == 1 && "force".equalsIgnoreCase(args[0])) {
            if (!src.hasPermission(FORCE_PERMISSION)) {
                src.sendMessage(feature.getLocalizationHandler().getMessage("general.no_permission").forAudience(src).build());
                return;
            }
            src.sendMessage(feature.getLocalizationHandler().getMessage("restart.forced").forAudience(src).build());
            handler.forceRestart();
            return;
        }

        src.sendMessage(feature.getLocalizationHandler().getMessage("restart.cmd_usage").forAudience(src).build());
    }


    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 1) {
            if ("force".startsWith(args[0].toLowerCase())) {
                return CompletableFuture.completedFuture(List.of("force"));
            }
        }
        return CompletableFuture.completedFuture(Collections.emptyList());
    }
}
