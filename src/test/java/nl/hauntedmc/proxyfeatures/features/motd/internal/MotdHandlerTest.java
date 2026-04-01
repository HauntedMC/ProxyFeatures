package nl.hauntedmc.proxyfeatures.features.motd.internal;

import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.api.APIRegistry;
import nl.hauntedmc.proxyfeatures.features.motd.Motd;
import nl.hauntedmc.proxyfeatures.features.vanish.internal.VanishAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MotdHandlerTest {

    @AfterEach
    void tearDown() {
        APIRegistry.clear();
    }

    @Test
    void modifyServerPingAppliesMultiplierAndVanishAdjustment() {
        Motd feature = feature(2.0D);
        MotdHandler handler = new MotdHandler(feature);

        VanishAPI vanishApi = mock(VanishAPI.class);
        when(vanishApi.getVanishedCount()).thenReturn(2);
        APIRegistry.register(VanishAPI.class, vanishApi);

        ServerPing original = ping(10, 100);
        ServerPing updated = handler.modifyServerPing(original);
        ServerPing.Players players = updated.getPlayers().orElseThrow();

        assertEquals(16, players.getOnline());
        assertEquals(100, players.getMax());
    }

    @Test
    void modifyServerPingClampsNegativeMultiplierToZero() {
        Motd feature = feature(-5.0D);
        MotdHandler handler = new MotdHandler(feature);

        VanishAPI vanishApi = mock(VanishAPI.class);
        when(vanishApi.getVanishedCount()).thenReturn(0);
        APIRegistry.register(VanishAPI.class, vanishApi);

        ServerPing original = ping(5, 20);
        ServerPing updated = handler.modifyServerPing(original);
        ServerPing.Players players = updated.getPlayers().orElseThrow();

        assertEquals(0, players.getOnline());
        assertEquals(20, players.getMax());
    }

    private static Motd feature(double multiplier) {
        Motd feature = mock(Motd.class, RETURNS_DEEP_STUBS);
        when(feature.getPlugin().getFeatureLoadManager().getFeatureRegistry().isFeatureLoaded("VersionCheck"))
                .thenReturn(false);
        when(feature.getConfigHandler().get("playerCountMultiplier", Double.class, 1.0D)).thenReturn(multiplier);
        when(feature.getConfigHandler().get("motdline1", String.class, "")).thenReturn("Haunted");
        when(feature.getConfigHandler().getList("motdline2", String.class, List.of())).thenReturn(List.of("Welkom"));
        return feature;
    }

    private static ServerPing ping(int online, int max) {
        return new ServerPing(
                new ServerPing.Version(763, "1.20+"),
                new ServerPing.Players(online, max, List.of()),
                Component.text("Original MOTD"),
                null
        );
    }
}
