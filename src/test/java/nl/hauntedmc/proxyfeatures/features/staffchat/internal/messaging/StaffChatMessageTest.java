package nl.hauntedmc.proxyfeatures.features.staffchat.internal.messaging;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StaffChatMessageTest {

    @Test
    void gsonRoundTripAndEmptyPayloadBehaveAsExpected() {
        Gson gson = new Gson();

        StaffChatMessage roundTrip = gson.fromJson(
                gson.toJson(new StaffChatMessage("staffchat", "!", "hello", "Remy", "hub")),
                StaffChatMessage.class
        );
        assertEquals("!", roundTrip.getPrefix());
        assertEquals("hello", roundTrip.getMessage());
        assertEquals("Remy", roundTrip.getSenderName());
        assertEquals("hub", roundTrip.getSenderServer());

        StaffChatMessage defaults = gson.fromJson("{}", StaffChatMessage.class);
        assertNull(defaults.getPrefix());
        assertNull(defaults.getMessage());
        assertNull(defaults.getSenderName());
        assertNull(defaults.getSenderServer());
    }
}
