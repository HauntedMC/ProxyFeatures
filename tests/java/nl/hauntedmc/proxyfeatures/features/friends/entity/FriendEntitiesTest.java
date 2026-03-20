package nl.hauntedmc.proxyfeatures.features.friends.entity;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FriendEntitiesTest {

    @Test
    void friendRelationStoresParticipantsAndStatus() throws Exception {
        PlayerEntity owner = mock(PlayerEntity.class);
        PlayerEntity target = mock(PlayerEntity.class);
        FriendRelationEntity relation = new FriendRelationEntity(owner, target, FriendStatus.PENDING);

        assertSame(owner, relation.getPlayer());
        assertSame(target, relation.getFriend());
        assertEquals(FriendStatus.PENDING, relation.getStatus());

        relation.setStatus(FriendStatus.ACCEPTED);
        assertEquals(FriendStatus.ACCEPTED, relation.getStatus());

        setPrivateField(relation, "id", 100L);
        assertEquals(100L, relation.getId());

        Constructor<FriendRelationEntity> ctor = FriendRelationEntity.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        FriendRelationEntity empty = ctor.newInstance();
        assertNull(empty.getPlayer());
        assertNull(empty.getFriend());
        assertNull(empty.getStatus());
    }

    @Test
    void friendSettingsDefaultAndToggleFlowWork() throws Exception {
        PlayerEntity owner = mock(PlayerEntity.class);
        when(owner.getId()).thenReturn(22L);
        FriendSettingsEntity settings = new FriendSettingsEntity(owner);

        assertSame(owner, settings.getPlayer());
        assertTrue(settings.isEnabled());
        settings.setEnabled(false);
        assertFalse(settings.isEnabled());

        Constructor<FriendSettingsEntity> ctor = FriendSettingsEntity.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        FriendSettingsEntity empty = ctor.newInstance();
        assertTrue(empty.isEnabled());
    }

    @Test
    void friendSnapshotAndPlayerRefRecordsExposeComponents() {
        FriendSnapshot snapshot = new FriendSnapshot(9L, "uuid-a", "Alpha");
        PlayerRef ref = new PlayerRef(9L, "uuid-a", "Alpha");

        assertEquals(9L, snapshot.id());
        assertEquals("uuid-a", snapshot.uuid());
        assertEquals("Alpha", snapshot.username());

        assertEquals(9L, ref.id());
        assertEquals("uuid-a", ref.uuid());
        assertEquals("Alpha", ref.username());
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
