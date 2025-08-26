package nl.hauntedmc.proxyfeatures.features.sanctions.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;

import java.util.UUID;

public class ConnectListener {

    private final Sanctions feature;

    public ConnectListener (Sanctions feature) {
        this.feature = feature;

    }
    @Subscribe
    public void onLogin(LoginEvent e) {
        String ip = e.getPlayer().getRemoteAddress().getAddress().getHostAddress();
        UUID uuid = e.getPlayer().getUniqueId();

        // Resolve PlayerEntity by uuid
        var playerOpt = feature.getService().getPlayerByUuid(uuid.toString());

        // Check UUID-ban (via PlayerEntity) and IP-ban
        var uuidBan = playerOpt.flatMap(feature.getService()::findActiveBanByPlayer).orElse(null);
        var ipBan   = feature.getService().findActiveBanByIp(ip).orElse(null);

        var ban = uuidBan != null ? uuidBan : ipBan;
        if (ban != null) {
            String key = ban.isPermanent() ? "sanctions.disconnect.banned.perm" : "sanctions.disconnect.banned.temp";
            e.setResult(LoginEvent.ComponentResult.denied(
                    feature.getLocalizationHandler().getMessage(key)
                            .withPlaceholders(feature.getService().placeholdersFor(ban))
                            .forAudience(e.getPlayer()).build()));
            return;
        }

        // Load mute into cache by playerId if we can resolve
        playerOpt.ifPresent(pEnt -> feature.getService().loadActiveMuteIntoCache(pEnt.getId()));
    }



}
