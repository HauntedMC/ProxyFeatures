package nl.hauntedmc.proxyfeatures.features.staffchat.internal;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.features.staffchat.StaffChat;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatChannelTest {

    @Test
    void channelMetadataAndViewerMembershipAreManagedCorrectly() {
        ChatChannel channel = new ChatChannel("staff", "!");
        Player a = mock(Player.class);
        Player b = mock(Player.class);

        assertEquals("staff", channel.getId());
        assertEquals("proxyfeatures.feature.staffchat.staff", channel.getPermission());
        assertEquals("staffchat.staff_format", channel.getFormatKey());
        assertEquals("!", channel.getPrefix());

        channel.addViewer(a);
        channel.addViewer(a);
        channel.addViewer(b);
        assertEquals(2, channel.getViewers().size());
        assertTrue(channel.getViewers().contains(a));
        assertTrue(channel.getViewers().contains(b));

        assertThrows(UnsupportedOperationException.class, () -> channel.getViewers().clear());

        channel.removeViewer(a);
        assertFalse(channel.getViewers().contains(a));
        assertTrue(channel.getViewers().contains(b));
    }

    @Test
    void broadcastSendsOnlyToAuthorizedViewersAndLogsMessage() {
        ChatChannel channel = new ChatChannel("staff", "!");
        Player allowed = mock(Player.class);
        Player denied = mock(Player.class);
        when(allowed.hasPermission("proxyfeatures.feature.staffchat.staff")).thenReturn(true);
        when(denied.hasPermission("proxyfeatures.feature.staffchat.staff")).thenReturn(false);
        channel.addViewer(allowed);
        channel.addViewer(denied);

        StaffChat feature = mock(StaffChat.class);
        LocalizationHandler localization = mock(LocalizationHandler.class);
        LocalizationHandler.MessageBuilder builder = mock(LocalizationHandler.MessageBuilder.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        Component formatted = Component.text("formatted");

        when(feature.getLocalizationHandler()).thenReturn(localization);
        when(feature.getLogger()).thenReturn(logger);
        when(localization.getMessage("staffchat.staff_format")).thenReturn(builder);
        when(builder.with(anyString(), anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(formatted);

        channel.broadcastMessage(feature, "hub-1", "Remy", "hello");

        verify(allowed).sendMessage(formatted);
        verify(denied, never()).sendMessage(formatted);
        verify(logger).info(formatted);
    }
}
