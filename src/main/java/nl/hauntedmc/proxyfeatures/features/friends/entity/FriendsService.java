package nl.hauntedmc.proxyfeatures.features.friends.entity;

import org.jetbrains.annotations.NotNull;

import java.util.*;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.friends.Friends;
import nl.hauntedmc.proxyfeatures.features.friends.support.FriendsCache;

public class FriendsService {
    private final Friends feature;
    private final FriendsCache cache;

    public FriendsService(Friends feature, FriendsCache cache) {
        this.feature = feature;
        this.cache = cache;
    }

    // -------- Player lookups (cached, lightweight) --------

    public Optional<PlayerRef> getPlayer(@NotNull String uuid) {
        return cache.getPlayerByUuid(uuid, () ->
                feature.getOrm().runInTransaction(
                        s -> s.createQuery(
                                        "SELECT new nl.hauntedmc.proxyfeatures.features.friends.entity.PlayerRef(p.id, p.uuid, p.username) " +
                                                "FROM PlayerEntity p WHERE p.uuid = :u",
                                        PlayerRef.class)
                                .setParameter("u", uuid)
                                .uniqueResultOptional()
                )
        );
    }

    public Optional<PlayerRef> resolvePlayerByUsernameCaseInsensitive(@NotNull String username) {
        String key = username.toLowerCase(Locale.ROOT);
        return cache.getPlayerByLowerName(key, () ->
                feature.getOrm().runInTransaction(s ->
                        s.createQuery(
                                        "SELECT new nl.hauntedmc.proxyfeatures.features.friends.entity.PlayerRef(p.id, p.uuid, p.username) " +
                                                "FROM PlayerEntity p WHERE lower(p.username) = :u",
                                        PlayerRef.class)
                                .setParameter("u", key)
                                .uniqueResultOptional()
                )
        );
    }

    // -------- Settings (cached) --------

    public FriendSettingsEntity getOrCreateSettings(PlayerRef p) {
        // Use DB to ensure the row exists, then refresh cache
        FriendSettingsEntity settings = feature.getOrm().runInTransaction(session -> {
            FriendSettingsEntity s = session.get(FriendSettingsEntity.class, p.id());
            if (s == null) {
                PlayerEntity managedPlayer = session.get(PlayerEntity.class, p.id());
                s = new FriendSettingsEntity(managedPlayer);
                session.persist(s);
            }
            return s;
        });
        cache.invalidatePlayer(p.id());
        cache.getSettingsEnabled(p.id(), settings::isEnabled);
        return settings;
    }

    public void setEnabled(PlayerRef player, boolean enabled) {
        feature.getOrm().runInTransaction(session -> {
            FriendSettingsEntity s = session.get(FriendSettingsEntity.class, player.id());
            if (s == null) {
                PlayerEntity managed = session.get(PlayerEntity.class, player.id());
                s = new FriendSettingsEntity(managed);
                session.persist(s);
            }
            s.setEnabled(enabled);
            return null;
        });
        cache.invalidatePlayer(player.id());
    }

    public boolean targetAcceptsRequests(PlayerRef target) {
        Boolean enabled = cache.getSettingsEnabled(target.id(), () -> {
            FriendSettingsEntity s = feature.getOrm().runInTransaction(session -> {
                FriendSettingsEntity se = session.get(FriendSettingsEntity.class, target.id());
                if (se == null) {
                    PlayerEntity managed = session.get(PlayerEntity.class, target.id());
                    se = new FriendSettingsEntity(managed);
                    session.persist(se);
                }
                return se;
            });
            return s.isEnabled();
        });
        return Boolean.TRUE.equals(enabled);
    }

    // -------- Relations --------

    /** Fetch full relation entity (rare). Prefer relationStatus(...) on hot paths. */
    public Optional<FriendRelationEntity> relation(PlayerRef owner, PlayerRef target) {
        return feature.getOrm().runInTransaction(s ->
                s.createQuery("FROM FriendRelationEntity fr WHERE fr.player.id = :p AND fr.friend.id = :f",
                                FriendRelationEntity.class)
                        .setParameter("p", owner.id())
                        .setParameter("f", target.id())
                        .uniqueResultOptional());
    }

