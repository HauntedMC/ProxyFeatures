package nl.hauntedmc.proxyfeatures.features.staffchat.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.StaffChatHandler;
import com.velocitypowered.api.proxy.Player;

public class ChatListener {

    private final StaffChat feature;
    private final StaffChatHandler handler;

    public ChatListener(StaffChat feature) {
        this.feature = feature;
        this.handler = feature.getStaffChatHandler();
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Get the prefix from the configuration.
        String prefix = (String) feature.getConfigHandler().getSetting("prefix");
        if (message.startsWith(prefix)) {
            // Verify the sender has the permission.
            if (!player.hasPermission("proxyfeatures.feature.staffchat.staff")) {
                return;
            }
            // Cancel the event so it does not appear in the normal chat.
            event.setResult(PlayerChatEvent.ChatResult.denied());
            // Remove the prefix from the message.
            String staffMessage = message.substring(prefix.length()).trim();
            // Resend the message to staff viewers.
            handler.sendStaffChatMessage(player, staffMessage);
        }
    }
}
