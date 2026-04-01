package nl.hauntedmc.proxyfeatures.features.clientinfo.internal;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.clientinfo.entity.PlayerClientInfoSettingsEntity;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClientInfoSettingsServiceTest {

    @Test
    void isNotificationsEnabledReturnsStoredSetting() {
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> playerQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerClientInfoSettingsEntity> settingsQuery = mock(Query.class);

        UUID uuid = UUID.randomUUID();
        PlayerEntity player = new PlayerEntity();
        player.setId(7L);
        player.setUuid(uuid.toString());
        player.setUsername("Remy");

        PlayerClientInfoSettingsEntity settings = new PlayerClientInfoSettingsEntity(player);
        settings.setNotifyEnabled(false);

        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });
        when(session.createQuery("FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class))
                .thenReturn(playerQuery);
        when(playerQuery.setParameter("uuid", uuid.toString())).thenReturn(playerQuery);
        when(playerQuery.uniqueResultOptional()).thenReturn(Optional.of(player));

        when(session.createQuery("FROM PlayerClientInfoSettingsEntity s WHERE s.playerId = :pid",
                PlayerClientInfoSettingsEntity.class)).thenReturn(settingsQuery);
        when(settingsQuery.setParameter("pid", 7L)).thenReturn(settingsQuery);
        when(settingsQuery.uniqueResultOptional()).thenReturn(Optional.of(settings));

        ClientInfoSettingsService service = new ClientInfoSettingsService(orm);
        assertFalse(service.isNotificationsEnabled(uuid, "Remy"));
    }

    @Test
    void setNotificationsEnabledCreatesMissingSettingsAndRefreshesUsername() {
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> playerQuery = mock(Query.class);

        UUID uuid = UUID.randomUUID();
        PlayerEntity player = new PlayerEntity();
        player.setId(11L);
        player.setUuid(uuid.toString());
        player.setUsername("OldName");

        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });
        when(session.createQuery("FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class))
                .thenReturn(playerQuery);
        when(playerQuery.setParameter("uuid", uuid.toString())).thenReturn(playerQuery);
        when(playerQuery.uniqueResultOptional()).thenReturn(Optional.of(player));

        when(session.get(PlayerClientInfoSettingsEntity.class, 11L)).thenReturn(null);

        ClientInfoSettingsService service = new ClientInfoSettingsService(orm);
        service.setNotificationsEnabled(uuid, "NewName", false);

        verify(session).persist(any(PlayerClientInfoSettingsEntity.class));
        verify(session).merge(any(PlayerClientInfoSettingsEntity.class));
        verify(session).merge(player);
        assertEquals("NewName", player.getUsername());
    }

    @Test
    void getPlayerEntityDelegatesToOrmQuery() {
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> playerQuery = mock(Query.class);

        UUID uuid = UUID.randomUUID();
        PlayerEntity player = new PlayerEntity();
        player.setId(44L);
        player.setUuid(uuid.toString());
        player.setUsername("Remy");

        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });
        when(session.createQuery("FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class))
                .thenReturn(playerQuery);
        when(playerQuery.setParameter("uuid", uuid.toString())).thenReturn(playerQuery);
        when(playerQuery.uniqueResultOptional()).thenReturn(Optional.of(player));

        ClientInfoSettingsService service = new ClientInfoSettingsService(orm);
        Optional<PlayerEntity> found = service.getPlayerEntity(uuid);

        assertTrue(found.isPresent());
        assertEquals("Remy", found.get().getUsername());
    }

    @Test
    void createsPlayerAndDefaultSettingsWhenMissing() {
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> playerQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerClientInfoSettingsEntity> settingsQuery = mock(Query.class);

        UUID uuid = UUID.randomUUID();

        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });
        when(session.createQuery("FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class))
                .thenReturn(playerQuery);
        when(playerQuery.setParameter("uuid", uuid.toString())).thenReturn(playerQuery);
        when(playerQuery.uniqueResultOptional()).thenReturn(Optional.empty());

        when(session.createQuery("FROM PlayerClientInfoSettingsEntity s WHERE s.playerId = :pid",
                PlayerClientInfoSettingsEntity.class)).thenReturn(settingsQuery);
        when(settingsQuery.setParameter(eq("pid"), any())).thenReturn(settingsQuery);
        when(settingsQuery.uniqueResultOptional()).thenReturn(Optional.empty());

        ClientInfoSettingsService service = new ClientInfoSettingsService(orm);
        boolean enabled = service.isNotificationsEnabled(uuid, "Remy");

        assertTrue(enabled); // defaults true
        verify(session, atLeastOnce()).persist(any(PlayerEntity.class));
        verify(session, atLeastOnce()).persist(any(PlayerClientInfoSettingsEntity.class));
    }
}
