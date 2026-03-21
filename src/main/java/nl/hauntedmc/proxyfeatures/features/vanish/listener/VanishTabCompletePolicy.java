package nl.hauntedmc.proxyfeatures.features.vanish.listener;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class VanishTabCompletePolicy {

    private VanishTabCompletePolicy() {
    }

    static Set<String> normalizeNamesLower(Collection<String> names) {
        Set<String> normalized = new HashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            normalized.add(name.toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    static void removeVanishedSuggestions(List<String> suggestions, Set<String> vanishedNamesLower) {
        if (suggestions.isEmpty() || vanishedNamesLower.isEmpty()) {
            return;
        }
        suggestions.removeIf(s -> vanishedNamesLower.contains(s.toLowerCase(Locale.ROOT)));
    }
}
