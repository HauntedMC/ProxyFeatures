package nl.hauntedmc.proxyfeatures.features.vanish.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.vanish.Vanish;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VanishRegistryTest {

    @Test
    void applyUpdateTracksOnlinePlayersAndUnvanishRemovesThem() {
        Vanish feature = mock(Vanish.class);
        VanishRegistry registry = new VanishRegistry(feature);

        UUID uuid = UUID.randomUUID();
        Player player = player(uuid, "Remy");
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        ProxyServer proxy = mock(ProxyServer.class);
        when(feature.getPlugin()).thenReturn(plugin);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getPlayer(uuid)).thenReturn(Optional.of(player));

        registry.applyUpdate(uuid, null, true);
        assertTrue(registry.isVanished(uuid));

        registry.applyUpdate(uuid, "Remy", false);
        assertFalse(registry.isVanished(uuid));
    }

    @Test
    void applyUpdateIgnoresOfflinePlayersAndRemoveHandlesNull() {
        Vanish feature = mock(Vanish.class);
        VanishRegistry registry = new VanishRegistry(feature);

        UUID uuid = UUID.randomUUID();
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        ProxyServer proxy = mock(ProxyServer.class);
        when(feature.getPlugin()).thenReturn(plugin);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getPlayer(uuid)).thenReturn(Optional.empty());

        registry.applyUpdate(uuid, "Offline", true);
        assertFalse(registry.isVanished(uuid));

        registry.remove(null);
        registry.remove(uuid);
        assertFalse(registry.isVanished(uuid));
    }

    @Test
    void onlineSnapshotsAndCountsExcludeStaleEntries() {
        Vanish feature = mock(Vanish.class);
        VanishRegistry registry = new VanishRegistry(feature);

        UUID vanishedOnlineId = UUID.randomUUID();
        UUID staleVanishedId = UUID.randomUUID();
        UUID visibleId = UUID.randomUUID();

        Player vanishedOnline = player(vanishedOnlineId, "VanishedOnline");
        Player staleVanished = player(staleVanishedId, "StaleVanished");
        Player visible = player(visibleId, "Visible");

        ProxyFeatures plugin = mock(ProxyFeatures.class);
        ProxyServer proxy = mock(ProxyServer.class);
        when(feature.getPlugin()).thenReturn(plugin);
        when(plugin.getProxy()).thenReturn(proxy);
        when(proxy.getPlayer(vanishedOnlineId)).thenReturn(Optional.of(vanishedOnline));
        when(proxy.getPlayer(staleVanishedId)).thenReturn(Optional.of(staleVanished));
        when(proxy.getAllPlayers()).thenReturn(List.of(vanishedOnline, visible));

        registry.applyUpdate(vanishedOnlineId, "VanishedOnline", true);
        registry.applyUpdate(staleVanishedId, "StaleVanished", true);

        assertEquals(1, registry.getVanishedOnlineCount());
        assertEquals(List.of(vanishedOnline), registry.getVanishedOnlinePlayers());
        assertEquals(List.of(visible), registry.getAdjustedOnlinePlayers());
        assertEquals(1, registry.getAdjustedOnlineCount());

        registry.clear();
        assertFalse(registry.isVanished(vanishedOnlineId));
        assertFalse(registry.isVanished(staleVanishedId));
    }

    private static Player player(UUID uuid, String username) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getUsername()).thenReturn(username);
        return player;
    }
}
