package nl.hauntedmc.proxyfeatures.features.announcer.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnnouncerAudienceFilterTest {

    @Test
    void allowsAppliesPermissionAllowAndDenyRules() {
        AnnouncerHandler.AudienceFilter filter = new AnnouncerHandler.AudienceFilter(
                " perm.node ",
                List.of("Hub", "SkyBlock"),
                List.of("SkyBlock")
        );

        assertFalse(filter.allows(null));

        Player noPerm = player("hub", false, "perm.node");
        assertFalse(filter.allows(noPerm));

        Player allowedServer = player("hub", true, "perm.node");
        assertTrue(filter.allows(allowedServer));

        Player deniedServer = player("skyblock", true, "perm.node");
        assertFalse(filter.allows(deniedServer));

        Player notInAllow = player("survival", true, "perm.node");
        assertFalse(filter.allows(notInAllow));
    }

    @Test
    void allowsHandlesMissingServerWhenNoAllowListIsConfigured() {
        AnnouncerHandler.AudienceFilter filter = new AnnouncerHandler.AudienceFilter(
                "",
                List.of(),
                List.of("hub")
        );

        Player noServer = mock(Player.class);
        when(noServer.getCurrentServer()).thenReturn(Optional.empty());
        assertTrue(filter.allows(noServer));
    }

    @Test
    void modeAndSettingsModelsAreReachable() {
        AnnouncerHandler.Mode mode = AnnouncerHandler.Mode.valueOf("SHUFFLE");
        AnnouncerHandler.AudienceFilter audience = new AnnouncerHandler.AudienceFilter("", List.of(), List.of());
        AnnouncerHandler.Settings settings = new AnnouncerHandler.Settings(
                java.time.Duration.ofSeconds(30),
                java.time.Duration.ofSeconds(5),
                mode,
                audience
        );

        assertEquals(AnnouncerHandler.Mode.SHUFFLE, mode);
        assertEquals(java.time.Duration.ofSeconds(30), settings.interval());
        assertEquals(java.time.Duration.ofSeconds(5), settings.initialDelay());
        assertSame(mode, settings.mode());
        assertSame(audience, settings.audience());
    }

    private static Player player(String serverName, boolean hasPerm, String permNode) {
        Player player = mock(Player.class);
        when(player.hasPermission(permNode)).thenReturn(hasPerm);

        ServerConnection connection = mock(ServerConnection.class);
        ServerInfo info = mock(ServerInfo.class);
        when(player.getCurrentServer()).thenReturn(Optional.of(connection));
        when(connection.getServerInfo()).thenReturn(info);
        when(info.getName()).thenReturn(serverName);

        return player;
    }
}
