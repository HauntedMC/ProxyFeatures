package nl.hauntedmc.proxyfeatures.features.vanish.internal;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.vanish.Vanish;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks players that are currently vanished AND online on the proxy.
 * Updates come from Redis (proxy.vanish.update). We also clean up on disconnect.
 */
public class VanishRegistry {

    private final Vanish feature;

    // Store online vanished players: UUID -> last known name (for convenience)
    private final Map<UUID, String> vanishedOnline = new ConcurrentHashMap<>();

    public VanishRegistry(Vanish feature) {
        this.feature = feature;
    }

    /**
     * Update a single player vanish state based on an incoming message.
     */
    public void applyUpdate(UUID uuid, String name, boolean vanished) {
        if (uuid == null) return;

        // Only track online players to keep "currently vanished" semantics exact.
        Optional<Player> online = feature.getPlugin().getProxyInstance().getPlayer(uuid);
        if (online.isEmpty()) {
            // Ensure it's not left behind if the player is offline
            vanishedOnline.remove(uuid);
            return;
        }

        if (vanished) {
            vanishedOnline.put(uuid, name != null ? name : online.get().getUsername());
        } else {
            vanishedOnline.remove(uuid);
        }
    }

    /**
     * Remove an entry on disconnect to avoid staleness.
     */
    public void remove(UUID uuid) {
        if (uuid != null) {
            vanishedOnline.remove(uuid);
        }
    }

    /**
     * Clear the registry (on feature disable).
     */
    public void clear() {
        vanishedOnline.clear();
    }

    /**
     * True if the given UUID is currently vanished (and online).
     */
    public boolean isVanished(UUID uuid) {
        return uuid != null && vanishedOnline.containsKey(uuid);
    }

    /**
     * Returns number of currently vanished online players.
     */
    public int getVanishedOnlineCount() {
        // Defensive intersect with real online players to be extra safe
        Set<UUID> onlineUuids = feature.getPlugin().getProxyInstance().getAllPlayers().stream()
                .map(Player::getUniqueId).collect(Collectors.toSet());
        return (int) vanishedOnline.keySet().stream().filter(onlineUuids::contains).count();
    }

    /**
     * Returns a snapshot of the vanished online players as Player objects.
     */
    public List<Player> getVanishedOnlinePlayers() {
        Set<UUID> vanished = new HashSet<>(vanishedOnline.keySet());
        return feature.getPlugin().getProxyInstance().getAllPlayers().stream()
                .filter(p -> vanished.contains(p.getUniqueId()))
                .toList();
    }

    /**
     * Returns a snapshot of the adjusted online players (excluding vanished).
     */
    public List<Player> getAdjustedOnlinePlayers() {
        Set<UUID> vanished = new HashSet<>(vanishedOnline.keySet());
        return feature.getPlugin().getProxyInstance().getAllPlayers().stream()
                .filter(p -> !vanished.contains(p.getUniqueId()))
                .toList();
    }

    /**
     * Adjusted count = all online - vanished online
     */
    public int getAdjustedOnlineCount() {
        int all = feature.getPlugin().getProxyInstance().getAllPlayers().size();
        return Math.max(0, all - getVanishedOnlineCount());
    }
}
