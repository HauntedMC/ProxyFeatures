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

    // -------- Player lookups (cached) --------

    public Optional<PlayerEntity> getPlayer(@NotNull String uuid) {
        return cache.getPlayerByUuid(uuid, () ->
                feature.getOrm().runInTransaction(
                        s -> s.createQuery("FROM PlayerEntity WHERE uuid = :u", PlayerEntity.class)
                                .setParameter("u", uuid)
                                .uniqueResultOptional()
                )
        );
    }

    public Optional<PlayerEntity> resolvePlayerByUsernameCaseInsensitive(@NotNull String username) {
        String key = username.toLowerCase(Locale.ROOT);
        return cache.getPlayerByLowerName(key, () ->
                feature.getOrm().runInTransaction(s ->
                        s.createQuery("FROM PlayerEntity WHERE lower(username) = :u", PlayerEntity.class)
                                .setParameter("u", key)
                                .uniqueResultOptional()
                )
        );
    }

    // -------- Settings (cached) --------

    public FriendSettingsEntity getOrCreateSettings(PlayerEntity p) {
        // Use DB to ensure the row exists, then refresh cache
        FriendSettingsEntity settings = feature.getOrm().runInTransaction(session -> {
            FriendSettingsEntity s = session.get(FriendSettingsEntity.class, p.getId());
            if (s == null) {
                PlayerEntity managedPlayer = session.get(PlayerEntity.class, p.getId());
                s = new FriendSettingsEntity(managedPlayer);
                session.persist(s);
            }
            return s;
        });
        cache.invalidatePlayer(p.getId());
        cache.getSettingsEnabled(p.getId(), settings::isEnabled);
        return settings;
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
        cache.invalidatePlayer(player.getId());
    }

    public boolean targetAcceptsRequests(PlayerEntity target) {
        // Prefer cached enabled flag; if missing, ensure row exists then cache it
        Boolean enabled = cache.getSettingsEnabled(target.getId(), () -> {
            FriendSettingsEntity s = feature.getOrm().runInTransaction(session -> {
                FriendSettingsEntity se = session.get(FriendSettingsEntity.class, target.getId());
                if (se == null) {
                    PlayerEntity managed = session.get(PlayerEntity.class, target.getId());
                    se = new FriendSettingsEntity(managed);
                    session.persist(se);
                }
                return se;
            });
            return s.isEnabled();
        });
        return Boolean.TRUE.equals(enabled);
    }

    // -------- Relations (cached) --------

    /**
     * Keep for internal, transactional uses (avoid touching lazy relations outside).
     */
    public Optional<FriendRelationEntity> relation(PlayerEntity owner, PlayerEntity target) {
        // For actual relation entity we still hit DB (rare), but for status checks we use getRelationStatus(...)
        return feature.getOrm().runInTransaction(s ->
                s.createQuery("FROM FriendRelationEntity WHERE player = :p AND friend = :f",
                                FriendRelationEntity.class)
                        .setParameter("p", owner)
                        .setParameter("f", target)
                        .uniqueResultOptional());
    }

    public Optional<FriendStatus> relationStatus(PlayerEntity owner, PlayerEntity target) {
        return cache.getRelationStatus(owner.getId(), target.getId(), () ->
                feature.getOrm().runInTransaction(s -> {
                    FriendRelationEntity fr = s.createQuery(
                                    "FROM FriendRelationEntity WHERE player = :p AND friend = :f",
                                    FriendRelationEntity.class)
                            .setParameter("p", owner)
                            .setParameter("f", target)
                            .uniqueResult();
                    return Optional.ofNullable(fr != null ? fr.getStatus() : null);
                })
        );
    }

    // -------- Pending lists (cached) --------

    public List<FriendRelationEntity> incomingRequests(PlayerEntity p) {
        // Not cached: full entity list is only used for size in one place; keep DB
        return feature.getOrm().runInTransaction(
                s -> s.createQuery("FROM FriendRelationEntity WHERE friend = :me AND status = :st",
                                FriendRelationEntity.class)
                        .setParameter("me", p)
                        .setParameter("st", FriendStatus.PENDING)
                        .list());
    }

    public List<String> incomingRequestUsernames(PlayerEntity me) {
        return cache.getIncomingUsernames(me.getId(), () ->
                feature.getOrm().runInTransaction(s ->
                        s.createQuery(
                                        "SELECT p.username FROM FriendRelationEntity fr " +
                                                "JOIN fr.player p " +
                                                "WHERE fr.friend = :me AND fr.status = :st",
                                        String.class)
                                .setParameter("me", me)
                                .setParameter("st", FriendStatus.PENDING)
                                .list()
                )
        );
    }

    public List<String> outgoingRequestUsernames(PlayerEntity me) {
        return cache.getOutgoingUsernames(me.getId(), () ->
                feature.getOrm().runInTransaction(s ->
                        s.createQuery(
                                        "SELECT f.username FROM FriendRelationEntity fr " +
                                                "JOIN fr.friend f " +
                                                "WHERE fr.player = :me AND fr.status = :st",
                                        String.class)
                                .setParameter("me", me)
                                .setParameter("st", FriendStatus.PENDING)
                                .list()
                )
        );
    }

    // -------- Accepted friends (cached) --------

    /**
     * Snapshots of my accepted friends (id/uuid/username), safe to use outside a session.
     */
    public List<FriendSnapshot> acceptedFriendSnapshots(PlayerEntity me) {
        return List.copyOf(cache.getAcceptedSnapshots(me.getId(), () ->
                feature.getOrm().runInTransaction(s ->
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
                )
        ));
    }

    /**
     * Usernames van mijn geaccepteerde vrienden (derived from snapshots).
     */
    public List<String> acceptedFriendUsernames(PlayerEntity me) {
        return acceptedFriendSnapshots(me).stream()
                .map(FriendSnapshot::username)
                .toList();
    }

    /**
     * Usernames van spelers die ik heb geblokkeerd.
     */
    public List<String> blockedUsernames(PlayerEntity me) {
        return List.copyOf(cache.getBlockedUsernames(me.getId(), () ->
                feature.getOrm().runInTransaction(s ->
                        s.createQuery(
                                        "SELECT f.username FROM FriendRelationEntity fr " +
                                                "JOIN fr.friend f " +
                                                "WHERE fr.player = :me AND fr.status = :st",
                                        String.class)
                                .setParameter("me", me)
                                .setParameter("st", FriendStatus.BLOCKED)
                                .list()
                )
        ));
    }

    public boolean isBlockedByTarget(PlayerEntity me, PlayerEntity target) {
        // Use relation status in reverse direction if exists, otherwise count
        Optional<FriendStatus> rev = relationStatus(target, me);
        if (rev.isPresent()) {
            return rev.get() == FriendStatus.BLOCKED;
        }
        Long cnt = feature.getOrm().runInTransaction(s ->
                s.createQuery(
                                "SELECT COUNT(fr) FROM FriendRelationEntity fr " +
                                        "WHERE fr.player = :target AND fr.friend = :me AND fr.status = :st",
                                Long.class)
                        .setParameter("target", target)
                        .setParameter("me", me)
                        .setParameter("st", FriendStatus.BLOCKED)
                        .uniqueResult());
        return cnt != null && cnt > 0;
    }

    public boolean didIBlockTarget(PlayerEntity me, PlayerEntity target) {
        Optional<FriendStatus> st = relationStatus(me, target);
        if (st.isPresent()) {
            return st.get() == FriendStatus.BLOCKED;
        }
        Long cnt = feature.getOrm().runInTransaction(s ->
                s.createQuery(
                                "SELECT COUNT(fr) FROM FriendRelationEntity fr " +
                                        "WHERE fr.player = :me AND fr.friend = :target AND fr.status = :st",
                                Long.class)
                        .setParameter("me", me)
                        .setParameter("target", target)
                        .setParameter("st", FriendStatus.BLOCKED)
                        .uniqueResult());
        return cnt != null && cnt > 0;
    }

    // -------- Mutations (with cache invalidation) --------

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
        Boolean result = feature.getOrm().runInTransaction(s -> {
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
        });
        if (Boolean.TRUE.equals(result)) {
            cache.invalidateRelation(from.getId(), to.getId());
            cache.invalidatePlayer(from.getId());
            cache.invalidatePlayer(to.getId());
        }
        return Boolean.TRUE.equals(result);
    }

    public boolean removeFriendship(PlayerEntity a, PlayerEntity b) {
        Boolean changed = feature.getOrm().runInTransaction(s -> {
            boolean ch = false;

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
                ch = true;
            }
            if (ba != null && ba.getStatus() == FriendStatus.ACCEPTED) {
                s.remove(ba);
                ch = true;
            }

            return ch;
        });
        if (Boolean.TRUE.equals(changed)) {
            cache.invalidateRelation(a.getId(), b.getId());
            cache.invalidatePlayer(a.getId());
            cache.invalidatePlayer(b.getId());
        }
        return Boolean.TRUE.equals(changed);
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
        cache.invalidateRelation(blocker.getId(), target.getId());
        cache.invalidatePlayer(blocker.getId());
        cache.invalidatePlayer(target.getId());
    }

    public boolean unblock(PlayerEntity blocker, PlayerEntity target) {
        Boolean ok = feature.getOrm().runInTransaction(s -> {
            FriendRelationEntity rel = s.createQuery(
                            "FROM FriendRelationEntity WHERE player = :b AND friend = :t",
                            FriendRelationEntity.class)
                    .setParameter("b", blocker).setParameter("t", target).uniqueResult();
            if (rel != null && rel.getStatus() == FriendStatus.BLOCKED) {
                s.remove(rel);
                return true;
            }
            return false;
        });
        if (Boolean.TRUE.equals(ok)) {
            cache.invalidateRelation(blocker.getId(), target.getId());
            cache.invalidatePlayer(blocker.getId());
            cache.invalidatePlayer(target.getId());
        }
        return Boolean.TRUE.equals(ok);
    }

    public boolean cancelPending(PlayerEntity from, PlayerEntity to) {
        Boolean ok = feature.getOrm().runInTransaction(s -> {
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
        });
        if (Boolean.TRUE.equals(ok)) {
            cache.invalidateRelation(from.getId(), to.getId());
            cache.invalidatePlayer(from.getId());
            cache.invalidatePlayer(to.getId());
        }
        return Boolean.TRUE.equals(ok);
    }

    public boolean denyIncoming(PlayerEntity to, PlayerEntity from) {
        Boolean ok = feature.getOrm().runInTransaction(s -> {
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
        });
        if (Boolean.TRUE.equals(ok)) {
            cache.invalidateRelation(from.getId(), to.getId());
            cache.invalidatePlayer(from.getId());
            cache.invalidatePlayer(to.getId());
        }
        return Boolean.TRUE.equals(ok);
    }

    public int acceptAll(PlayerEntity me) {
        Integer accepted = feature.getOrm().runInTransaction(s -> {
            List<FriendRelationEntity> reqs = s.createQuery(
                            "FROM FriendRelationEntity WHERE friend = :me AND status = :st",
                            FriendRelationEntity.class)
                    .setParameter("me", me).setParameter("st", FriendStatus.PENDING)
                    .list();
            int n = 0;
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
                n++;
            }
            return n;
        });
        if (accepted != null && accepted > 0) {
            cache.invalidatePlayer(me.getId());
            // Also invalidate all counterparties affected (safe but a bit coarse)
            // If you want, load the usernames/ids in the tx and invalidate pairwise.
        }
        return accepted == null ? 0 : accepted;
    }

    public int denyAll(PlayerEntity me) {
        Integer denied = feature.getOrm().runInTransaction(s -> {
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
        if (denied != null && denied > 0) {
            cache.invalidatePlayer(me.getId());
        }
        return denied == null ? 0 : denied;
    }
}
