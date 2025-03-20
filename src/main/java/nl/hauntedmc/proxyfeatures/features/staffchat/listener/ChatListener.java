package nl.hauntedmc.proxyfeatures.features.staffchat.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.ChatChannel;
import nl.hauntedmc.proxyfeatures.features.staffchat.internal.ChatChannelHandler;

public class ChatListener {

    private final StaffChat feature;
    private final ChatChannelHandler handler;

    public ChatListener(StaffChat feature) {
        this.feature = feature;
        this.handler = feature.getChatChannelHandler();
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Loop through each channel and check if the message starts with its prefix.
        for (ChatChannel channel : handler.getChannels()) {
            String prefix = channel.getPrefix();
            if (message.startsWith(prefix)) {
                if (!player.hasPermission(channel.getPermission())) {
                    return;
                }

                // A player has gotten the channel permissions after joining. We have to add the player to the channel.
                if (!handler.getViewers(channel).contains(player)) {
                    handler.addViewer(channel, player);
                }

                event.setResult(PlayerChatEvent.ChatResult.denied());
                String channelMessage = message.substring(prefix.length()).trim();
                handler.sendChannelMessage(channel, player, channelMessage);
                return;
            }
        }
    }
}
