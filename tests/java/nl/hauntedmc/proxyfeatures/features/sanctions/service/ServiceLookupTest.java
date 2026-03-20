package nl.hauntedmc.proxyfeatures.features.sanctions.service;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServiceLookupTest {

    @Test
    void byNameUsesExpectedQueryAndReturnsResult() {
        Sanctions feature = mock(Sanctions.class);
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> query = mock(Query.class);
        PlayerEntity player = mock(PlayerEntity.class);

        when(feature.getOrm()).thenReturn(orm);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });
        when(session.createQuery("FROM PlayerEntity WHERE username = :u", PlayerEntity.class))
                .thenReturn(query);
        when(query.setParameter("u", "Remy")).thenReturn(query);
        when(query.uniqueResultOptional()).thenReturn(Optional.of(player));

        ServiceLookup lookup = new ServiceLookup(feature);
        Optional<PlayerEntity> result = lookup.byName("Remy");

        assertTrue(result.isPresent());
        assertSame(player, result.get());
        verify(query).setParameter("u", "Remy");
    }

    @Test
    void byUuidUsesExpectedQueryAndCanReturnEmpty() {
        Sanctions feature = mock(Sanctions.class);
        ORMContext orm = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerEntity> query = mock(Query.class);

        when(feature.getOrm()).thenReturn(orm);
        when(orm.runInTransaction(any())).thenAnswer(invocation -> {
            ORMContext.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.execute(session);
        });
        when(session.createQuery("FROM PlayerEntity WHERE uuid = :u", PlayerEntity.class))
                .thenReturn(query);
        when(query.setParameter("u", "b776f381-8df4-4d6c-84e4-b7f42025795a")).thenReturn(query);
        when(query.uniqueResultOptional()).thenReturn(Optional.empty());

        ServiceLookup lookup = new ServiceLookup(feature);
        Optional<PlayerEntity> result = lookup.byUuid("b776f381-8df4-4d6c-84e4-b7f42025795a");

        assertTrue(result.isEmpty());
        verify(query).setParameter("u", "b776f381-8df4-4d6c-84e4-b7f42025795a");
    }
}
