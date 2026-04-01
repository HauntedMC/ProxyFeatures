package nl.hauntedmc.proxyfeatures.features.antivpn.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.features.antivpn.AntiVPN;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    @Test
    void notifiesOnlyPermittedPlayersAndSuppressesDuplicatesWithinCooldown() {
        AntiVPN feature = mock(AntiVPN.class);
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        LocalizationHandler localization = mock(LocalizationHandler.class);
        LocalizationHandler.MessageBuilder builder = mock(LocalizationHandler.MessageBuilder.class);
        when(feature.getConfigHandler()).thenReturn(cfg);
        when(feature.getLocalizationHandler()).thenReturn(localization);
        when(feature.getPlugin()).thenReturn(plugin);
        when(cfg.node("notify")).thenReturn(ConfigNode.ofRaw(java.util.Map.of("cooldown_seconds", 30L), "notify"));
        when(localization.getMessage(anyString())).thenReturn(builder);
        when(builder.with(anyString(), anyString())).thenReturn(builder);
        when(builder.forAudience(any())).thenReturn(builder);
        when(builder.build()).thenReturn(Component.text("notification"));

        Player allowed = mock(Player.class);
        Player denied = mock(Player.class);
        when(allowed.hasPermission("proxyfeatures.feature.antivpn.notify")).thenReturn(true);
        when(denied.hasPermission("proxyfeatures.feature.antivpn.notify")).thenReturn(false);

        ProxyServer proxy = mock(ProxyServer.class);
        when(proxy.getAllPlayers()).thenReturn(List.of(allowed, denied));
        when(plugin.getProxy()).thenReturn(proxy);

        NotificationService service = new NotificationService(feature);
        service.notifyVpnBlocked("Remy");
        service.notifyVpnBlocked("Remy"); // suppressed by cooldown key
        service.notifyRegionUnknownBlocked("Remy"); // different key => delivered

        verify(allowed, times(2)).sendMessage(Component.text("notification"));
        verify(denied, never()).sendMessage(any(Component.class));
        verify(localization, times(1)).getMessage("antivpn.notify_vpn");
        verify(localization, times(1)).getMessage("antivpn.notify_region_unknown");
    }

    @Test
    void regionNotificationIncludesCountryAndAllowsNullCountry() {
        AntiVPN feature = mock(AntiVPN.class);
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        LocalizationHandler localization = mock(LocalizationHandler.class);
        LocalizationHandler.MessageBuilder builder = mock(LocalizationHandler.MessageBuilder.class);
        when(feature.getConfigHandler()).thenReturn(cfg);
        when(feature.getLocalizationHandler()).thenReturn(localization);
        when(feature.getPlugin()).thenReturn(plugin);
        when(cfg.node("notify")).thenReturn(ConfigNode.ofRaw(java.util.Map.of("cooldown_seconds", 30L), "notify"));
        when(localization.getMessage("antivpn.notify_region")).thenReturn(builder);
        when(builder.with(anyString(), anyString())).thenReturn(builder);
        when(builder.forAudience(any())).thenReturn(builder);
        when(builder.build()).thenReturn(Component.text("notification"));

        Player allowed = mock(Player.class);
        when(allowed.hasPermission("proxyfeatures.feature.antivpn.notify")).thenReturn(true);

        ProxyServer proxy = mock(ProxyServer.class);
        when(proxy.getAllPlayers()).thenReturn(List.of(allowed));
        when(plugin.getProxy()).thenReturn(proxy);

        NotificationService service = new NotificationService(feature);
        service.notifyRegionBlocked("Remy", "NL");
        service.notifyRegionBlocked("Alex", null);

        verify(builder).with("country", "NL");
        verify(builder).with("country", "");
        verify(allowed, times(2)).sendMessage(Component.text("notification"));
    }
}
