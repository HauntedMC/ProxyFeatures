package nl.hauntedmc.proxyfeatures.features.playerlanguage.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.PlayerLanguage;

public class LanguageListener {

    private final PlayerLanguage feature;

    public LanguageListener(PlayerLanguage feature) {
        this.feature = feature;
    }

    @Subscribe(priority = 10)
    public void onPostLogin(PostLoginEvent e) {
        feature.getService().warm(e.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent e) {
        feature.getService().forget(e.getPlayer().getUniqueId());
    }
}
