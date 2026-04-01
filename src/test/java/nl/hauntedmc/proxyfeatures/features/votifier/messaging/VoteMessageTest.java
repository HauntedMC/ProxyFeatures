package nl.hauntedmc.proxyfeatures.features.votifier.messaging;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoteMessageTest {

    @Test
    void gsonRoundTripAndEmptyPayloadBehaveAsExpected() {
        Gson gson = new Gson();

        VoteMessage roundTrip = gson.fromJson(
                gson.toJson(new VoteMessage("service", "user", "1.2.3.4", 123L)),
                VoteMessage.class
        );
        assertEquals("service", roundTrip.getServiceName());
        assertEquals("user", roundTrip.getUsername());
        assertEquals("1.2.3.4", roundTrip.getAddress());
        assertEquals(123L, roundTrip.getVoteTimestamp());

        VoteMessage defaults = gson.fromJson("{}", VoteMessage.class);
        assertNull(defaults.getServiceName());
        assertNull(defaults.getUsername());
        assertNull(defaults.getAddress());
        assertEquals(0L, defaults.getVoteTimestamp());
    }
}
