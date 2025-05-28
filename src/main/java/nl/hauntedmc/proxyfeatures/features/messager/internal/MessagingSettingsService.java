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
     * Fetches (or creates) the settings row for the given PlayerEntity.
     */
    public PlayerMessageSettingsEntity getOrCreateSettings(PlayerEntity player) {
        return feature.getOrmContext().runInTransaction(session -> {
            Optional<PlayerMessageSettingsEntity> opt = session.createQuery(
                            "FROM PlayerMessageSettingsEntity s WHERE s.playerId = :pid",
                            PlayerMessageSettingsEntity.class)
                    .setParameter("pid", player.getId())
                    .uniqueResultOptional();

            if (opt.isPresent()) {
                return opt.get();
            } else {
                PlayerMessageSettingsEntity settings = new PlayerMessageSettingsEntity(player);
                session.persist(settings);
                return settings;
            }
        });
    }

    public boolean isToggleEnabled(PlayerEntity player) {
        return getOrCreateSettings(player).isMsgToggle();
    }

    public void setToggle(PlayerEntity player, boolean enabled) {
        feature.getOrmContext().runInTransaction(session -> {
            PlayerMessageSettingsEntity s = getOrCreateSettings(player);
            s.setMsgToggle(enabled);
            session.merge(s);
            return null;
        });
    }

    public boolean isSpyEnabled(PlayerEntity player) {
        return getOrCreateSettings(player).isMsgSpy();
    }

    public void setSpy(PlayerEntity player, boolean enabled) {
        feature.getOrmContext().runInTransaction(session -> {
            PlayerMessageSettingsEntity s = getOrCreateSettings(player);
            s.setMsgSpy(enabled);
            session.merge(s);
            return null;
        });
    }

    public void block(PlayerEntity me, PlayerEntity target) {
        feature.getOrmContext().runInTransaction(session -> {
            PlayerMessageSettingsEntity s = getOrCreateSettings(me);
            s.block(target);
            session.merge(s);
            return null;
        });
    }

    public void unblock(PlayerEntity me, PlayerEntity target) {
        feature.getOrmContext().runInTransaction(session -> {
            PlayerMessageSettingsEntity s = getOrCreateSettings(me);
            s.unblock(target);
            session.merge(s);
            return null;
        });
    }

    public boolean isBlocking(PlayerEntity me, PlayerEntity target) {
        return getOrCreateSettings(me).isBlocking(target);
    }

    /**
     * Look up the PlayerEntity by UUID.
     */
    public Optional<PlayerEntity> getPlayerEntity(UUID uuid) {
        return feature.getOrmContext().runInTransaction(session ->
                session.createQuery("FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class)
                        .setParameter("uuid", uuid.toString())
                        .uniqueResultOptional()
        );
    }
}
