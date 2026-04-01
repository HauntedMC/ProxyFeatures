package nl.hauntedmc.proxyfeatures.features.commandrelay.internal.messaging;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandRelayMessageTest {

    @Test
    void gsonRoundTripAndEmptyPayloadBehaveAsExpected() {
        Gson gson = new Gson();

        CommandRelayMessage roundTrip = gson.fromJson(
                gson.toJson(new CommandRelayMessage("/say hello", "hub-1")),
                CommandRelayMessage.class
        );
        assertEquals("/say hello", roundTrip.getCommand());
        assertEquals("hub-1", roundTrip.getOriginServer());

        CommandRelayMessage defaults = gson.fromJson("{}", CommandRelayMessage.class);
        assertNull(defaults.getCommand());
        assertNull(defaults.getOriginServer());
    }
}