    public Optional<FriendStatus> relationStatus(PlayerRef owner, PlayerRef target) {
        return cache.getRelationStatus(owner.id(), target.id(), () ->
                feature.getOrm().runInTransaction(s -> {
                    FriendStatus st = s.createQuery(
                                    "SELECT fr.status FROM FriendRelationEntity fr " +
                                            "WHERE fr.player.id = :p AND fr.friend.id = :f",
                                    FriendStatus.class)
                            .setParameter("p", owner.id())
                            .setParameter("f", target.id())
                            .uniqueResult();
                    return Optional.ofNullable(st);
                })
        );
    }

    // -------- Pending lists (cached) --------

    public List<String> incomingRequestUsernames(PlayerRef me) {
        return cache.getIncomingUsernames(me.id(), () ->
                feature.getOrm().runInTransaction(s ->
                        s.createQuery(
                                        "SELECT p.username FROM FriendRelationEntity fr " +
                                                "JOIN fr.player p " +
                                                "WHERE fr.friend.id = :me AND fr.status = :st",
                                        String.class)
                                .setParameter("me", me.id())
                                .setParameter("st", FriendStatus.PENDING)
                                .list()
                )
        );
    }

    public List<String> outgoingRequestUsernames(PlayerRef me) {
        return cache.getOutgoingUsernames(me.id(), () ->
                feature.getOrm().runInTransaction(s ->
                        s.createQuery(
                                        "SELECT f.username FROM FriendRelationEntity fr " +
                                                "JOIN fr.friend f " +
                                                "WHERE fr.player.id = :me AND fr.status = :st",
                                        String.class)
                                .setParameter("me", me.id())
                                .setParameter("st", FriendStatus.PENDING)
                                .list()
                )
        );
    }

    // -------- Accepted friends (cached) --------

    /** Snapshots of my accepted friends (id/uuid/username). */
    public List<FriendSnapshot> acceptedFriendSnapshots(PlayerRef me) {
        return List.copyOf(cache.getAcceptedSnapshots(me.id(), () ->
                feature.getOrm().runInTransaction(s ->
                        s.createQuery(
                                        "SELECT new nl.hauntedmc.proxyfeatures.features.friends.entity.FriendSnapshot(" +
                                                "f.id, f.uuid, f.username) " +
                                                "FROM FriendRelationEntity fr " +
                                                "JOIN fr.friend f " +
                                                "WHERE fr.player.id = :me AND fr.status = :st",
                                        FriendSnapshot.class)
                                .setParameter("me", me.id())
                                .setParameter("st", FriendStatus.ACCEPTED)
                                .list()
                )
        ));
    }

    /** Usernames of my accepted friends (derived from snapshots). */
    public List<String> acceptedFriendUsernames(PlayerRef me) {
        return acceptedFriendSnapshots(me).stream()
                .map(FriendSnapshot::username)
                .toList();
    }

    /** Usernames of players I blocked. */
    public List<String> blockedUsernames(PlayerRef me) {
        return List.copyOf(cache.getBlockedUsernames(me.id(), () ->
                feature.getOrm().runInTransaction(s ->
                        s.createQuery(
                                        "SELECT f.username FROM FriendRelationEntity fr " +
                                                "JOIN fr.friend f " +
                                                "WHERE fr.player.id = :me AND fr.status = :st",
                                        String.class)
                                .setParameter("me", me.id())
                                .setParameter("st", FriendStatus.BLOCKED)
                                .list()
                )
        ));
    }

    public boolean isBlockedByTarget(PlayerRef me, PlayerRef target) {
        Optional<FriendStatus> rev = relationStatus(target, me);
        if (rev.isPresent()) return rev.get() == FriendStatus.BLOCKED;

        Long cnt = feature.getOrm().runInTransaction(s ->
                s.createQuery(
                                "SELECT COUNT(fr) FROM FriendRelationEntity fr " +
                                        "WHERE fr.player.id = :target AND fr.friend.id = :me AND fr.status = :st",
                                Long.class)
                        .setParameter("target", target.id())
                        .setParameter("me", me.id())
                        .setParameter("st", FriendStatus.BLOCKED)
                        .uniqueResult());
        return cnt != null && cnt > 0;
    }

