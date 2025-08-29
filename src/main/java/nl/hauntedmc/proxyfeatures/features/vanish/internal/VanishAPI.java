package nl.hauntedmc.proxyfeatures.features.vanish.internal;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.vanish.Vanish;

import java.util.List;
import java.util.UUID;

/**
 * Public API other features can use to query vanish-aware player stats.
 */
public class VanishAPI {

    private final Vanish feature;

    public VanishAPI(Vanish feature) {
        this.feature = feature;
    }

    /** All online players minus the currently vanished online players. */
    public int getAdjustedPlayerCount() {
        return feature.getVanishRegistry().getAdjustedOnlineCount();
    }

    /** List of online players excluding those currently vanished. */
    public List<Player> getAdjustedOnlinePlayers() {
        return feature.getVanishRegistry().getAdjustedOnlinePlayers();
    }

    /** List of currently vanished online players. */
    public List<Player> getVanishedPlayers() {
        return feature.getVanishRegistry().getVanishedOnlinePlayers();
    }

    /** Number of currently vanished online players. */
    public int getVanishedCount() {
        return feature.getVanishRegistry().getVanishedOnlineCount();
    }

    public boolean isVanished(UUID uuid) {
        return feature.getVanishRegistry().isVanished(uuid);
    }
}
