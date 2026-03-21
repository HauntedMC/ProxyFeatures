package nl.hauntedmc.proxyfeatures.features.friends.listener;

import nl.hauntedmc.proxyfeatures.features.friends.entity.FriendSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class FriendActivityPolicy {

    enum ConnectType {
        NONE,
        ONLINE,
        SWITCH
    }

    record ConnectPlan(ConnectType type, String from, String to) {
    }

    private FriendActivityPolicy() {
    }

    static ConnectPlan classifyServerConnect(Optional<String> previousServer, String currentServer) {
        if (previousServer.isEmpty()) {
            return new ConnectPlan(ConnectType.ONLINE, null, currentServer);
        }

        String from = previousServer.get();
        if (from.equalsIgnoreCase(currentServer)) {
            return new ConnectPlan(ConnectType.NONE, from, currentServer);
        }

        return new ConnectPlan(ConnectType.SWITCH, from, currentServer);
    }

    static List<UUID> parseFriendUuids(List<FriendSnapshot> snapshots) {
        List<UUID> parsed = new ArrayList<>(snapshots.size());
        for (FriendSnapshot snapshot : snapshots) {
            if (snapshot == null || snapshot.uuid() == null || snapshot.uuid().isBlank()) {
                continue;
            }
            try {
                parsed.add(UUID.fromString(snapshot.uuid()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return parsed;
    }
}
