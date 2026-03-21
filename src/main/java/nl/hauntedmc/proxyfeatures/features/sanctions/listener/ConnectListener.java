package nl.hauntedmc.proxyfeatures.features.sanctions.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;

import java.time.Instant;
import java.util.UUID;

public class ConnectListener {

    private final Sanctions feature;

    public ConnectListener(Sanctions feature) {
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
            var ipBanOpt = feature.getService().findActiveBanByIp(ip);

            LoginBanDecisionPolicy.Decision decision = LoginBanDecisionPolicy.decide(
                    uuidBanOpt,
                    ipBanOpt,
                    Instant.now()
            );

            if (decision.outcome() == LoginBanDecisionPolicy.Outcome.DEACTIVATE_EXPIRED) {
                SanctionEntity expired = decision.sanction();
                feature.getService().deactivateExpiredSanction(expired != null ? expired.getId() : null);
                return;
            }

            if (decision.outcome() == LoginBanDecisionPolicy.Outcome.DENY) {
                SanctionEntity ban = decision.sanction();
                e.setResult(LoginEvent.ComponentResult.denied(
                        feature.getLocalizationHandler().getMessage(decision.messageKey())
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
