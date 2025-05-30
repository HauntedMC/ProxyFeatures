package nl.hauntedmc.proxyfeatures.features.commandhider.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import nl.hauntedmc.proxyfeatures.features.commandhider.CommandHider;

import java.util.Set;

public class AvailableCommandListener {

    private final CommandHider feature;

    public AvailableCommandListener(CommandHider feature) {
        this.feature = feature;

    }
    /**
     * Removes configured commands from the player's available commands tree.
     */
    @Subscribe
    public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
        if (event.getPlayer().hasPermission("proxyfeatures.feature.commandhider.bypass")) {
            return;
        }

        var rootNode = event.getRootNode();

        Set<String> hiddenCommands = feature.getHiderHandler().getHiddenCommands();

        if (hiddenCommands.isEmpty()) {
            return;
        }

        // Remove each hidden command
        rootNode.getChildren().removeIf(node -> hiddenCommands.contains(node.getName()));
    }

}