    public boolean didIBlockTarget(PlayerRef me, PlayerRef target) {
        Optional<FriendStatus> st = relationStatus(me, target);
        if (st.isPresent()) return st.get() == FriendStatus.BLOCKED;

        Long cnt = feature.getOrm().runInTransaction(s ->
                s.createQuery(
                                "SELECT COUNT(fr) FROM FriendRelationEntity fr " +
                                        "WHERE fr.player.id = :me AND fr.friend.id = :target AND fr.status = :st",
                                Long.class)
                        .setParameter("me", me.id())
                        .setParameter("target", target.id())
                        .setParameter("st", FriendStatus.BLOCKED)
                        .uniqueResult());
        return cnt != null && cnt > 0;
    }

    // -------- Mutations (with cache invalidation) --------

    private void upsertRelationInTx(org.hibernate.Session s,
                                    long ownerId, long friendId,
                                    FriendStatus status) {
        FriendRelationEntity rel = s.createQuery(
                        "FROM FriendRelationEntity WHERE player.id = :p AND friend.id = :f",
                        FriendRelationEntity.class)
                .setParameter("p", ownerId)
                .setParameter("f", friendId)
                .uniqueResult();
        if (rel == null) {
            PlayerEntity mo = s.get(PlayerEntity.class, ownerId);
            PlayerEntity mf = s.get(PlayerEntity.class, friendId);
            rel = new FriendRelationEntity(mo, mf, status);
            s.persist(rel);
        } else {
            rel.setStatus(status);
            s.merge(rel);
        }
    }

    /** Create a PENDING request if none exists, and invalidate caches immediately. */
    public boolean createPending(PlayerRef from, PlayerRef to) {
        Boolean ok = feature.getOrm().runInTransaction(s -> {
            FriendRelationEntity existing = s.createQuery(
                            "FROM FriendRelationEntity WHERE player.id = :f AND friend.id = :t",
                            FriendRelationEntity.class)
                    .setParameter("f", from.id())
                    .setParameter("t", to.id())
                    .uniqueResult();
            if (existing != null) return false;

            PlayerEntity f = s.get(PlayerEntity.class, from.id());
            PlayerEntity t = s.get(PlayerEntity.class, to.id());
            s.persist(new FriendRelationEntity(f, t, FriendStatus.PENDING));
            return true;
        });

        if (Boolean.TRUE.equals(ok)) {
            cache.invalidateRelation(from.id(), to.id());
            cache.invalidatePlayer(from.id());
            cache.invalidatePlayer(to.id());
        }
        return Boolean.TRUE.equals(ok);
    }

    public boolean acceptPending(PlayerRef from, PlayerRef to) {
        Boolean result = feature.getOrm().runInTransaction(s -> {
            FriendRelationEntity incoming = s.createQuery(
                            "FROM FriendRelationEntity WHERE player.id = :from AND friend.id = :to",
                            FriendRelationEntity.class)
                    .setParameter("from", from.id())
                    .setParameter("to", to.id())
                    .uniqueResult();

            if (incoming == null || incoming.getStatus() != FriendStatus.PENDING) {
                return false;
            }

            Long blocks = s.createQuery(
                            "SELECT COUNT(fr) FROM FriendRelationEntity fr WHERE " +
                                    "((fr.player.id = :a AND fr.friend.id = :b) OR (fr.player.id = :b AND fr.friend.id = :a)) " +
                                    "AND fr.status = :st",
                            Long.class)
                    .setParameter("a", from.id()).setParameter("b", to.id())
                    .setParameter("st", FriendStatus.BLOCKED)
                    .uniqueResult();
            if (blocks != null && blocks > 0) {
                return false;
            }

            incoming.setStatus(FriendStatus.ACCEPTED);
            s.merge(incoming);
            upsertRelationInTx(s, to.id(), from.id(), FriendStatus.ACCEPTED);
            return true;
        });
        if (Boolean.TRUE.equals(result)) {
            cache.invalidateRelation(from.id(), to.id());
            cache.invalidatePlayer(from.id());
            cache.invalidatePlayer(to.id());
        }
        return Boolean.TRUE.equals(result);
    }

