package nl.hauntedmc.proxyfeatures.features.playerlist.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import nl.hauntedmc.commonlib.util.CastUtils;
import nl.hauntedmc.proxyfeatures.commands.FeatureCommand;
import nl.hauntedmc.proxyfeatures.features.playerlist.PlayerList;
import net.kyori.adventure.text.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GlobalListCommand implements FeatureCommand{

    private final PlayerList feature;
    private final List<String> blacklist;

    public GlobalListCommand(PlayerList feature) {
        this.feature = feature;
        blacklist = CastUtils.safeCastToList(feature.getConfigHandler().getSetting("blacklist"), String.class);
        
    }

    
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        if (!(source instanceof Player player)) {
            source.sendMessage(feature.getLocalizationHandler().getMessage("playerlist.command_only_players").forAudience(source).build());
            return;
        }

        Collection<RegisteredServer> servers = feature.getPlugin().getProxy().getAllServers().stream().toList();

        var filteredServers = servers.stream()
                .filter(server -> !blacklist.contains(server.getServerInfo().getName()))
                .toList();

        Component message = feature.getPlayerListHandler().formatGlobalList(filteredServers, player);
        source.sendMessage(message);
    }

    
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("proxyfeatures.feature.playerlist.command.glist");
    }

    
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }

    
    public String getName() {
        return "glist";
    }

    
    public String[] getAliases() {
        return new String[]{""};
    }
}
