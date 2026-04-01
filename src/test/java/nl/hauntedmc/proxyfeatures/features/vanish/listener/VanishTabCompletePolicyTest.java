package nl.hauntedmc.proxyfeatures.features.vanish.listener;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanishTabCompletePolicyTest {

    @Test
    void normalizeNamesLowerSkipsBlankEntriesAndLowercasesValues() {
        Set<String> normalized = VanishTabCompletePolicy.normalizeNamesLower(
                List.of("Remy", "MiKa", " ", "REMY")
        );

        assertEquals(Set.of("remy", "mika"), normalized);
    }

    @Test
    void removeVanishedSuggestionsRemovesCaseInsensitiveMatches() {
        List<String> suggestions = new ArrayList<>(List.of("Remy", "help", "MIKA", "list"));
        VanishTabCompletePolicy.removeVanishedSuggestions(suggestions, Set.of("remy", "mika"));

        assertEquals(List.of("help", "list"), suggestions);
    }

    @Test
    void removeVanishedSuggestionsIsNoOpForEmptyInputs() {
        List<String> suggestions = new ArrayList<>(List.of("help"));
        VanishTabCompletePolicy.removeVanishedSuggestions(suggestions, Set.of());
        assertEquals(List.of("help"), suggestions);

        List<String> emptySuggestions = new ArrayList<>();
        VanishTabCompletePolicy.removeVanishedSuggestions(emptySuggestions, Set.of("remy"));
        assertTrue(emptySuggestions.isEmpty());
    }
}
