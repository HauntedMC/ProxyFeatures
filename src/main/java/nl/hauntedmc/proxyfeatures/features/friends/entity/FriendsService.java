package nl.hauntedmc.proxyfeatures.features.friends.entity;

import jakarta.persistence.PersistenceException;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.friends.Friends;
import nl.hauntedmc.proxyfeatures.features.friends.support.FriendsCache;
import org.hibernate.exception.ConstraintViolationException;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            FriendSettingsEntity se = session.get(FriendSettingsEntity.class, p.id());
            if (se == null) {
                PlayerEntity managedPlayer = session.get(PlayerEntity.class, p.id());
                se = new FriendSettingsEntity(managedPlayer);
                session.persist(se);
            }
            return se;
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
            FriendSettingsEntity settings = feature.getOrm().runInTransaction(session -> {
                FriendSettingsEntity se = session.get(FriendSettingsEntity.class, target.id());
                if (se == null) {
                    PlayerEntity managed = session.get(PlayerEntity.class, target.id());
                    se = new FriendSettingsEntity(managed);
                    session.persist(se);
                }
                return se;
            });
            return settings.isEnabled();
        });
        return Boolean.TRUE.equals(enabled);
    }

    // -------- Relations --------

    /**
     * Fetch full relation entity (rare). Prefer relationStatus(...) on hot paths.
     */
    public Optional<FriendStatus> relationStatus(PlayerRef owner, PlayerRef target) {
        return cache.getRelationStatus(owner.id(), target.id(), () ->
                feature.getOrm().runInTransaction(s ->
                        s.createQuery(
                                        "SELECT fr.status FROM FriendRelationEntity fr " +
                                                "WHERE fr.player.id = :p AND fr.friend.id = :f",
                                        FriendStatus.class)
                                .setParameter("p", owner.id())
                                .setParameter("f", target.id())
                                .uniqueResultOptional()
                )
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

    /**
     * Snapshots of my accepted friends (id/uuid/username).
     */
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

    /**
     * Usernames of my accepted friends (derived from snapshots).
     */
    public List<String> acceptedFriendUsernames(PlayerRef me) {
        return acceptedFriendSnapshots(me).stream()
                .map(FriendSnapshot::username)
                .toList();
    }

    /**
     * Usernames of players I blocked.
     */
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

    /**
     * Create a PENDING request if none exists; tolerate races under the UNIQUE constraint.
     */
    public boolean createPending(PlayerRef from, PlayerRef to) {
        try {
            Boolean ok = feature.getOrm().runInTransaction(s -> {
                // Try to insert directly; if it exists, we’ll rely on the UNIQUE constraint.
                PlayerEntity f = s.get(PlayerEntity.class, from.id());
                PlayerEntity t = s.get(PlayerEntity.class, to.id());
                s.persist(new FriendRelationEntity(f, t, FriendStatus.PENDING));
                return true;
            });

            if (Boolean.TRUE.equals(ok)) {
                cache.invalidateRelation(from.id(), to.id());
                cache.invalidatePlayer(from.id());
                cache.invalidatePlayer(to.id());
                return true;
            }
            return false;
        } catch (PersistenceException e) {
            // Likely due to UNIQUE (player_id, friend_id) if another thread inserted first.
            Throwable cause = e.getCause();
            if (cause instanceof ConstraintViolationException) {
                return false;
            }
            return false;
        }
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
        Integer affected = feature.getOrm().runInTransaction(s ->
                s.createMutationQuery(
                                "DELETE FROM FriendRelationEntity fr " +
                                        "WHERE ((fr.player.id = :a AND fr.friend.id = :b) OR (fr.player.id = :b AND fr.friend.id = :a)) " +
                                        "AND fr.status = :st")
                        .setParameter("a", a.id())
                        .setParameter("b", b.id())
                        .setParameter("st", FriendStatus.ACCEPTED)
                        .executeUpdate());
        int count = affected == null ? 0 : affected;
        if (count > 0) {
            cache.invalidateRelation(a.id(), b.id());
            cache.invalidatePlayer(a.id());
            cache.invalidatePlayer(b.id());
            return true;
        }
        return false;
    }

    public void block(PlayerRef blocker, PlayerRef target) {
        feature.getOrm().runInTransaction(s -> {
            upsertRelationInTx(s, blocker.id(), target.id(), FriendStatus.BLOCKED);

            s.createMutationQuery(
                            "DELETE FROM FriendRelationEntity fr " +
                                    "WHERE fr.player.id = :t AND fr.friend.id = :b " +
                                    "AND fr.status <> :st")
                    .setParameter("t", target.id())
                    .setParameter("b", blocker.id())
                    .setParameter("st", FriendStatus.BLOCKED)
                    .executeUpdate();

            return null;
        });
        cache.invalidateRelation(blocker.id(), target.id());
        cache.invalidatePlayer(blocker.id());
        cache.invalidatePlayer(target.id());
    }

    public boolean unblock(PlayerRef blocker, PlayerRef target) {
        Integer deleted = feature.getOrm().runInTransaction(s ->
                s.createMutationQuery(
                                "DELETE FROM FriendRelationEntity fr " +
                                        "WHERE fr.player.id = :b AND fr.friend.id = :t AND fr.status = :st")
                        .setParameter("b", blocker.id())
                        .setParameter("t", target.id())
                        .setParameter("st", FriendStatus.BLOCKED)
                        .executeUpdate()
        );
        int count = deleted == null ? 0 : deleted;
        if (count > 0) {
            cache.invalidateRelation(blocker.id(), target.id());
            cache.invalidatePlayer(blocker.id());
            cache.invalidatePlayer(target.id());
            return true;
        }
        return false;
    }

    public boolean cancelPending(PlayerRef from, PlayerRef to) {
        Integer deleted = feature.getOrm().runInTransaction(s ->
                s.createMutationQuery(
                                "DELETE FROM FriendRelationEntity fr " +
                                        "WHERE fr.player.id = :f AND fr.friend.id = :t AND fr.status = :st")
                        .setParameter("f", from.id())
                        .setParameter("t", to.id())
                        .setParameter("st", FriendStatus.PENDING)
                        .executeUpdate()
        );
        int count = deleted == null ? 0 : deleted;
        if (count > 0) {
            cache.invalidateRelation(from.id(), to.id());
            cache.invalidatePlayer(from.id());
            cache.invalidatePlayer(to.id());
            return true;
        }
        return false;
    }

    public boolean denyIncoming(PlayerRef to, PlayerRef from) {
        Integer deleted = feature.getOrm().runInTransaction(s ->
                s.createMutationQuery(
                                "DELETE FROM FriendRelationEntity fr " +
                                        "WHERE fr.player.id = :f AND fr.friend.id = :t AND fr.status = :st")
                        .setParameter("f", from.id())
                        .setParameter("t", to.id())
                        .setParameter("st", FriendStatus.PENDING)
                        .executeUpdate()
        );
        int count = deleted == null ? 0 : deleted;
        if (count > 0) {
            cache.invalidateRelation(from.id(), to.id());
            cache.invalidatePlayer(from.id());
            cache.invalidatePlayer(to.id());
            return true;
        }
        return false;
    }

    public int acceptAll(PlayerRef me) {
        List<Long> affectedIds = new ArrayList<>();

        Integer accepted = feature.getOrm().runInTransaction(s -> {
            // 1) Load all pending incoming
            List<FriendRelationEntity> incoming = s.createQuery(
                            "FROM FriendRelationEntity fr " +
                                    "WHERE fr.friend.id = :me AND fr.status = :st",
                            FriendRelationEntity.class)
                    .setParameter("me", me.id())
                    .setParameter("st", FriendStatus.PENDING)
                    .list();

            if (incoming.isEmpty()) return 0;

            Set<Long> senderIds = incoming.stream()
                    .map(fr -> fr.getPlayer().getId())
                    .collect(Collectors.toSet());

            // 2) Load blocks for these pairs in one go
            Set<Long> blockedSenders = new HashSet<>(s.createQuery(
                            "SELECT CASE WHEN fr.player.id = :me THEN fr.friend.id ELSE fr.player.id END " +
                                    "FROM FriendRelationEntity fr " +
                                    "WHERE fr.status = :st AND " +
                                    "((fr.player.id = :me AND fr.friend.id IN :senders) " +
                                    " OR (fr.friend.id = :me AND fr.player.id IN :senders))",
                            Long.class)
                    .setParameter("me", me.id())
                    .setParameter("st", FriendStatus.BLOCKED)
                    .setParameter("senders", senderIds)
                    .list());

            // 3) Load existing reciprocals once (me -> senders)
            Map<Long, FriendRelationEntity> reciprocals = s.createQuery(
                            "FROM FriendRelationEntity fr " +
                                    "WHERE fr.player.id = :me AND fr.friend.id IN :senders",
                            FriendRelationEntity.class)
                    .setParameter("me", me.id())
                    .setParameter("senders", senderIds)
                    .list()
                    .stream()
                    .collect(Collectors.toMap(fr -> fr.getFriend().getId(), Function.identity()));

            int n = 0;
            for (FriendRelationEntity inc : incoming) {
                long otherId = inc.getPlayer().getId();
                if (blockedSenders.contains(otherId)) continue;

                // accept incoming
                inc.setStatus(FriendStatus.ACCEPTED);
                s.merge(inc);

                // ensure reciprocal
                FriendRelationEntity rec = reciprocals.get(otherId);
                if (rec == null) {
                    PlayerEntity meManaged = s.get(PlayerEntity.class, me.id());
                    PlayerEntity otherManaged = s.get(PlayerEntity.class, otherId);
                    rec = new FriendRelationEntity(meManaged, otherManaged, FriendStatus.ACCEPTED);
                    s.persist(rec);
                } else {
                    rec.setStatus(FriendStatus.ACCEPTED);
                    s.merge(rec);
                }

                n++;
                affectedIds.add(otherId);
            }
            return n;
        });

        int count = accepted == null ? 0 : accepted;
        if (count > 0) {
            cache.invalidatePlayer(me.id());
            for (Long otherId : affectedIds) {
                cache.invalidateRelation(me.id(), otherId);
                cache.invalidatePlayer(otherId);
            }
        }
        return count;
    }

    public int denyAll(PlayerRef me) {
        // Gather affected first (for cache invalidation), then delete in bulk.
        List<Long> affected = feature.getOrm().runInTransaction(s ->
                s.createQuery(
                                "SELECT fr.player.id FROM FriendRelationEntity fr " +
                                        "WHERE fr.friend.id = :me AND fr.status = :st",
                                Long.class)
                        .setParameter("me", me.id())
                        .setParameter("st", FriendStatus.PENDING)
                        .list()
        );

        if (affected.isEmpty()) return 0;

        Integer denied = feature.getOrm().runInTransaction(s ->
                s.createMutationQuery(
                                "DELETE FROM FriendRelationEntity fr " +
                                        "WHERE fr.friend.id = :me AND fr.status = :st")
                        .setParameter("me", me.id())
                        .setParameter("st", FriendStatus.PENDING)
                        .executeUpdate()
        );

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
