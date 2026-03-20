package nl.hauntedmc.proxyfeatures.features.friends.support;

import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendSnapshot;
import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendStatus;
import nl.hauntedmc.proxyfeatures.features.friends.entity.PlayerRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FriendsCacheTest {

    @Test
    void loaderResultsAreCachedPerKey() {
        FriendsCache cache = new FriendsCache();
        AtomicInteger uuidCalls = new AtomicInteger();
        AtomicInteger nameCalls = new AtomicInteger();

        Optional<PlayerRef> byUuidA = cache.getPlayerByUuid("uuid-1", () -> {
            uuidCalls.incrementAndGet();
            return Optional.of(new PlayerRef(1L, "uuid-1", "Alice"));
        });
        Optional<PlayerRef> byUuidB = cache.getPlayerByUuid("uuid-1", () -> {
            uuidCalls.incrementAndGet();
            return Optional.empty();
        });

        Optional<PlayerRef> byNameA = cache.getPlayerByLowerName("alice", () -> {
            nameCalls.incrementAndGet();
            return Optional.of(new PlayerRef(1L, "uuid-1", "Alice"));
        });
        Optional<PlayerRef> byNameB = cache.getPlayerByLowerName("alice", () -> {
            nameCalls.incrementAndGet();
            return Optional.empty();
        });

        assertEquals(byUuidA, byUuidB);
        assertEquals(byNameA, byNameB);
        assertEquals(1, uuidCalls.get());
        assertEquals(1, nameCalls.get());
    }

    @Test
    void invalidatePlayerClearsPlayerScopedCaches() {
        FriendsCache cache = new FriendsCache();
        long playerId = 10L;
        AtomicInteger settingsCalls = new AtomicInteger();
        AtomicInteger acceptedCalls = new AtomicInteger();
        AtomicInteger blockedCalls = new AtomicInteger();
        AtomicInteger incomingCalls = new AtomicInteger();
        AtomicInteger outgoingCalls = new AtomicInteger();
        AtomicInteger uuidCalls = new AtomicInteger();

        cache.getSettingsEnabled(playerId, () -> {
            settingsCalls.incrementAndGet();
            return true;
        });
        cache.getAcceptedSnapshots(playerId, () -> {
            acceptedCalls.incrementAndGet();
            return List.of(new FriendSnapshot(2L, "u2", "Bob"));
        });
        cache.getBlockedUsernames(playerId, () -> {
            blockedCalls.incrementAndGet();
            return List.of("Bob");
        });
        cache.getIncomingUsernames(playerId, () -> {
            incomingCalls.incrementAndGet();
            return List.of("Carol");
        });
        cache.getOutgoingUsernames(playerId, () -> {
            outgoingCalls.incrementAndGet();
            return List.of("Dave");
        });
        cache.getPlayerByUuid("persisted", () -> {
            uuidCalls.incrementAndGet();
            return Optional.of(new PlayerRef(playerId, "persisted", "Owner"));
        });

        cache.invalidatePlayer(playerId);

        cache.getSettingsEnabled(playerId, () -> {
            settingsCalls.incrementAndGet();
            return false;
        });
        cache.getAcceptedSnapshots(playerId, () -> {
            acceptedCalls.incrementAndGet();
            return List.of();
        });
        cache.getBlockedUsernames(playerId, () -> {
            blockedCalls.incrementAndGet();
            return List.of();
        });
        cache.getIncomingUsernames(playerId, () -> {
            incomingCalls.incrementAndGet();
            return List.of();
        });
        cache.getOutgoingUsernames(playerId, () -> {
            outgoingCalls.incrementAndGet();
            return List.of();
        });
        cache.getPlayerByUuid("persisted", () -> {
            uuidCalls.incrementAndGet();
            return Optional.empty();
        });

        assertEquals(2, settingsCalls.get());
        assertEquals(2, acceptedCalls.get());
        assertEquals(2, blockedCalls.get());
        assertEquals(2, incomingCalls.get());
        assertEquals(2, outgoingCalls.get());
        assertEquals(1, uuidCalls.get());
    }

    @Test
    void invalidateRelationClearsBothDirections() {
        FriendsCache cache = new FriendsCache();
        AtomicInteger directCalls = new AtomicInteger();
        AtomicInteger reverseCalls = new AtomicInteger();

        cache.getRelationStatus(1L, 2L, () -> {
            directCalls.incrementAndGet();
            return Optional.of(FriendStatus.ACCEPTED);
        });
        cache.getRelationStatus(2L, 1L, () -> {
            reverseCalls.incrementAndGet();
            return Optional.of(FriendStatus.ACCEPTED);
        });

        cache.invalidateRelation(1L, 2L);

        cache.getRelationStatus(1L, 2L, () -> {
            directCalls.incrementAndGet();
            return Optional.of(FriendStatus.BLOCKED);
        });
        cache.getRelationStatus(2L, 1L, () -> {
            reverseCalls.incrementAndGet();
            return Optional.of(FriendStatus.PENDING);
        });

        assertEquals(2, directCalls.get());
        assertEquals(2, reverseCalls.get());
    }
}
