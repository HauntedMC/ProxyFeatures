package nl.hauntedmc.proxyfeatures.features.antivpn.api;

import java.util.Optional;
import java.util.UUID;

/** Simple API to get the player's ISO country code (e.g., "NL", "US"). */
public interface CountryAPI {
    /** Returns the country code for the given player UUID, if known. */
    Optional<String> getCountry(UUID uuid);
}
