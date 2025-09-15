package nl.hauntedmc.proxyfeatures.features.friends.entity;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.friends.Friends;

public class FriendsService {
    private final Friends feature;

    public FriendsService(Friends feature) {
        this.feature = feature;
    }

    public Optional<PlayerEntity> getPlayer(@NotNull String uuid) {
        return feature.getOrm().runInTransaction(
                s -> s.createQuery("FROM PlayerEntity WHERE uuid = :u", PlayerEntity.class)
                        .setParameter("u", uuid)
                        .uniqueResultOptional());
    }

    public FriendSettingsEntity getOrCreateSettings(PlayerEntity p) {
        return feature.getOrm().runInTransaction(session -> {
            FriendSettingsEntity settings = session.get(FriendSettingsEntity.class, p.getId());
            if (settings == null) {
                PlayerEntity managedPlayer = session.get(PlayerEntity.class, p.getId());
                settings = new FriendSettingsEntity(managedPlayer);
                session.persist(settings);
            }
            return settings;
        });
    }

    public void setEnabled(PlayerEntity player, boolean enabled) {
        feature.getOrm().runInTransaction(session -> {
            FriendSettingsEntity s = session.get(FriendSettingsEntity.class, player.getId());
            if (s == null) {
                PlayerEntity managed = session.get(PlayerEntity.class, player.getId());
                s = new FriendSettingsEntity(managed);
                session.persist(s);
            }
            s.setEnabled(enabled);
            return null;
        });
    }

    /**
     * Keep for internal, transactional uses (avoid touching lazy relations outside).
     */
    public Optional<FriendRelationEntity> relation(PlayerEntity owner, PlayerEntity target) {
        return feature.getOrm().runInTransaction(s ->
                s.createQuery("FROM FriendRelationEntity WHERE player = :p AND friend = :f",
                                FriendRelationEntity.class)
                        .setParameter("p", owner)
                        .setParameter("f", target)
                        .uniqueResultOptional());
    }

    /**
     * Internal transactional uses only (size checks etc. are fine).
     */
    public List<FriendRelationEntity> incomingRequests(PlayerEntity p) {
        return feature.getOrm().runInTransaction(
                s -> s.createQuery("FROM FriendRelationEntity WHERE friend = :me AND status = :st",
                                FriendRelationEntity.class)
                        .setParameter("me", p)
                        .setParameter("st", FriendStatus.PENDING)
                        .list());
    }

    /**
     * Usernames of players who sent me a pending request (incoming).
     */
    public List<String> incomingRequestUsernames(PlayerEntity me) {
        return feature.getOrm().runInTransaction(s ->
                s.createQuery(
                                "SELECT p.username FROM FriendRelationEntity fr " +
                                        "JOIN fr.player p " +
                                        "WHERE fr.friend = :me AND fr.status = :st",
                                String.class)
                        .setParameter("me", me)
                        .setParameter("st", FriendStatus.PENDING)
                        .list()
        );
    }

    /**
     * Usernames I have sent a pending request to (outgoing).
     */
    public List<String> outgoingRequestUsernames(PlayerEntity me) {
        return feature.getOrm().runInTransaction(s ->
                s.createQuery(
                                "SELECT f.username FROM FriendRelationEntity fr " +
                                        "JOIN fr.friend f " +
                                        "WHERE fr.player = :me AND fr.status = :st",
                                String.class)
                        .setParameter("me", me)
                        .setParameter("st", FriendStatus.PENDING)
                        .list()
        );
    }

    /**
     * Snapshots of my accepted friends (id/uuid/username), safe to use outside a session.
     */
    public List<FriendSnapshot> acceptedFriendSnapshots(PlayerEntity me) {
        return feature.getOrm().runInTransaction(s ->
                s.createQuery(
                                "SELECT new nl.hauntedmc.proxyfeatures.features.friends.entity.FriendSnapshot(" +
                                        "f.id, f.uuid, f.username) " +
                                        "FROM FriendRelationEntity fr " +
                                        "JOIN fr.friend f " +
                                        "WHERE fr.player = :me AND fr.status = :st",
                                FriendSnapshot.class)
                        .setParameter("me", me)
                        .setParameter("st", FriendStatus.ACCEPTED)
                        .list()
        );
    }

    public boolean isBlockedByTarget(PlayerEntity me, PlayerEntity target) {
        return feature.getOrm().runInTransaction(s -> {
            Long cnt = s.createQuery(
                            "SELECT COUNT(fr) FROM FriendRelationEntity fr " +
                                    "WHERE fr.player = :target AND fr.friend = :me AND fr.status = :st",
                            Long.class)
                    .setParameter("target", target)
                    .setParameter("me", me)
                    .setParameter("st", FriendStatus.BLOCKED)
                    .uniqueResult();
            return cnt != null && cnt > 0;
        });
    }

