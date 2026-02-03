package nl.hauntedmc.proxyfeatures.features.clientinfo.internal;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.clientinfo.entity.PlayerClientInfoSettingsEntity;

import java.util.Optional;
import java.util.UUID;

public final class ClientInfoSettingsService {

    private final ORMContext orm;

    public ClientInfoSettingsService(ORMContext ormContext) {
        this.orm = ormContext;
    }

    public boolean isNotificationsEnabled(UUID uuid, String username) {
        PlayerClientInfoSettingsEntity s = loadSettings(uuid, username);
        return s.isNotifyEnabled();
    }

    public void setNotificationsEnabled(UUID uuid, String username, boolean enabled) {
        orm.runInTransaction(session -> {
            PlayerEntity pe = findOrCreatePlayer(session, uuid, username);
            PlayerClientInfoSettingsEntity s = session.get(PlayerClientInfoSettingsEntity.class, pe.getId());
            if (s == null) {
                s = new PlayerClientInfoSettingsEntity(pe);
                session.persist(s);
            }
            s.setNotifyEnabled(enabled);
            session.merge(s);
            return null;
        });
    }

    private PlayerClientInfoSettingsEntity loadSettings(UUID uuid, String username) {
        return orm.runInTransaction(session -> {
            PlayerEntity pe = findOrCreatePlayer(session, uuid, username);

            return session.createQuery(
                            "FROM PlayerClientInfoSettingsEntity s WHERE s.playerId = :pid",
                            PlayerClientInfoSettingsEntity.class)
                    .setParameter("pid", pe.getId())
                    .uniqueResultOptional()
                    .orElseGet(() -> {
                        PlayerClientInfoSettingsEntity s = new PlayerClientInfoSettingsEntity(pe);
                        session.persist(s);
                        return s;
                    });
        });
    }

    private PlayerEntity findOrCreatePlayer(org.hibernate.Session session, UUID uuid, String username) {
        PlayerEntity pe = session.createQuery(
                        "FROM PlayerEntity p WHERE p.uuid = :uuid",
                        PlayerEntity.class)
                .setParameter("uuid", uuid.toString())
                .uniqueResultOptional()
                .orElseGet(() -> {
                    PlayerEntity created = new PlayerEntity();
                    created.setUuid(uuid.toString());
                    created.setUsername(username);
                    session.persist(created);
                    return created;
                });

        // Keep username fresh (optional, but useful)
        if (username != null && !username.isBlank() && !username.equals(pe.getUsername())) {
            pe.setUsername(username);
            session.merge(pe);
        }

        return pe;
    }

    public Optional<PlayerEntity> getPlayerEntity(UUID uuid) {
        return orm.runInTransaction(session ->
                session.createQuery("FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class)
                        .setParameter("uuid", uuid.toString())
                        .uniqueResultOptional()
        );
    }
}