    public boolean removeFriendship(PlayerRef a, PlayerRef b) {
        Boolean changed = feature.getOrm().runInTransaction(s -> {
            boolean ch = false;

            FriendRelationEntity ab = s.createQuery(
                            "FROM FriendRelationEntity WHERE player.id = :a AND friend.id = :b",
                            FriendRelationEntity.class)
                    .setParameter("a", a.id()).setParameter("b", b.id()).uniqueResult();

            FriendRelationEntity ba = s.createQuery(
                            "FROM FriendRelationEntity WHERE player.id = :b AND friend.id = :a",
                            FriendRelationEntity.class)
                    .setParameter("a", a.id()).setParameter("b", b.id()).uniqueResult();

            if (ab != null && ab.getStatus() == FriendStatus.ACCEPTED) {
                s.remove(ab);
                ch = true;
            }
            if (ba != null && ba.getStatus() == FriendStatus.ACCEPTED) {
                s.remove(ba);
                ch = true;
            }

            return ch;
        });
        if (Boolean.TRUE.equals(changed)) {
            cache.invalidateRelation(a.id(), b.id());
            cache.invalidatePlayer(a.id());
            cache.invalidatePlayer(b.id());
        }
        return Boolean.TRUE.equals(changed);
    }

    public void block(PlayerRef blocker, PlayerRef target) {
        feature.getOrm().runInTransaction(s -> {
            upsertRelationInTx(s, blocker.id(), target.id(), FriendStatus.BLOCKED);

            FriendRelationEntity reverse = s.createQuery(
                            "FROM FriendRelationEntity WHERE player.id = :t AND friend.id = :b",
                            FriendRelationEntity.class)
                    .setParameter("t", target.id()).setParameter("b", blocker.id()).uniqueResult();
            if (reverse != null && reverse.getStatus() != FriendStatus.BLOCKED) {
                s.remove(reverse);
            }
            return null;
        });
        cache.invalidateRelation(blocker.id(), target.id());
        cache.invalidatePlayer(blocker.id());
        cache.invalidatePlayer(target.id());
    }

    public boolean unblock(PlayerRef blocker, PlayerRef target) {
        Boolean ok = feature.getOrm().runInTransaction(s -> {
            FriendRelationEntity rel = s.createQuery(
                            "FROM FriendRelationEntity WHERE player.id = :b AND friend.id = :t",
                            FriendRelationEntity.class)
                    .setParameter("b", blocker.id()).setParameter("t", target.id()).uniqueResult();
            if (rel != null && rel.getStatus() == FriendStatus.BLOCKED) {
                s.remove(rel);
                return true;
            }
            return false;
        });
        if (Boolean.TRUE.equals(ok)) {
            cache.invalidateRelation(blocker.id(), target.id());
            cache.invalidatePlayer(blocker.id());
            cache.invalidatePlayer(target.id());
        }
        return Boolean.TRUE.equals(ok);
    }

    public boolean cancelPending(PlayerRef from, PlayerRef to) {
        Boolean ok = feature.getOrm().runInTransaction(s -> {
            FriendRelationEntity rel = s.createQuery(
                            "FROM FriendRelationEntity WHERE player.id = :f AND friend.id = :t",
                            FriendRelationEntity.class)
                    .setParameter("f", from.id()).setParameter("t", to.id())
                    .uniqueResult();
            if (rel != null && rel.getStatus() == FriendStatus.PENDING) {
                s.remove(rel);
                return true;
            }
            return false;
        });
        if (Boolean.TRUE.equals(ok)) {
            cache.invalidateRelation(from.id(), to.id());
            cache.invalidatePlayer(from.id());
            cache.invalidatePlayer(to.id());
        }
        return Boolean.TRUE.equals(ok);
    }

