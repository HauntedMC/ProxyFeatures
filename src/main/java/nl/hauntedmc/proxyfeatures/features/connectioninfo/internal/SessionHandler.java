package nl.hauntedmc.proxyfeatures.features.connectioninfo.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SessionHandler {
    private final ConcurrentMap<UUID, Instant> joinTimes = new ConcurrentHashMap<>();

    /**
     * Called by the listener on post-login
     */
    public void recordJoin(UUID playerUuid) {
        joinTimes.put(playerUuid, Instant.now());
    }

    /**
     * Called by the listener on disconnect
     */
    public void clearJoin(UUID playerUuid) {
        joinTimes.remove(playerUuid);
    }

    /**
     * Lookup join-time for session duration
     */
    public Optional<Instant> getJoinTime(UUID playerUuid) {
        return Optional.ofNullable(joinTimes.get(playerUuid));
    }
}
