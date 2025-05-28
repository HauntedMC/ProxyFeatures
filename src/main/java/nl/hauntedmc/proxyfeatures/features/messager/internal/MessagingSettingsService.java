package nl.hauntedmc.proxyfeatures.features.messager.internal;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.messager.Messenger;
import nl.hauntedmc.proxyfeatures.features.messager.entity.PlayerMessageSettingsEntity;

import java.util.Optional;
import java.util.UUID;

public class MessagingSettingsService {
    private final Messenger feature;

    public MessagingSettingsService(Messenger feature) {
        this.feature = feature;
    }

    /**
     * Find-or-create both the PlayerEntity and their MessageSettings
     * in one transaction. Returns the fully-initialized settings.
     */
    public PlayerMessageSettingsEntity loadSettings(UUID uuid, String username) {
        return feature.getOrmContext().runInTransaction(session -> {
            // 1) find or create PlayerEntity
            PlayerEntity playerEnt = session.createQuery(
                            "FROM PlayerEntity p WHERE p.uuid = :uuid",
                            PlayerEntity.class)
                    .setParameter("uuid", uuid.toString())
                    .uniqueResultOptional()
                    .orElseGet(() -> {
                        PlayerEntity pe = new PlayerEntity();
                        pe.setUuid(uuid.toString());
                        pe.setUsername(username);
                        session.persist(pe);
                        return pe;
                    });

            // 2) find or create MessageSettings
            PlayerMessageSettingsEntity settings = session.createQuery(
                            "FROM PlayerMessageSettingsEntity s WHERE s.playerId = :pid",
                            PlayerMessageSettingsEntity.class)
                    .setParameter("pid", playerEnt.getId())
                    .uniqueResultOptional()
                    .orElseGet(() -> {
                        PlayerMessageSettingsEntity s = new PlayerMessageSettingsEntity(playerEnt);
                        session.persist(s);
                        return s;
                    });

            // eager-load blocks
            settings.getBlockedPlayers().size();
            return settings;
        });
    }

    public void setToggle(PlayerEntity player, boolean enabled) {
        feature.getOrmContext().runInTransaction(session -> {
            PlayerMessageSettingsEntity s = session.get(PlayerMessageSettingsEntity.class, player.getId());
            s.setMsgToggle(enabled);
            session.merge(s);
            return null;
        });
    }

    public void setSpy(PlayerEntity player, boolean enabled) {
        feature.getOrmContext().runInTransaction(session -> {
            PlayerMessageSettingsEntity s = session.get(PlayerMessageSettingsEntity.class, player.getId());
            s.setMsgSpy(enabled);
            session.merge(s);
            return null;
        });
    }

    public void block(PlayerEntity me, PlayerEntity target) {
        feature.getOrmContext().runInTransaction(session -> {
            PlayerMessageSettingsEntity s = session.get(PlayerMessageSettingsEntity.class, me.getId());
            // load managed target
            PlayerEntity managedTarget = session.get(PlayerEntity.class, target.getId());
            s.block(managedTarget);
            session.merge(s);
            return null;
        });
    }

    public void unblock(PlayerEntity me, PlayerEntity target) {
        feature.getOrmContext().runInTransaction(session -> {
            PlayerMessageSettingsEntity s = session.get(PlayerMessageSettingsEntity.class, me.getId());
            // load managed target
            PlayerEntity managedTarget = session.get(PlayerEntity.class, target.getId());
            s.unblock(managedTarget);
            session.merge(s);
            return null;
        });
    }

    public Optional<PlayerEntity> getPlayerEntity(UUID uuid) {
        return feature.getOrmContext().runInTransaction(session ->
                session.createQuery("FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class)
                        .setParameter("uuid", uuid.toString())
                        .uniqueResultOptional()
        );
    }
}
