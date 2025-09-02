package nl.hauntedmc.proxyfeatures.common.util;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.commonlib.localization.Language;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.api.LanguageAPI;

public class LanguageUtils {

    /** Default method for detecting the player's language (proxy-side). */
    public static Language getPlayerLanguage(Player player) {
        if (player == null) return Language.NL;
        return APIRegistry.get(LanguageAPI.class)
                .map(api -> api.get(player.getUniqueId()))
                .orElse(Language.NL);
    }
}
