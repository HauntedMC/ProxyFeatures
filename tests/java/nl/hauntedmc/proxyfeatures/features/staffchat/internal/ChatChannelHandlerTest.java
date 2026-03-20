package nl.hauntedmc.proxyfeatures.features.staffchat.internal;

import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatChannelHandlerTest {

    @Test
    void constructorNormalizesPrefixesAndBuildsExpectedChannels() {
        ChatChannelHandler handler = new ChatChannelHandler(featureWithPrefixes("   ", " @ ", null));
        Map<String, ChatChannel> channels = handler.getChannels();

        assertTrue(channels.containsKey("!"));
        assertTrue(channels.containsKey("@"));
        assertTrue(channels.containsKey("#"));

        assertEquals("staff", handler.getChannelByPrefix("!").getId());
        assertEquals("team", handler.getChannelByPrefix("@").getId());
        assertEquals("admin", handler.getChannelByPrefix("#").getId());
        assertNull(handler.getChannelByPrefix("?"));
    }

    @Test
    void initializeViewersAddsPlayersToChannelsByPermission() {
        ChatChannelHandler handler = new ChatChannelHandler(featureWithPrefixes("!", "@", "#"));

        Player staffOnly = mock(Player.class);
        when(staffOnly.hasPermission(anyString()))
                .thenAnswer(inv -> "proxyfeatures.feature.staffchat.staff".equals(inv.getArgument(0)));

        Player all = mock(Player.class);
        when(all.hasPermission(anyString())).thenReturn(true);

        handler.initializeViewers(List.of(staffOnly, all));

        assertEquals(2, handler.getChannelByPrefix("!").getViewers().size());
        assertEquals(1, handler.getChannelByPrefix("@").getViewers().size());
        assertEquals(1, handler.getChannelByPrefix("#").getViewers().size());
    }

    private static StaffChat featureWithPrefixes(String staff, String team, String admin) {
        StaffChat feature = mock(StaffChat.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        when(feature.getConfigHandler()).thenReturn(cfg);
        when(cfg.get("staff_prefix", String.class, "!")).thenReturn(staff);
        when(cfg.get("team_prefix", String.class, "?")).thenReturn(team);
        when(cfg.get("admin_prefix", String.class, "#")).thenReturn(admin);
        return feature;
    }
}
