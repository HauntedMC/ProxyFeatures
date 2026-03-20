package nl.hauntedmc.proxyfeatures.features.playerinfo.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerInfoServiceModelsTest {

    @Test
    void onlineStatusRecordStoresValues() {
        PlayerInfoService.OnlineStatus online = new PlayerInfoService.OnlineStatus(true, "hub");
        PlayerInfoService.OnlineStatus offline = new PlayerInfoService.OnlineStatus(false, null);

        assertTrue(online.online());
        assertEquals("hub", online.serverName());
        assertFalse(offline.online());
        assertNull(offline.serverName());
    }
}
