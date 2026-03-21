package nl.hauntedmc.proxyfeatures.features.versioncheck.internal;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.features.versioncheck.VersionCheck;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VersionHandlerTest {

    @Test
    void constructorReadsConfigAndAppliesFriendlyFallback() {
        VersionCheck feature = mock(VersionCheck.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        when(feature.getConfigHandler()).thenReturn(cfg);
        when(cfg.get("minimum_protocol_version", Integer.class, 0)).thenReturn(763);
        when(cfg.get("friendly_protocol_name", String.class, "")).thenReturn(" ");

        VersionHandler handler = new VersionHandler(feature);

        assertEquals(763, handler.getMinimumProtcolVersion());
        assertEquals("unsupported", handler.getFriendlyProtocolName());
        assertTrue(handler.isUnsupportedVersion(762));
        assertFalse(handler.isUnsupportedVersion(763));
        assertFalse(handler.isAllowedVersion(762));
        assertTrue(handler.isAllowedVersion(763));
    }

    @Test
    void checkVersionDeniesUnsupportedClientsWithLocalizedMessage() {
        VersionCheck feature = mock(VersionCheck.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        LocalizationHandler localization = mock(LocalizationHandler.class);
        LocalizationHandler.MessageBuilder builder = mock(LocalizationHandler.MessageBuilder.class);
        when(feature.getConfigHandler()).thenReturn(cfg);
        when(feature.getLocalizationHandler()).thenReturn(localization);
        when(cfg.get("minimum_protocol_version", Integer.class, 0)).thenReturn(800);
        when(cfg.get("friendly_protocol_name", String.class, "")).thenReturn("1.21");
        when(localization.getMessage("versioncheck.unsupported_version")).thenReturn(builder);
        when(builder.forAudience(any())).thenReturn(builder);
        when(builder.with("friendly_protocol_name", "1.21")).thenReturn(builder);
        when(builder.build()).thenReturn(Component.text("unsupported"));

        Player player = mock(Player.class);
        ProtocolVersion protocol = mock(ProtocolVersion.class);
        when(player.getProtocolVersion()).thenReturn(protocol);
        when(protocol.getProtocol()).thenReturn(760);

        LoginEvent event = mock(LoginEvent.class);
        when(event.getPlayer()).thenReturn(player);

        VersionHandler handler = new VersionHandler(feature);
        handler.checkVersion(event);

        ArgumentCaptor<ResultedEvent.ComponentResult> captor =
                ArgumentCaptor.forClass(ResultedEvent.ComponentResult.class);
        verify(event).setResult(captor.capture());
        assertFalse(captor.getValue().isAllowed());
        assertTrue(captor.getValue().getReasonComponent().isPresent());
    }

    @Test
    void checkVersionDoesNothingForSupportedClients() {
        VersionCheck feature = mock(VersionCheck.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        when(feature.getConfigHandler()).thenReturn(cfg);
        when(cfg.get("minimum_protocol_version", Integer.class, 0)).thenReturn(700);
        when(cfg.get("friendly_protocol_name", String.class, "")).thenReturn("1.8");

        Player player = mock(Player.class);
        ProtocolVersion protocol = mock(ProtocolVersion.class);
        when(player.getProtocolVersion()).thenReturn(protocol);
        when(protocol.getProtocol()).thenReturn(760);

        LoginEvent event = mock(LoginEvent.class);
        when(event.getPlayer()).thenReturn(player);

        VersionHandler handler = new VersionHandler(feature);
        handler.checkVersion(event);

        verify(event, never()).setResult(any(ResultedEvent.ComponentResult.class));
        verify(feature, never()).getLocalizationHandler();
    }
}
