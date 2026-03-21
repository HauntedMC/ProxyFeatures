package nl.hauntedmc.proxyfeatures.features.messager.internal;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.messager.Messenger;
import nl.hauntedmc.proxyfeatures.features.messager.entity.PlayerMessageSettingsEntity;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessagingSettingsServiceTest {

    @Test
    void loadSettingsReturnsExistingSettingsAndLoadsBlocks() {
        Messenger feature = mock(Messenger.class);
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> playerQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerMessageSettingsEntity> settingsQuery = mock(Query.class);
        when(feature.getOrmContext()).thenReturn(orm);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });

        UUID uuid = UUID.randomUUID();
        PlayerEntity player = new PlayerEntity();
        player.setId(10L);
        player.setUuid(uuid.toString());
        player.setUsername("Remy");
        PlayerMessageSettingsEntity settings = new PlayerMessageSettingsEntity(player);

        when(session.createQuery("FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class))
                .thenReturn(playerQuery);
        when(playerQuery.setParameter("uuid", uuid.toString())).thenReturn(playerQuery);
        when(playerQuery.uniqueResultOptional()).thenReturn(Optional.of(player));

        when(session.createQuery("FROM PlayerMessageSettingsEntity s WHERE s.playerId = :pid",
                PlayerMessageSettingsEntity.class)).thenReturn(settingsQuery);
        when(settingsQuery.setParameter("pid", 10L)).thenReturn(settingsQuery);
        when(settingsQuery.uniqueResultOptional()).thenReturn(Optional.of(settings));

        MessagingSettingsService service = new MessagingSettingsService(feature);
        PlayerMessageSettingsEntity loaded = service.loadSettings(uuid, "Remy");

        assertSame(settings, loaded);
        assertTrue(loaded.getBlockedPlayers().isEmpty());
    }

    @Test
    void toggleAndSpyMutationsUpdateSettingsInTransaction() {
        Messenger feature = mock(Messenger.class);
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        when(feature.getOrmContext()).thenReturn(orm);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });

        PlayerEntity me = new PlayerEntity();
        me.setId(12L);
        PlayerMessageSettingsEntity settings = new PlayerMessageSettingsEntity(me);
        when(session.get(PlayerMessageSettingsEntity.class, 12L)).thenReturn(settings);

        MessagingSettingsService service = new MessagingSettingsService(feature);
        service.setToggle(me, false);
        service.setSpy(me, true);

        assertFalse(settings.isMsgToggle());
        assertTrue(settings.isMsgSpy());
        verify(session, times(2)).merge(settings);
    }

    @Test
    void blockAndUnblockMutationsManageBlockSet() {
        Messenger feature = mock(Messenger.class);
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        when(feature.getOrmContext()).thenReturn(orm);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });

        PlayerEntity me = new PlayerEntity();
        me.setId(1L);
        PlayerEntity target = new PlayerEntity();
        target.setId(2L);

        PlayerMessageSettingsEntity settings = new PlayerMessageSettingsEntity(me);
        when(session.get(PlayerMessageSettingsEntity.class, 1L)).thenReturn(settings);
        when(session.get(PlayerEntity.class, 2L)).thenReturn(target);

        MessagingSettingsService service = new MessagingSettingsService(feature);
        service.block(me, target);
        assertTrue(settings.isBlocking(target));

        service.unblock(me, target);
        assertFalse(settings.isBlocking(target));
        verify(session, times(2)).merge(settings);
    }

    @Test
    void getPlayerEntityDelegatesToOrmQuery() {
        Messenger feature = mock(Messenger.class);
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> playerQuery = mock(Query.class);
        when(feature.getOrmContext()).thenReturn(orm);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });

        UUID uuid = UUID.randomUUID();
        PlayerEntity player = new PlayerEntity();
        player.setId(3L);
        player.setUuid(uuid.toString());
        player.setUsername("Player");

        when(session.createQuery("FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class))
                .thenReturn(playerQuery);
        when(playerQuery.setParameter("uuid", uuid.toString())).thenReturn(playerQuery);
        when(playerQuery.uniqueResultOptional()).thenReturn(Optional.of(player));

        MessagingSettingsService service = new MessagingSettingsService(feature);
        Optional<PlayerEntity> found = service.getPlayerEntity(uuid);

        assertTrue(found.isPresent());
        assertEquals("Player", found.get().getUsername());
    }

    @Test
    void loadSettingsCreatesPlayerAndSettingsWhenBothMissing() {
        Messenger feature = mock(Messenger.class);
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> playerQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerMessageSettingsEntity> settingsQuery = mock(Query.class);
        when(feature.getOrmContext()).thenReturn(orm);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });

        UUID uuid = UUID.randomUUID();
        when(session.createQuery("FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class))
                .thenReturn(playerQuery);
        when(playerQuery.setParameter("uuid", uuid.toString())).thenReturn(playerQuery);
        when(playerQuery.uniqueResultOptional()).thenReturn(Optional.empty());

        when(session.createQuery("FROM PlayerMessageSettingsEntity s WHERE s.playerId = :pid",
                PlayerMessageSettingsEntity.class)).thenReturn(settingsQuery);
        when(settingsQuery.setParameter(eq("pid"), any())).thenReturn(settingsQuery);
        when(settingsQuery.uniqueResultOptional()).thenReturn(Optional.empty());

        MessagingSettingsService service = new MessagingSettingsService(feature);
        PlayerMessageSettingsEntity loaded = service.loadSettings(uuid, "Remy");

        assertNotNull(loaded);
        verify(session, atLeastOnce()).persist(any(PlayerEntity.class));
        verify(session, atLeastOnce()).persist(any(PlayerMessageSettingsEntity.class));
    }
}
