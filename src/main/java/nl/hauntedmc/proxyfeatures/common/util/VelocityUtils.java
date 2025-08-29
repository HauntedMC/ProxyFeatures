package nl.hauntedmc.proxyfeatures.common.util;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.commonlib.localization.Language;

public class VelocityUtils {

    /**
     * Default method for detecting the player's language.
     * Modify this as needed to detect a player's actual preferred language.
     */
    public static Language getPlayerLanguage(Player player) {
        return Language.NL;
    }
}
