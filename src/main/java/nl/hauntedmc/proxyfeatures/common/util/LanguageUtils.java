package nl.hauntedmc.proxyfeatures.common.util;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.api.io.localization.Language;
import nl.hauntedmc.proxyfeatures.features.playerlanguage.api.LanguageAPI;
import nl.hauntedmc.proxyfeatures.features.antivpn.api.CountryAPI;

import java.util.UUID;

public class LanguageUtils {

    private static final String DEFAULT_COUNTRY = "UNKNOWN";

    /** Detect the player's language via the LanguageAPI (proxy-side). */
    public static Language getPlayerLanguage(Player player) {
        if (player == null) return Language.NL;
        return APIRegistry.get(LanguageAPI.class)
                .map(api -> api.get(player.getUniqueId()))
                .orElse(Language.NL);
    }

    /** Get the player's ISO country code via the CountryAPI (proxy-side). */
    public static String getPlayerCountry(UUID uuid) {
        return APIRegistry.get(CountryAPI.class)
                .flatMap(api -> api.getCountry(uuid))
                .orElse(DEFAULT_COUNTRY);
    }
}
