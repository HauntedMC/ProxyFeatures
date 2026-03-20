package nl.hauntedmc.proxyfeatures.api.player;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.api.APIRegistry;
import nl.hauntedmc.proxyfeatures.api.io.localization.Language;
import nl.hauntedmc.proxyfeatures.features.antivpn.api.CountryAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LanguageAPITest {

    @AfterEach
    void clearRegistry() {
        APIRegistry.clear();
    }

    @Test
    void classCanBeInstantiated() {
        assertNotNull(new LanguageAPI());
    }

    @Test
    void getPlayerLanguageDefaultsToNlWhenPlayerOrApiMissing() {
        assertEquals(Language.NL, LanguageAPI.getPlayerLanguage(null));

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        assertEquals(Language.NL, LanguageAPI.getPlayerLanguage(player));
    }

    @Test
    void getPlayerLanguageUsesRegisteredApi() {
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);

        nl.hauntedmc.proxyfeatures.features.playerlanguage.api.LanguageAPI api =
                mock(nl.hauntedmc.proxyfeatures.features.playerlanguage.api.LanguageAPI.class);
        when(api.get(uuid)).thenReturn(Language.DE);
        APIRegistry.register(nl.hauntedmc.proxyfeatures.features.playerlanguage.api.LanguageAPI.class, api);

        assertEquals(Language.DE, LanguageAPI.getPlayerLanguage(player));

        when(api.get(uuid)).thenReturn(null);
        assertEquals(Language.NL, LanguageAPI.getPlayerLanguage(player));
    }

    @Test
    void getPlayerCountryDefaultsAndUsesRegisteredCountryApi() {
        UUID uuid = UUID.randomUUID();
        assertEquals("UNKNOWN", LanguageAPI.getPlayerCountry(uuid));

        CountryAPI countryApi = mock(CountryAPI.class);
        when(countryApi.getCountry(uuid)).thenReturn(Optional.of("NL"));
        APIRegistry.register(CountryAPI.class, countryApi);

        assertEquals("NL", LanguageAPI.getPlayerCountry(uuid));
    }
}
