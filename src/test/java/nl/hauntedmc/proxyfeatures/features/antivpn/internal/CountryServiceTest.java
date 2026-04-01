package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CountryServiceTest {

    @Test
    void stagePromoteAndClearFlowWorks() {
        CountryService service = new CountryService(Duration.ofSeconds(60));
        UUID uuid = UUID.randomUUID();

        service.stageForUsername("Remy", "nl");
        service.promoteToUuid("REMY", uuid);

        assertEquals(Optional.of("NL"), service.getCountry(uuid));

        service.clear(uuid);
        assertEquals(Optional.empty(), service.getCountry(uuid));
    }

    @Test
    void invalidInputsAreIgnoredWithoutThrowing() {
        CountryService service = new CountryService(Duration.ofSeconds(60));
        UUID uuid = UUID.randomUUID();

        service.stageForUsername(null, "NL");
        service.stageForUsername("x", " ");
        service.promoteToUuid(null, uuid);
        service.promoteToUuid("x", null);
        service.clear(null);

        assertTrue(service.getCountry(uuid).isEmpty());
    }
}
