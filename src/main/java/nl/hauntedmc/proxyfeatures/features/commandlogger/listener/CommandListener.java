package nl.hauntedmc.proxyfeatures.features.commandlogger.listener;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.proxyfeatures.features.commandlogger.CommandLogger;

public class CommandListener {

    private final CommandLogger feature;

    public CommandListener(CommandLogger feature) {
        this.feature = feature;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onCommandExecute(CommandExecuteEvent event) {
        String full = event.getCommand();

        String alias = CommandExecutionPolicy.extractAlias(full).orElse(null);
        if (alias == null) return;

        ProxyServer proxy = feature.getPlugin().getProxyInstance();
        CommandManager cmdMgr = proxy.getCommandManager();

        CommandSource source = event.getCommandSource();
        boolean isRegisteredForSource = cmdMgr.hasCommand(alias, source);
        if (!isRegisteredForSource) {
            return;
        }

        String who = CommandExecutionPolicy.describeSource(source);

        // Console/info logger
        feature.getLogHandler().logProxyCommand(who, full);

        // Persist into DB (server should be "proxy")
        feature.getCommandLogService().logProxyCommand(source, full);
    }
}
