package nl.hauntedmc.proxyfeatures.features.sanctions.listener;

import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;

import java.time.Instant;
import java.util.Optional;

final class LoginBanDecisionPolicy {

    enum Outcome {
        ALLOW,
        DEACTIVATE_EXPIRED,
        DENY
    }

    record Decision(Outcome outcome, SanctionEntity sanction) {
        String messageKey() {
            if (outcome != Outcome.DENY || sanction == null) {
                return null;
            }
            return sanction.isPermanent()
                    ? "sanctions.disconnect.banned.perm"
                    : "sanctions.disconnect.banned.temp";
        }
    }

    private LoginBanDecisionPolicy() {
    }

    static Decision decide(Optional<SanctionEntity> uuidBanOpt,
                           Optional<SanctionEntity> ipBanOpt,
                           Instant now) {
        SanctionEntity selected = uuidBanOpt.orElseGet(() -> ipBanOpt.orElse(null));
        if (selected == null) {
            return new Decision(Outcome.ALLOW, null);
        }

        if (selected.isExpired(now)) {
            return new Decision(Outcome.DEACTIVATE_EXPIRED, selected);
        }

        return new Decision(Outcome.DENY, selected);
    }
}
