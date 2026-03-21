package nl.hauntedmc.proxyfeatures.features.playerlanguage.command;

import nl.hauntedmc.proxyfeatures.api.io.localization.Language;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageCommandPolicyTest {

    @Test
    void parseLanguageHandlesValidAndInvalidTokens() {
        assertEquals(Optional.of(Language.EN), LanguageCommandPolicy.parseLanguage("en"));
        assertEquals(Optional.of(Language.DE), LanguageCommandPolicy.parseLanguage(" DE "));
        assertEquals(Optional.empty(), LanguageCommandPolicy.parseLanguage("unknown"));
        assertEquals(Optional.empty(), LanguageCommandPolicy.parseLanguage(null));
    }

    @Test
    void isLanguageTokenReflectsParseability() {
        assertTrue(LanguageCommandPolicy.isLanguageToken("nl"));
        assertFalse(LanguageCommandPolicy.isLanguageToken("remy"));
    }

    @Test
    void suggestionsForNoArgsContainLanguagesAndMaybePlayers() {
        List<String> languages = List.of("DE", "EN", "NL");
        List<String> players = List.of("Remy", "Alex");

        assertEquals(
                List.of("DE", "EN", "NL"),
                LanguageCommandPolicy.suggestions(false, new String[0], languages, players)
        );

        assertEquals(
                List.of("DE", "EN", "NL", "Remy", "Alex"),
                LanguageCommandPolicy.suggestions(true, new String[0], languages, players)
        );
    }

    @Test
    void suggestionsForSingleArgCombineLanguageAndPlayerMatchesForStaff() {
        List<String> out = LanguageCommandPolicy.suggestions(
                true,
                new String[]{"n"},
                List.of("DE", "EN", "NL"),
                List.of("Noah", "Alex")
        );

        assertEquals(List.of("NL", "Noah"), out);
    }

    @Test
    void suggestionsForTwoArgsReturnLanguageMatchesOnlyForStaff() {
        List<String> out = LanguageCommandPolicy.suggestions(
                true,
                new String[]{"Remy", "d"},
                List.of("DE", "EN", "NL"),
                List.of("Remy")
        );

        assertEquals(List.of("DE"), out);
    }

    @Test
    void suggestionsForUnsupportedShapesAreEmpty() {
        List<String> out = LanguageCommandPolicy.suggestions(
                false,
                new String[]{"Remy", "en"},
                List.of("DE", "EN", "NL"),
                List.of("Remy")
        );

        assertEquals(List.of(), out);
    }
}
