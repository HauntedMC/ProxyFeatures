package nl.hauntedmc.proxyfeatures.features.clientinfo.internal;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientInfoAdvisorModelsTest {

    @Test
    void recommendationRecordStoresExpectedValues() {
        Component setting = Component.text("view distance");
        ClientInfoAdvisor.Recommendation rec =
                new ClientInfoAdvisor.Recommendation("view_distance", setting, "2", "6");

        assertEquals("view_distance", rec.id());
        assertEquals(setting, rec.settingName());
        assertEquals("2", rec.found());
        assertEquals("6", rec.recommended());
    }
}
