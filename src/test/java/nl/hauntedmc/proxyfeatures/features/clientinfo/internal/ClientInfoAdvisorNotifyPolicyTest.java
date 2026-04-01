package nl.hauntedmc.proxyfeatures.features.clientinfo.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.api.io.config.ConfigNode;
import nl.hauntedmc.proxyfeatures.features.clientinfo.ClientInfo;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.localization.LocalizationHandler;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClientInfoAdvisorNotifyPolicyTest {

    private ClientInfo feature;
    private ProxyFeatures plugin;
    private FeatureConfigHandler config;
    private ClientInfoSettingsService settingsService;
    private ProxyServer proxy;
    private Player player;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        feature = mock(ClientInfo.class);
        plugin = mock(ProxyFeatures.class);
        config = mock(FeatureConfigHandler.class);
        settingsService = mock(ClientInfoSettingsService.class);
        proxy = mock(ProxyServer.class);
        player = mock(Player.class);
        playerId = UUID.randomUUID();

        FeatureLogger logger = mock(FeatureLogger.class);
        LocalizationHandler localization = mock(LocalizationHandler.class);
        LocalizationHandler.MessageBuilder builder = mock(LocalizationHandler.MessageBuilder.class);

        when(feature.getConfigHandler()).thenReturn(config);
        when(feature.getLogger()).thenReturn(logger);
        when(feature.getLocalizationHandler()).thenReturn(localization);
        when(feature.getPlugin()).thenReturn(plugin);
        when(plugin.getProxy()).thenReturn(proxy);

        when(localization.getMessage(anyString())).thenReturn(builder);
        when(builder.with(anyString(), anyString())).thenReturn(builder);
        when(builder.with(anyString(), any(Number.class))).thenReturn(builder);
        when(builder.with(anyString(), any(Component.class))).thenReturn(builder);
        when(builder.forAudience(any())).thenReturn(builder);
        when(builder.build()).thenReturn(Component.text("ok"));

        stubConfig(true, 10_000L, true, false);

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getUsername()).thenReturn("Remy");
        when(proxy.getAllPlayers()).thenReturn(List.of());
        when(proxy.getPlayer(playerId)).thenReturn(Optional.of(player));
    }

    @Test
    void maybeNotifySendsOnlyOnceWhenConfiguredPerSession() throws Exception {
        when(settingsService.isNotificationsEnabled(playerId, "Remy")).thenReturn(true);
        stubConfig(true, 0L, false, true);

        ClientInfoAdvisor advisor = spy(new ClientInfoAdvisor(feature, settingsService));
        doReturn(List.of(rec("view_distance", "2", "6"))).when(advisor).evaluate(player);

        advisor.maybeNotify(playerId);
        advisor.maybeNotify(playerId);

        verify(player, times(1)).sendMessage(any(Component.class));
        verify(settingsService, times(1)).isNotificationsEnabled(playerId, "Remy");
    }

    @Test
    void maybeNotifySkipsUnchangedRecommendationsWhenOnlyIfChanged() throws Exception {
        when(settingsService.isNotificationsEnabled(playerId, "Remy")).thenReturn(true);
        stubConfig(true, 0L, true, false);

        ClientInfoAdvisor advisor = spy(new ClientInfoAdvisor(feature, settingsService));
        doReturn(List.of(rec("particles", "MINIMAL", "ALL"))).when(advisor).evaluate(player);

        advisor.maybeNotify(playerId);
        advisor.maybeNotify(playerId);

        verify(player, times(1)).sendMessage(any(Component.class));
    }

    @Test
    void maybeNotifyAppliesCooldownOnlyWhenFingerprintUnchanged() throws Exception {
        when(settingsService.isNotificationsEnabled(playerId, "Remy")).thenReturn(true);
        stubConfig(true, 60_000L, false, false);

        ClientInfoAdvisor advisor = spy(new ClientInfoAdvisor(feature, settingsService));
        doReturn(List.of(rec("chat_mode", "HIDDEN", "SHOWN"))).when(advisor).evaluate(player);

        advisor.maybeNotify(playerId);
        advisor.maybeNotify(playerId);

        verify(player, times(1)).sendMessage(any(Component.class));
    }

    @Test
    void maybeNotifyBypassesCooldownWhenRecommendationsChange() throws Exception {
        when(settingsService.isNotificationsEnabled(playerId, "Remy")).thenReturn(true);
        stubConfig(true, 60_000L, false, false);

        ClientInfoAdvisor advisor = spy(new ClientInfoAdvisor(feature, settingsService));
        doReturn(
                List.of(rec("view_distance", "2", "6")),
                List.of(rec("view_distance", "3", "6"))
        ).when(advisor).evaluate(player);

        advisor.maybeNotify(playerId);
        advisor.maybeNotify(playerId);

        verify(player, times(2)).sendMessage(any(Component.class));
    }

    @Test
    void maybeNotifyFailsClosedWhenSettingsLookupThrows() {
        when(settingsService.isNotificationsEnabled(playerId, "Remy"))
                .thenThrow(new RuntimeException("db unavailable"));

        ClientInfoAdvisor advisor = new ClientInfoAdvisor(feature, settingsService);
        advisor.maybeNotify(playerId);

        verify(player, never()).sendMessage(any(Component.class));
    }

    private static ClientInfoAdvisor.Recommendation rec(String id, String found, String recommended) {
        return new ClientInfoAdvisor.Recommendation(id, Component.text(id), found, recommended);
    }

    private void stubConfig(boolean notifyEnabled, long cooldownMs, boolean onlyIfChanged, boolean oncePerSession) {
        when(config.get("notify.enabled", Boolean.class, true)).thenReturn(notifyEnabled);
        when(config.get("notify.debounce_millis", Long.class, 5000L)).thenReturn(10_000L);
        when(config.get("notify.cooldown_millis", Long.class, 30L * 60L * 1000L)).thenReturn(cooldownMs);
        when(config.get("notify.only_send_if_changed", Boolean.class, true)).thenReturn(onlyIfChanged);
        when(config.get("notify.only_once_per_session", Boolean.class, false)).thenReturn(oncePerSession);
        when(config.get("checks.view_distance.enabled", Boolean.class, true)).thenReturn(true);
        when(config.get("checks.chat_mode.enabled", Boolean.class, true)).thenReturn(true);
        when(config.get("checks.particles.enabled", Boolean.class, true)).thenReturn(true);
        when(config.get("recommend.view_distance_min", Integer.class, 5)).thenReturn(5);
        when(config.get("recommend.chat_mode", String.class, "SHOWN")).thenReturn(PlayerSettings.ChatMode.SHOWN.name());
        when(config.get("recommend.particles", String.class, "ALL")).thenReturn(PlayerSettings.ParticleStatus.ALL.name());
        when(config.node("profiles")).thenReturn(ConfigNode.ofRaw(Map.of(), "profiles"));
    }
}
