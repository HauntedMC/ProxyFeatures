package nl.hauntedmc.proxyfeatures.features.sanctions.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SanctionEntityTest {

    @Test
    void permanentSanctionBehaviorIsDerivedFromNullExpiry() {
        SanctionEntity entity = new SanctionEntity();
        entity.setExpiresAt(null);

        assertTrue(entity.isPermanent());
        assertFalse(entity.isExpired(Instant.now()));
    }

    @Test
    void expirationUsesStrictBeforeComparison() {
        SanctionEntity entity = new SanctionEntity();
        Instant now = Instant.now();

        entity.setExpiresAt(now.minusSeconds(1));
        assertTrue(entity.isExpired(now));

        entity.setExpiresAt(now);
        assertFalse(entity.isExpired(now));

        entity.setExpiresAt(now.plusSeconds(1));
        assertFalse(entity.isExpired(now));
    }
}
