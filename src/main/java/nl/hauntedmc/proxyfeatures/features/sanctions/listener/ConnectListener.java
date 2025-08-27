package nl.hauntedmc.proxyfeatures.features.sanctions.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;

import java.time.Instant;
import java.util.UUID;

public class ConnectListener {

    private final Sanctions feature;

    public ConnectListener (Sanctions feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onLogin(LoginEvent e) {
        try {
            String ip = e.getPlayer().getRemoteAddress().getAddress().getHostAddress();
            UUID uuid = e.getPlayer().getUniqueId();

            // Resolve PlayerEntity by uuid
            var playerOpt = feature.getService().getPlayerByUuid(uuid.toString());

            // Check UUID-ban (via PlayerEntity) and IP-ban
            var uuidBanOpt = playerOpt.flatMap(feature.getService()::findActiveBanByPlayer);
            var ipBanOpt   = feature.getService().findActiveBanByIp(ip);

            // Prefer UUID ban over IP ban
            SanctionEntity ban = uuidBanOpt.orElse(ipBanOpt.orElse(null));

            if (ban != null) {
                // If ban expired right now, deactivate and allow login
                if (ban.isExpired(Instant.now())) {
                    feature.getService().deactivateExpiredSanction(ban.getId());
                    return;
                }
                String key = ban.isPermanent() ? "sanctions.disconnect.banned.perm" : "sanctions.disconnect.banned.temp";
                e.setResult(LoginEvent.ComponentResult.denied(
                        feature.getLocalizationHandler().getMessage(key)
                                .withPlaceholders(feature.getService().placeholdersFor(ban))
                                .forAudience(e.getPlayer()).build()));
                return;
            }

            // Load mute into cache by playerId if we can resolve
            playerOpt.ifPresent(pEnt -> feature.getService().loadActiveMuteIntoCache(pEnt.getId()));
        } catch (Throwable t) {
            // Defensive: never break login flow because of sanctions code
            feature.getLogger().error("[Sanctions/ConnectListener] Error during login checks");
        }
    }
}
