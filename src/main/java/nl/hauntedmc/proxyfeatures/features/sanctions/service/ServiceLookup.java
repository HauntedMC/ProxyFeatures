package nl.hauntedmc.proxyfeatures.features.sanctions.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;

import java.util.Optional;

public class ServiceLookup {
    private final Sanctions feature;

    public ServiceLookup(Sanctions feature) {
        this.feature = feature;
    }

    public Optional<PlayerEntity> byName(String name) {
        return feature.getOrm().runInTransaction(s ->
                s.createQuery("FROM PlayerEntity WHERE username = :u", PlayerEntity.class)
                        .setParameter("u", name)
                        .uniqueResultOptional());
    }

    public Optional<PlayerEntity> byUuid(String uuid) {
        return feature.getOrm().runInTransaction(s ->
                s.createQuery("FROM PlayerEntity WHERE uuid = :u", PlayerEntity.class)
                        .setParameter("u", uuid)
                        .uniqueResultOptional());
    }
}
