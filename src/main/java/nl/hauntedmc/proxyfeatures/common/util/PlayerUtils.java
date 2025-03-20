package nl.hauntedmc.proxyfeatures.common.util;

import com.velocitypowered.api.proxy.Player;
import de.myzelyam.api.vanish.VelocityVanishAPI;

public class PlayerUtils {

    /**
     * Checks if a player is vanished using the VanishAPI.
     */
    public static boolean isVanished(Player player) {
        return VelocityVanishAPI.isInvisible(player);
    }
}
