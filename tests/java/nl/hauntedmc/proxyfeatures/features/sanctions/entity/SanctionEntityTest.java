package nl.hauntedmc.proxyfeatures.features.sanctions.entity;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

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

    @Test
    void mutableFieldsCanBeSetAndRetrieved() {
        SanctionEntity entity = new SanctionEntity();
        PlayerEntity target = mock(PlayerEntity.class);
        PlayerEntity actor = mock(PlayerEntity.class);
        Instant created = Instant.now().minusSeconds(60);
        Instant expires = Instant.now().plusSeconds(60);

        entity.setType(SanctionType.BAN);
        entity.setReason("test");
        entity.setTargetPlayer(target);
        entity.setTargetIp("1.2.3.4");
        entity.setActorPlayer(actor);
        entity.setActorName("console");
        entity.setCreatedAt(created);
        entity.setExpiresAt(expires);
        entity.setActive(true);

        assertEquals(SanctionType.BAN, entity.getType());
        assertEquals("test", entity.getReason());
        assertSame(target, entity.getTargetPlayer());
        assertEquals("1.2.3.4", entity.getTargetIp());
        assertSame(actor, entity.getActorPlayer());
        assertEquals("console", entity.getActorName());
        assertEquals(created, entity.getCreatedAt());
        assertEquals(expires, entity.getExpiresAt());
        assertTrue(entity.isActive());
    }

    @Test
    void idAndVersionAccessorsExposePersistenceFields() throws Exception {
        SanctionEntity entity = new SanctionEntity();
        setPrivateField(entity, "id", 99L);
        setPrivateField(entity, "version", 7L);

        assertEquals(99L, entity.getId());
        assertEquals(7L, entity.getVersion());
    }

    private static void setPrivateField(Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
