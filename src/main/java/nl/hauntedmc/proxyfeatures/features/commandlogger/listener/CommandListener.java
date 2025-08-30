package nl.hauntedmc.proxyfeatures.features.commandlogger.listener;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.proxyfeatures.features.commandlogger.CommandLogger;

import java.util.Locale;

public class CommandListener {

    private final CommandLogger feature;

    public CommandListener(CommandLogger feature) {
        this.feature = feature;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onCommandExecute(CommandExecuteEvent event) {
        String full = event.getCommand();

        String trimmed = full.stripLeading();
        if (trimmed.isEmpty()) return;

        String alias = trimmed.split("\\s+", 2)[0];

        ProxyServer proxy = feature.getPlugin().getProxy();
        CommandManager cmdMgr = proxy.getCommandManager();

        CommandSource source = event.getCommandSource();
        boolean isRegisteredForSource = cmdMgr.hasCommand(alias, source);
        if (!isRegisteredForSource) {
            return;
        }

        // Human-readable "who" string for log output
        String who;
        if (source instanceof Player p) {
            who = p.getUsername() + " (" + p.getUniqueId() + ")";
        } else {
            who = source.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        }

        // Console/info logger
        feature.getLogHandler().logProxyCommand(who, full);

        // Persist into DB (server should be "proxy")
        feature.getCommandLogService().logProxyCommand(source, full);
    }
}
