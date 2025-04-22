package nl.hauntedmc.proxyfeatures.common.util;

import com.velocitypowered.api.proxy.Player;
import de.myzelyam.api.vanish.VelocityVanishAPI;
import nl.hauntedmc.commonlib.localization.Language;

public class VelocityUtils {

    /**
     * Checks if a player is vanished using the VanishAPI.
     */
    public static boolean isVanished(Player player) {
        return VelocityVanishAPI.isInvisible(player);
    }

    /**
     * Default method for detecting the player's language.
     * Modify this as needed to detect a player's actual preferred language.
     */
    public static Language getPlayerLanguage(Player player) {
        return Language.NL;
    }
}