    public boolean denyIncoming(PlayerRef to, PlayerRef from) {
        Boolean ok = feature.getOrm().runInTransaction(s -> {
            FriendRelationEntity rel = s.createQuery(
                            "FROM FriendRelationEntity WHERE player.id = :f AND friend.id = :t",
                            FriendRelationEntity.class)
                    .setParameter("f", from.id()).setParameter("t", to.id())
                    .uniqueResult();
            if (rel != null && rel.getStatus() == FriendStatus.PENDING) {
                s.remove(rel);
                return true;
            }
            return false;
        });
        if (Boolean.TRUE.equals(ok)) {
            cache.invalidateRelation(from.id(), to.id());
            cache.invalidatePlayer(from.id());
            cache.invalidatePlayer(to.id());
        }
        return Boolean.TRUE.equals(ok);
    }

    public int acceptAll(PlayerRef me) {
        record Pair(long otherId) {}
        var affected = new ArrayList<Pair>();

        Integer accepted = feature.getOrm().runInTransaction(s -> {
            // gather senders for pending incoming
            List<Long> senderIds = s.createQuery(
                            "SELECT fr.player.id FROM FriendRelationEntity fr " +
                                    "WHERE fr.friend.id = :me AND fr.status = :st",
                            Long.class)
                    .setParameter("me", me.id())
                    .setParameter("st", FriendStatus.PENDING)
                    .list();

            int n = 0;
            for (Long otherId : senderIds) {
                Long blocks = s.createQuery(
                                "SELECT COUNT(fr) FROM FriendRelationEntity fr WHERE " +
                                        "((fr.player.id = :a AND fr.friend.id = :b) OR (fr.player.id = :b AND fr.friend.id = :a)) " +
                                        "AND fr.status = :st",
                                Long.class)
                        .setParameter("a", me.id()).setParameter("b", otherId)
                        .setParameter("st", FriendStatus.BLOCKED)
                        .uniqueResult();
                if (blocks != null && blocks > 0) continue;

                FriendRelationEntity incoming = s.createQuery(
                                "FROM FriendRelationEntity WHERE player.id = :from AND friend.id = :to",
                                FriendRelationEntity.class)
                        .setParameter("from", otherId)
                        .setParameter("to", me.id())
                        .uniqueResult();

                if (incoming != null && incoming.getStatus() == FriendStatus.PENDING) {
                    incoming.setStatus(FriendStatus.ACCEPTED);
                    s.merge(incoming);
                    upsertRelationInTx(s, me.id(), otherId, FriendStatus.ACCEPTED);
                    n++;
                    affected.add(new Pair(otherId));
                }
            }
            return n;
        });

        int count = accepted == null ? 0 : accepted;
        if (count > 0) {
            cache.invalidatePlayer(me.id());
            for (var pair : affected) {
                cache.invalidateRelation(me.id(), pair.otherId());
                cache.invalidatePlayer(pair.otherId());
            }
        }
        return count;
    }

    public int denyAll(PlayerRef me) {
        var affected = new ArrayList<Long>();
        Integer denied = feature.getOrm().runInTransaction(s -> {
            List<FriendRelationEntity> reqs = s.createQuery(
                            "FROM FriendRelationEntity WHERE friend.id = :me AND status = :st",
                            FriendRelationEntity.class)
                    .setParameter("me", me.id()).setParameter("st", FriendStatus.PENDING)
                    .list();
            for (FriendRelationEntity rel : reqs) {
                affected.add(rel.getPlayer().getId());
                s.remove(rel);
            }
            return reqs.size();
        });
        int count = denied == null ? 0 : denied;
        if (count > 0) {
            cache.invalidatePlayer(me.id());
            for (Long otherId : affected) {
                cache.invalidateRelation(me.id(), otherId);
                cache.invalidatePlayer(otherId);
            }
        }
        return count;
    }
}
