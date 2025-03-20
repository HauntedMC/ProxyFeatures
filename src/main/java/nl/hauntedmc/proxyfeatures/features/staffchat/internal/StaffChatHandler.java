package nl.hauntedmc.proxyfeatures.features.staffchat.internal;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;
import net.kyori.adventure.text.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StaffChatHandler {

    private final StaffChat feature;
    private final Set<Player> staffViewers = Collections.synchronizedSet(new HashSet<>());

    public StaffChatHandler(StaffChat feature) {
        this.feature = feature;
    }

    public void addViewer(Player player) {
        staffViewers.add(player);
    }

    public void removeViewer(Player player) {
        staffViewers.remove(player);
    }

    public Set<Player> getStaffViewers() {
        return staffViewers;
    }

    public void sendStaffChatMessage(Player sender, String message) {
        // Determine the sender's current server.
        String serverName = sender.getCurrentServer()
                .map(s -> s.getServerInfo().getName())
                .orElse("unknown");

        // Retrieve and format the staff chat message from the MessageMap.
        Component staffChatMessage = feature.getLocalizationHandler().getMessage("staffchat.format", sender,
                Map.of(
                        "server", serverName,
                        "player", sender.getUsername(),
                        "message", message
                )
        );

        // Broadcast the formatted message to all staff viewers.
        synchronized (staffViewers) {
            for (Player viewer : staffViewers) {
                if (viewer.hasPermission("proxyfeatures.feature.staffchat.staff")) {
                    viewer.sendMessage(staffChatMessage);
                }
            }
        }
    }
}
