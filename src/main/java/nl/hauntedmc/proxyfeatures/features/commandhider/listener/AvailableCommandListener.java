package nl.hauntedmc.proxyfeatures.features.commandhider.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import nl.hauntedmc.proxyfeatures.features.commandhider.CommandHider;

import java.util.Locale;
import java.util.Set;

public final class AvailableCommandListener {

    private static final Locale LOCALE = Locale.ROOT;

    private final CommandHider feature;

    public AvailableCommandListener(CommandHider feature) {
        this.feature = feature;
    }

    /**
     * Removes configured commands from the player's available commands tree.
     * Note: This only hides commands from the client-side tree (tab-complete / suggestions).
     */
    @Subscribe
    public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
        if (event.getPlayer().hasPermission("proxyfeatures.feature.commandhider.bypass")) {
            return;
        }

        Set<String> hidden = feature.getHiderHandler().hiddenCommandsSet();
        if (hidden.isEmpty()) {
            return;
        }

        var rootNode = event.getRootNode();
        rootNode.getChildren().removeIf(node -> hidden.contains(node.getName().toLowerCase(LOCALE)));
    }
}
