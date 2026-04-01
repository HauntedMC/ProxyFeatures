package nl.hauntedmc.proxyfeatures.features.vanish.internal.messaging;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VanishStateMessageTest {

    @Test
    void gsonRoundTripAndEmptyPayloadBehaveAsExpected() {
        Gson gson = new Gson();

        VanishStateMessage roundTrip = gson.fromJson(
                gson.toJson(new VanishStateMessage("vanish_update", "uuid", "name", true, "hub")),
                VanishStateMessage.class
        );
        assertEquals("uuid", roundTrip.getPlayerUuid());
        assertEquals("name", roundTrip.getPlayerName());
        assertTrue(roundTrip.isVanished());
        assertEquals("hub", roundTrip.getServer());

        VanishStateMessage defaults = gson.fromJson("{}", VanishStateMessage.class);
        assertNull(defaults.getPlayerUuid());
        assertNull(defaults.getPlayerName());
        assertFalse(defaults.isVanished());
        assertNull(defaults.getServer());
    }
}