    public boolean didIBlockTarget(PlayerEntity me, PlayerEntity target) {
        return feature.getOrm().runInTransaction(s -> {
            Long cnt = s.createQuery(
                            "SELECT COUNT(fr) FROM FriendRelationEntity fr " +
                                    "WHERE fr.player = :me AND fr.friend = :target AND fr.status = :st",
                            Long.class)
                    .setParameter("me", me)
                    .setParameter("target", target)
                    .setParameter("st", FriendStatus.BLOCKED)
                    .uniqueResult();
            return cnt != null && cnt > 0;
        });
    }

    public boolean targetAcceptsRequests(PlayerEntity target) {
        return getOrCreateSettings(target).isEnabled();
    }

    private void upsertRelationInTx(org.hibernate.Session s,
                                    PlayerEntity owner, PlayerEntity friend,
                                    FriendStatus status) {
        FriendRelationEntity rel = s.createQuery(
                        "FROM FriendRelationEntity WHERE player = :p AND friend = :f",
                        FriendRelationEntity.class)
                .setParameter("p", owner)
                .setParameter("f", friend)
                .uniqueResult();
        if (rel == null) {
            PlayerEntity mo = s.get(PlayerEntity.class, owner.getId());
            PlayerEntity mf = s.get(PlayerEntity.class, friend.getId());
            rel = new FriendRelationEntity(mo, mf, status);
            s.persist(rel);
        } else {
            rel.setStatus(status);
            s.merge(rel);
        }
    }

    public boolean acceptPending(PlayerEntity from, PlayerEntity to) {
        return Boolean.TRUE.equals(feature.getOrm().runInTransaction(s -> {
            FriendRelationEntity incoming = s.createQuery(
                            "FROM FriendRelationEntity WHERE player = :from AND friend = :to",
                            FriendRelationEntity.class)
                    .setParameter("from", from)
                    .setParameter("to", to)
                    .uniqueResult();

            if (incoming == null || incoming.getStatus() != FriendStatus.PENDING) {
                return false;
            }

            Long blocks = s.createQuery(
                            "SELECT COUNT(fr) FROM FriendRelationEntity fr WHERE " +
                                    "((fr.player = :a AND fr.friend = :b) OR (fr.player = :b AND fr.friend = :a)) " +
                                    "AND fr.status = :st",
                            Long.class)
                    .setParameter("a", from).setParameter("b", to)
                    .setParameter("st", FriendStatus.BLOCKED)
                    .uniqueResult();
            if (blocks != null && blocks > 0) {
                return false;
            }

            incoming.setStatus(FriendStatus.ACCEPTED);
            s.merge(incoming);
            upsertRelationInTx(s, to, from, FriendStatus.ACCEPTED);
            return true;
        }));
    }

    public boolean removeFriendship(PlayerEntity a, PlayerEntity b) {
        return Boolean.TRUE.equals(feature.getOrm().runInTransaction(s -> {
            boolean changed = false;

            FriendRelationEntity ab = s.createQuery(
                            "FROM FriendRelationEntity WHERE player = :a AND friend = :b",
                            FriendRelationEntity.class)
                    .setParameter("a", a).setParameter("b", b).uniqueResult();

            FriendRelationEntity ba = s.createQuery(
                            "FROM FriendRelationEntity WHERE player = :b AND friend = :a",
                            FriendRelationEntity.class)
                    .setParameter("a", a).setParameter("b", b).uniqueResult();

            if (ab != null && ab.getStatus() == FriendStatus.ACCEPTED) {
                s.remove(ab);
                changed = true;
            }
            if (ba != null && ba.getStatus() == FriendStatus.ACCEPTED) {
                s.remove(ba);
                changed = true;
            }

            return changed;
        }));
    }

    public void block(PlayerEntity blocker, PlayerEntity target) {
        feature.getOrm().runInTransaction(s -> {
            upsertRelationInTx(s, blocker, target, FriendStatus.BLOCKED);

            FriendRelationEntity reverse = s.createQuery(
                            "FROM FriendRelationEntity WHERE player = :t AND friend = :b",
                            FriendRelationEntity.class)
                    .setParameter("t", target).setParameter("b", blocker).uniqueResult();
            if (reverse != null && reverse.getStatus() != FriendStatus.BLOCKED) {
                s.remove(reverse);
            }
            return null;
        });
    }

