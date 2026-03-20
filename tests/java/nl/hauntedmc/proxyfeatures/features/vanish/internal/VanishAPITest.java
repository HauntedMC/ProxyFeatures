package nl.hauntedmc.proxyfeatures.features.vanish.internal;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.vanish.Vanish;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VanishAPITest {

    @Test
    void apiDelegatesToRegistry() {
        Vanish feature = mock(Vanish.class);
        VanishRegistry registry = mock(VanishRegistry.class);
        when(feature.getVanishRegistry()).thenReturn(registry);

        Player a = mock(Player.class);
        Player b = mock(Player.class);
        UUID id = UUID.randomUUID();

        when(registry.getAdjustedOnlineCount()).thenReturn(12);
        when(registry.getAdjustedOnlinePlayers()).thenReturn(List.of(a));
        when(registry.getVanishedOnlinePlayers()).thenReturn(List.of(b));
        when(registry.getVanishedOnlineCount()).thenReturn(1);
        when(registry.isVanished(id)).thenReturn(true);

        VanishAPI api = new VanishAPI(feature);
        assertEquals(12, api.getAdjustedPlayerCount());
        assertEquals(List.of(a), api.getAdjustedOnlinePlayers());
        assertEquals(List.of(b), api.getVanishedPlayers());
        assertEquals(1, api.getVanishedCount());
        assertTrue(api.isVanished(id));
    }
}
