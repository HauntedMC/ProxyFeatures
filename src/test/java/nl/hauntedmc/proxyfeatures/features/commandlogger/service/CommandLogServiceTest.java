package nl.hauntedmc.proxyfeatures.features.commandlogger.service;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.commandlogger.CommandLogger;
import nl.hauntedmc.proxyfeatures.features.commandlogger.entity.CommandExecutionEntity;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CommandLogServiceTest {

    @Test
    void logsConsoleSourceWithoutPlayerEntity() {
        CommandLogger feature = mock(CommandLogger.class);
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        when(feature.getOrmContext()).thenReturn(orm);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });

        CommandSource source = mock(CommandSource.class);
        CommandLogService service = new CommandLogService(feature);
        service.logProxyCommand(source, "velocity info");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<CommandExecutionEntity> captor =
                org.mockito.ArgumentCaptor.forClass(CommandExecutionEntity.class);
        verify(session).persist(captor.capture());

        CommandExecutionEntity entry = captor.getValue();
        assertEquals("proxy", entry.getServer());
        assertNull(entry.getPlayer());
        assertEquals(source.getClass().getSimpleName().toLowerCase(Locale.ROOT), entry.getSource());
        assertEquals("velocity info", entry.getCommand());
        assertNotNull(entry.getTimestamp());
    }

    @Test
    void logsPlayerCommandAndCreatesMissingPlayerEntity() {
        CommandLogger feature = mock(CommandLogger.class);
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> query = mock(Query.class);
        when(feature.getOrmContext()).thenReturn(orm);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });
        when(session.createQuery("SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class))
                .thenReturn(query);

        UUID uuid = UUID.randomUUID();
        Player source = mock(Player.class);
        when(source.getUniqueId()).thenReturn(uuid);
        when(source.getUsername()).thenReturn("Remy");
        when(query.setParameter("uuid", uuid.toString())).thenReturn(query);
        when(query.uniqueResult()).thenReturn(null);

        CommandLogService service = new CommandLogService(feature);
        service.logProxyCommand(source, "say hi");

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Object> captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(session, times(2)).persist(captor.capture());

        List<Object> persisted = new ArrayList<>(captor.getAllValues());
        PlayerEntity createdPlayer = persisted.stream()
                .filter(PlayerEntity.class::isInstance)
                .map(PlayerEntity.class::cast)
                .findFirst()
                .orElseThrow();
        CommandExecutionEntity entry = persisted.stream()
                .filter(CommandExecutionEntity.class::isInstance)
                .map(CommandExecutionEntity.class::cast)
                .findFirst()
                .orElseThrow();

        assertEquals(uuid.toString(), createdPlayer.getUuid());
        assertEquals("Remy", createdPlayer.getUsername());
        assertEquals("say hi", entry.getCommand());
        assertEquals("proxy", entry.getServer());
    }

    @Test
    void logsPlayerCommandAndUpdatesUsernameWhenChanged() {
        CommandLogger feature = mock(CommandLogger.class);
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> query = mock(Query.class);
        when(feature.getOrmContext()).thenReturn(orm);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });
        when(session.createQuery("SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class))
                .thenReturn(query);

        UUID uuid = UUID.randomUUID();
        Player source = mock(Player.class);
        when(source.getUniqueId()).thenReturn(uuid);
        when(source.getUsername()).thenReturn("NewName");
        when(query.setParameter("uuid", uuid.toString())).thenReturn(query);

        PlayerEntity existing = new PlayerEntity();
        existing.setId(12L);
        existing.setUuid(uuid.toString());
        existing.setUsername("OldName");
        when(query.uniqueResult()).thenReturn(existing);

        CommandLogService service = new CommandLogService(feature);
        service.logProxyCommand(source, "list");

        verify(session).merge(existing);
        assertEquals("NewName", existing.getUsername());
        verify(session).persist(any(CommandExecutionEntity.class));
    }
}