    public boolean unblock(PlayerEntity blocker, PlayerEntity target) {
        return Boolean.TRUE.equals(feature.getOrm().runInTransaction(s -> {
            FriendRelationEntity rel = s.createQuery(
                            "FROM FriendRelationEntity WHERE player = :b AND friend = :t",
                            FriendRelationEntity.class)
                    .setParameter("b", blocker).setParameter("t", target).uniqueResult();
            if (rel != null && rel.getStatus() == FriendStatus.BLOCKED) {
                s.remove(rel);
                return true;
            }
            return false;
        }));
    }

    public boolean cancelPending(PlayerEntity from, PlayerEntity to) {
        return Boolean.TRUE.equals(feature.getOrm().runInTransaction(s -> {
            FriendRelationEntity rel = s.createQuery(
                            "FROM FriendRelationEntity WHERE player = :f AND friend = :t",
                            FriendRelationEntity.class)
                    .setParameter("f", from).setParameter("t", to)
                    .uniqueResult();
            if (rel != null && rel.getStatus() == FriendStatus.PENDING) {
                s.remove(rel);
                return true;
            }
            return false;
        }));
    }

    public boolean denyIncoming(PlayerEntity to, PlayerEntity from) {
        return Boolean.TRUE.equals(feature.getOrm().runInTransaction(s -> {
            FriendRelationEntity rel = s.createQuery(
                            "FROM FriendRelationEntity WHERE player = :f AND friend = :t",
                            FriendRelationEntity.class)
                    .setParameter("f", from).setParameter("t", to)
                    .uniqueResult();
            if (rel != null && rel.getStatus() == FriendStatus.PENDING) {
                s.remove(rel);
                return true;
            }
            return false;
        }));
    }

    public int acceptAll(PlayerEntity me) {
        return feature.getOrm().runInTransaction(s -> {
            List<FriendRelationEntity> reqs = s.createQuery(
                            "FROM FriendRelationEntity WHERE friend = :me AND status = :st",
                            FriendRelationEntity.class)
                    .setParameter("me", me).setParameter("st", FriendStatus.PENDING)
                    .list();
            int accepted = 0;
            for (FriendRelationEntity rel : reqs) {
                Long blocks = s.createQuery(
                                "SELECT COUNT(fr) FROM FriendRelationEntity fr WHERE " +
                                        "((fr.player = :a AND fr.friend = :b) OR (fr.player = :b AND fr.friend = :a)) " +
                                        "AND fr.status = :st",
                                Long.class)
                        .setParameter("a", me).setParameter("b", rel.getPlayer())
                        .setParameter("st", FriendStatus.BLOCKED)
                        .uniqueResult();
                if (blocks != null && blocks > 0) continue;

                rel.setStatus(FriendStatus.ACCEPTED);
                s.merge(rel);
                upsertRelationInTx(s, me, rel.getPlayer(), FriendStatus.ACCEPTED);
                accepted++;
            }
            return accepted;
        });
    }

    public int denyAll(PlayerEntity me) {
        return feature.getOrm().runInTransaction(s -> {
            List<FriendRelationEntity> reqs = s.createQuery(
                            "FROM FriendRelationEntity WHERE friend = :me AND status = :st",
                            FriendRelationEntity.class)
                    .setParameter("me", me).setParameter("st", FriendStatus.PENDING)
                    .list();
            for (FriendRelationEntity rel : reqs) {
                s.remove(rel);
            }
            return reqs.size();
        });
    }

    // FriendsService.java

    /**
     * Usernames van mijn geaccepteerde vrienden.
     */
    public List<String> acceptedFriendUsernames(PlayerEntity me) {
        return feature.getOrm().runInTransaction(s ->
                s.createQuery(
                                "SELECT f.username FROM FriendRelationEntity fr " +
                                        "JOIN fr.friend f " +
                                        "WHERE fr.player = :me AND fr.status = :st",
                                String.class)
                        .setParameter("me", me)
                        .setParameter("st", FriendStatus.ACCEPTED)
                        .list()
        );
    }

    /**
     * Usernames van spelers die ik heb geblokkeerd.
     */
    public List<String> blockedUsernames(PlayerEntity me) {
        return feature.getOrm().runInTransaction(s ->
                s.createQuery(
                                "SELECT f.username FROM FriendRelationEntity fr " +
                                        "JOIN fr.friend f " +
                                        "WHERE fr.player = :me AND fr.status = :st",
                                String.class)
                        .setParameter("me", me)
                        .setParameter("st", FriendStatus.BLOCKED)
                        .list()
        );
    }

}
