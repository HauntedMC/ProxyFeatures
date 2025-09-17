package nl.hauntedmc.proxyfeatures.features.queue.util;

import com.velocitypowered.api.proxy.Player;

/**
 * Priority is determined solely via numeric permissions: "queue.priority.<N>".
 * Higher N means higher priority (e.g., 3 > 2 > 1).
 */
public class PriorityResolver {

    public int resolve(Player player) {
        int best = 0;
        int highest = 3;
        final String prefix = "proxyfeatures.feature.queue.priority.";
        for (int lvl = 1; lvl <= highest; lvl++) {
            if (player.hasPermission(prefix + lvl)) {
                best = lvl;
            }
        }
        return best;
    }
}
