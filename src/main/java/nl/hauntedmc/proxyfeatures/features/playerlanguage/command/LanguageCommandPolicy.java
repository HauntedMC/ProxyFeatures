package nl.hauntedmc.proxyfeatures.features.playerlanguage.command;

import nl.hauntedmc.proxyfeatures.api.io.localization.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class LanguageCommandPolicy {

    private LanguageCommandPolicy() {
    }

    static Optional<Language> parseLanguage(String token) {
        if (token == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(Language.valueOf(token.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    static boolean isLanguageToken(String token) {
        return parseLanguage(token).isPresent();
    }

    static List<String> suggestions(boolean canOthers,
                                    String[] args,
                                    List<String> languages,
                                    List<String> players) {
        if (args.length == 0) {
            List<String> out = new ArrayList<>(languages);
            if (canOthers) {
                out.addAll(players);
            }
            return out;
        }

        if (args.length == 1) {
            String partialUpper = args[0].toUpperCase(Locale.ROOT);
            List<String> langMatches = languages.stream()
                    .filter(s -> s.startsWith(partialUpper))
                    .toList();

            if (!canOthers) {
                return langMatches;
            }

            String partialRawLower = args[0].toLowerCase(Locale.ROOT);
            List<String> playerMatches = players.stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partialRawLower))
                    .toList();

            List<String> out = new ArrayList<>(langMatches);
            out.addAll(playerMatches);
            return out;
        }

        if (args.length == 2 && canOthers) {
            String partialUpper = args[1].toUpperCase(Locale.ROOT);
            return languages.stream()
                    .filter(s -> s.startsWith(partialUpper))
                    .toList();
        }

        return List.of();
    }
}
