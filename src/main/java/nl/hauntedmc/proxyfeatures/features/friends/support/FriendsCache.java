package nl.hauntedmc.proxyfeatures.features.friends.support;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendSnapshot;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Small local cache to reduce DB round-trips on hot paths.
 * TTLs are short to keep data fresh without coupling to cluster-wide invalidation.
 */
public class FriendsCache {

    private record PairKey(long a, long b) { }

    private final Cache<String, Optional<PlayerEntity>> playerByUuid =
            Caffeine.newBuilder()
                    .maximumSize(50_000)
                    .expireAfterWrite(Duration.ofMinutes(5))
                    .build();

    private final Cache<String, Optional<PlayerEntity>> playerByLowerName =
            Caffeine.newBuilder()
                    .maximumSize(50_000)
                    .expireAfterWrite(Duration.ofMinutes(2))
                    .build();

    private final Cache<Long, Boolean> settingsEnabledByPlayerId =
            Caffeine.newBuilder()
                    .maximumSize(100_000)
                    .expireAfterWrite(Duration.ofMinutes(10))
                    .build();

    private final Cache<Long, List<FriendSnapshot>> acceptedSnapshotsByPlayerId =
            Caffeine.newBuilder()
                    .maximumSize(100_000)
                    .expireAfterWrite(Duration.ofSeconds(45))
                    .build();

    private final Cache<Long, List<String>> blockedUsernamesByPlayerId =
            Caffeine.newBuilder()
                    .maximumSize(100_000)
                    .expireAfterWrite(Duration.ofSeconds(45))
                    .build();

    private final Cache<Long, List<String>> incomingUsernamesByPlayerId =
            Caffeine.newBuilder()
                    .maximumSize(100_000)
                    .expireAfterWrite(Duration.ofSeconds(45))
                    .build();

    private final Cache<Long, List<String>> outgoingUsernamesByPlayerId =
            Caffeine.newBuilder()
                    .maximumSize(100_000)
                    .expireAfterWrite(Duration.ofSeconds(45))
                    .build();

    private final Cache<PairKey, Optional<FriendStatus>> relationStatusCache =
            Caffeine.newBuilder()
                    .maximumSize(200_000)
                    .expireAfterWrite(Duration.ofSeconds(45))
                    .build();

    // -------- Getters with loaders --------

    public Optional<PlayerEntity> getPlayerByUuid(String uuid,
                                                  java.util.function.Supplier<Optional<PlayerEntity>> loader) {
        return playerByUuid.get(uuid, k -> loader.get());
    }

    public Optional<PlayerEntity> getPlayerByLowerName(String lowerName,
                                                       java.util.function.Supplier<Optional<PlayerEntity>> loader) {
        return playerByLowerName.get(lowerName, k -> loader.get());
    }

    public Boolean getSettingsEnabled(long playerId,
                                      java.util.function.Supplier<Boolean> loader) {
        return settingsEnabledByPlayerId.get(playerId, k -> loader.get());
    }

    public List<FriendSnapshot> getAcceptedSnapshots(long playerId,
                                                     java.util.function.Supplier<List<FriendSnapshot>> loader) {
        return acceptedSnapshotsByPlayerId.get(playerId, k -> loader.get());
    }

    public List<String> getBlockedUsernames(long playerId,
                                            java.util.function.Supplier<List<String>> loader) {
        return blockedUsernamesByPlayerId.get(playerId, k -> loader.get());
    }

    public List<String> getIncomingUsernames(long playerId,
                                             java.util.function.Supplier<List<String>> loader) {
        return incomingUsernamesByPlayerId.get(playerId, k -> loader.get());
    }

    public List<String> getOutgoingUsernames(long playerId,
                                             java.util.function.Supplier<List<String>> loader) {
        return outgoingUsernamesByPlayerId.get(playerId, k -> loader.get());
    }

    public Optional<FriendStatus> getRelationStatus(long ownerId, long targetId,
                                                    java.util.function.Supplier<Optional<FriendStatus>> loader) {
        return relationStatusCache.get(new PairKey(ownerId, targetId), k -> loader.get());
    }

    // -------- Invalidation helpers --------

    public void invalidatePlayer(long playerId) {
        settingsEnabledByPlayerId.invalidate(playerId);
        acceptedSnapshotsByPlayerId.invalidate(playerId);
        blockedUsernamesByPlayerId.invalidate(playerId);
        incomingUsernamesByPlayerId.invalidate(playerId);
        outgoingUsernamesByPlayerId.invalidate(playerId);
        // relationStatusCache is invalidated per relation via invalidateRelation
    }

    public void invalidatePlayerIdentity(String uuid, String lowerName) {
        if (uuid != null) playerByUuid.invalidate(uuid);
        if (lowerName != null) playerByLowerName.invalidate(lowerName);
    }

    public void invalidateRelation(long aId, long bId) {
        relationStatusCache.invalidate(new PairKey(aId, bId));
        relationStatusCache.invalidate(new PairKey(bId, aId));
    }
}
