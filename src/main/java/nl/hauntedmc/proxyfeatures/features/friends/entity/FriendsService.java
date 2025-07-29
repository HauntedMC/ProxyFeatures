package nl.hauntedmc.proxyfeatures.features.friends.entity;

import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Optional;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.friends.Friends;

public class FriendsService {
    private final Friends feature;

    public FriendsService(Friends feature) { this.feature = feature; }

    public Optional<PlayerEntity> getPlayer(@NotNull String uuid) {
        return feature.getOrm().runInTransaction(
                s -> s.createQuery("FROM PlayerEntity WHERE uuid = :u", PlayerEntity.class)
                        .setParameter("u", uuid)
                        .uniqueResultOptional());
    }

    public FriendSettingsEntity getOrCreateSettings(PlayerEntity p) {
        return feature.getOrm().runInTransaction(session -> {

            FriendSettingsEntity settings =
                    session.get(FriendSettingsEntity.class, p.getId());

            if (settings == null) {
                PlayerEntity managedPlayer =
                        session.get(PlayerEntity.class, p.getId());

                settings = new FriendSettingsEntity(managedPlayer);
                session.persist(settings);
            }
            return settings;
        });
    }

    public Optional<FriendRelationEntity> relation(PlayerEntity owner, PlayerEntity target) {
        return feature.getOrm().runInTransaction(s ->
                s.createQuery("FROM FriendRelationEntity WHERE player = :p AND friend = :f",
                                FriendRelationEntity.class)
                        .setParameter("p", owner)
                        .setParameter("f", target)
                        .uniqueResultOptional());
    }

    public void saveRelation(FriendRelationEntity rel) {
        feature.getOrm().runInTransaction(s -> { s.merge(rel); return null; });
    }

    public void deleteRelation(FriendRelationEntity rel) {
        feature.getOrm().runInTransaction(s -> {
            FriendRelationEntity managed = (FriendRelationEntity) s.merge(rel);
            s.remove(managed);
            return null;
        });
    }

    public List<FriendRelationEntity> incomingRequests(PlayerEntity p) {
        return feature.getOrm().runInTransaction(
                s -> s.createQuery("FROM FriendRelationEntity WHERE friend = :me AND status = :st",
                                FriendRelationEntity.class)
                        .setParameter("me", p)
                        .setParameter("st", FriendStatus.PENDING)
                        .list());
    }

    public List<FriendRelationEntity> acceptedRelations(PlayerEntity p) {
        return feature.getOrm().runInTransaction(
                s -> s.createQuery("FROM FriendRelationEntity WHERE player = :me AND status = :st",
                                FriendRelationEntity.class)
                        .setParameter("me", p)
                        .setParameter("st", FriendStatus.ACCEPTED)
                        .list());
    }

    public void setEnabled(PlayerEntity player, boolean enabled) {
        feature.getOrm().runInTransaction(session -> {
            FriendSettingsEntity s =
                    session.get(FriendSettingsEntity.class, player.getId());

            if (s == null) {
                PlayerEntity managed = session.get(PlayerEntity.class, player.getId());
                s = new FriendSettingsEntity(managed);
                session.persist(s);
            }
            s.setEnabled(enabled);
            return null;
        });
    }

    public List<FriendRelationEntity> outgoingRequests(PlayerEntity p) {
        return feature.getOrm().runInTransaction(
                s -> s.createQuery("FROM FriendRelationEntity WHERE player = :me AND status = :st",
                                FriendRelationEntity.class)
                        .setParameter("me", p)
                        .setParameter("st", FriendStatus.PENDING)
                        .list());
    }
}