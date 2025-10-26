package nl.hauntedmc.proxyfeatures.features.playerlanguage.api;

import nl.hauntedmc.proxyfeatures.api.io.localization.Language;

import java.util.UUID;

public interface LanguageAPI {
    Language get(UUID playerUuid);
    void set(UUID playerUuid, Language language);
}
