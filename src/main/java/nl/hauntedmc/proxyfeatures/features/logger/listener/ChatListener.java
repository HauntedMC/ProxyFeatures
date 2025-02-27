package nl.hauntedmc.proxyfeatures.features.logger.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import nl.hauntedmc.proxyfeatures.features.logger.Logger;

public class ChatListener {

    private final Logger feature;

    public ChatListener(Logger feature) {
        this.feature = feature;
    }

    @Subscribe(priority = 10, async = true)
    public void onPlayerChat(PlayerChatEvent event) {
        feature.getLogHandler().logChat(event.getPlayer(), event.getMessage());
    }
}
