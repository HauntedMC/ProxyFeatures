package nl.hauntedmc.proxyfeatures.features.hlink.internal.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HLinkApiRequestsTest {

    @Test
    void accountRequestGettersAndSettersWork() {
        AccountRequest req = new AccountRequest();
        assertNull(req.getStatus());
        assertFalse(req.getExists());

        req.setStatus("ok");
        req.setExists(true);
        assertEquals("ok", req.getStatus());
        assertTrue(req.getExists());
    }

    @Test
    void linkRequestGettersAndSettersWork() {
        LinkRequest req = new LinkRequest();
        assertNull(req.getStatus());
        assertNull(req.getResults());
        assertNull(req.getFound());

        req.setStatus("ok");
        req.setResults("linked");
        req.setFound("player");
        assertEquals("ok", req.getStatus());
        assertEquals("linked", req.getResults());
        assertEquals("player", req.getFound());
    }
}
