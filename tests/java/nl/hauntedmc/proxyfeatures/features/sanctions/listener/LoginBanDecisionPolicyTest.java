package nl.hauntedmc.proxyfeatures.features.sanctions.listener;

import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LoginBanDecisionPolicyTest {

    @Test
    void decideAllowsWhenNoBanExists() {
        LoginBanDecisionPolicy.Decision decision = LoginBanDecisionPolicy.decide(
                Optional.empty(),
                Optional.empty(),
                Instant.now()
        );

        assertEquals(LoginBanDecisionPolicy.Outcome.ALLOW, decision.outcome());
        assertNull(decision.sanction());
        assertNull(decision.messageKey());
    }

    @Test
    void decidePrefersUuidBanOverIpBan() {
        Instant now = Instant.parse("2026-03-21T12:00:00Z");
        SanctionEntity uuidBan = banExpiringAt(now.plusSeconds(60));
        SanctionEntity ipBan = banExpiringAt(now.plusSeconds(120));

        LoginBanDecisionPolicy.Decision decision = LoginBanDecisionPolicy.decide(
                Optional.of(uuidBan),
                Optional.of(ipBan),
                now
        );

        assertEquals(LoginBanDecisionPolicy.Outcome.DENY, decision.outcome());
        assertSame(uuidBan, decision.sanction());
        assertEquals("sanctions.disconnect.banned.temp", decision.messageKey());
    }

    @Test
    void decideReturnsDeactivateForExpiredSelectedBan() {
        Instant now = Instant.parse("2026-03-21T12:00:00Z");
        SanctionEntity expired = banExpiringAt(now.minusSeconds(1));

        LoginBanDecisionPolicy.Decision decision = LoginBanDecisionPolicy.decide(
                Optional.of(expired),
                Optional.empty(),
                now
        );

        assertEquals(LoginBanDecisionPolicy.Outcome.DEACTIVATE_EXPIRED, decision.outcome());
        assertSame(expired, decision.sanction());
        assertNull(decision.messageKey());
    }

    @Test
    void messageKeyUsesPermanentVariantForPermanentBans() {
        Instant now = Instant.parse("2026-03-21T12:00:00Z");
        SanctionEntity permanent = new SanctionEntity();

        LoginBanDecisionPolicy.Decision decision = LoginBanDecisionPolicy.decide(
                Optional.of(permanent),
                Optional.empty(),
                now
        );

        assertEquals(LoginBanDecisionPolicy.Outcome.DENY, decision.outcome());
        assertEquals("sanctions.disconnect.banned.perm", decision.messageKey());
    }

    private static SanctionEntity banExpiringAt(Instant expiresAt) {
        SanctionEntity sanction = new SanctionEntity();
        sanction.setExpiresAt(expiresAt);
        return sanction;
    }
}
