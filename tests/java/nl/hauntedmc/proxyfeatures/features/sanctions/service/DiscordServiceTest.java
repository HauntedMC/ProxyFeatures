package nl.hauntedmc.proxyfeatures.features.sanctions.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.proxyfeatures.api.util.http.DiscordUtils;
import nl.hauntedmc.proxyfeatures.api.util.text.placeholder.MessagePlaceholders;
import nl.hauntedmc.proxyfeatures.features.sanctions.Sanctions;
import nl.hauntedmc.proxyfeatures.features.sanctions.entity.SanctionEntity;
import nl.hauntedmc.proxyfeatures.framework.config.FeatureConfigHandler;
import nl.hauntedmc.proxyfeatures.framework.lifecycle.FeatureLifecycleManager;
import nl.hauntedmc.proxyfeatures.framework.lifecycle.FeatureTaskManager;
import nl.hauntedmc.proxyfeatures.framework.log.FeatureLogger;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DiscordServiceTest {

    @Test
    void sendWarnSkipsWhenWebhookNotConfigured() {
        Sanctions feature = configuredFeature(" ");
        FeatureTaskManager taskManager = feature.getLifecycleManager().getTaskManager();
        FeatureLogger logger = feature.getLogger();

        DiscordService service = new DiscordService(feature);
        service.sendWarn(null, null, "Admin");

        verify(taskManager, never()).scheduleTask(any(Runnable.class));
        verify(logger).warn(contains("Discord webhook URL not configured"));
    }

    @Test
    void sendUnbanSchedulesTaskAndPostsPayload() {
        Sanctions feature = configuredFeature("https://discord.example/hook");
        FeatureLogger logger = feature.getLogger();

        PlayerEntity target = new PlayerEntity();
        target.setUsername("Remy");

        try (MockedStatic<DiscordUtils> mocked = mockStatic(DiscordUtils.class)) {
            mocked.when(() -> DiscordUtils.sendPayload(anyString(), anyString())).thenReturn(true);

            DiscordService service = new DiscordService(feature);
            service.sendUnban(target, "Admin");

            mocked.verify(() -> DiscordUtils.sendPayload(
                    eq("https://discord.example/hook"),
                    argThat(payload -> payload.contains("Sanctie Melding")
                            && payload.contains("Unban")
                            && payload.contains("Remy")
                            && payload.contains("Admin"))));
        }

        verify(logger, never()).warn(contains("Failed to deliver webhook payload"));
    }

    @Test
    void sendKickLogsWarningWhenDeliveryFails() {
        Sanctions feature = configuredFeature("https://discord.example/hook");
        FeatureLogger logger = feature.getLogger();

        PlayerEntity target = new PlayerEntity();
        target.setUsername("Remy");

        try (MockedStatic<DiscordUtils> mocked = mockStatic(DiscordUtils.class)) {
            mocked.when(() -> DiscordUtils.sendPayload(anyString(), anyString())).thenReturn(false);

            DiscordService service = new DiscordService(feature);
            service.sendKick(target, "spam", "Admin");
        }

        verify(logger).warn(contains("Failed to deliver webhook payload"));
    }

    @Test
    void sendBanMuteAndUnmuteUsePlaceholderPayloadData() {
        Sanctions feature = configuredFeature("https://discord.example/hook");
        SanctionsService sanctionsService = mock(SanctionsService.class);
        when(feature.getService()).thenReturn(sanctionsService);
        when(sanctionsService.placeholdersFor(any(SanctionEntity.class)))
                .thenReturn(MessagePlaceholders.of(java.util.Map.of(
                        "target", "Remy",
                        "duration", "1d",
                        "reason", "Cheating",
                        "actor", "Admin"
                )));

        PlayerEntity target = new PlayerEntity();
        target.setUsername("Remy");
        SanctionEntity sanction = new SanctionEntity();

        try (MockedStatic<DiscordUtils> mocked = mockStatic(DiscordUtils.class)) {
            mocked.when(() -> DiscordUtils.sendPayload(anyString(), anyString())).thenReturn(true);

            DiscordService service = new DiscordService(feature);
            service.sendBan(sanction);
            service.sendMute(sanction);
            service.sendUnmute(target, "Admin");

            mocked.verify(() -> DiscordUtils.sendPayload(
                    eq("https://discord.example/hook"),
                    argThat(payload -> payload.contains("Ban")
                            && payload.contains("Mute")
                            && payload.contains("Unmute"))), atLeast(1));
        }
    }

    @Test
    void sendWarnAndKickHandleNullTargetAndBlankReasonWithDashFallback() {
        Sanctions feature = configuredFeature("https://discord.example/hook");

        try (MockedStatic<DiscordUtils> mocked = mockStatic(DiscordUtils.class)) {
            mocked.when(() -> DiscordUtils.sendPayload(anyString(), anyString())).thenReturn(true);

            DiscordService service = new DiscordService(feature);
            service.sendWarn(null, " ", "Admin");
            service.sendKick(null, null, "Admin");

            mocked.verify(() -> DiscordUtils.sendPayload(
                    eq("https://discord.example/hook"),
                    argThat(payload -> payload.contains("Warn")
                            && payload.contains("Kick")
                            && payload.contains("-"))), atLeast(1));
        }
    }

    private static Sanctions configuredFeature(String webhookUrl) {
        Sanctions feature = mock(Sanctions.class);
        FeatureConfigHandler cfg = mock(FeatureConfigHandler.class);
        FeatureLogger logger = mock(FeatureLogger.class);
        FeatureLifecycleManager lifecycle = mock(FeatureLifecycleManager.class);
        FeatureTaskManager taskManager = mock(FeatureTaskManager.class);
        when(feature.getConfigHandler()).thenReturn(cfg);
        when(feature.getLogger()).thenReturn(logger);
        when(feature.getLifecycleManager()).thenReturn(lifecycle);
        when(lifecycle.getTaskManager()).thenReturn(taskManager);
        when(feature.getFeatureVersion()).thenReturn("2.2.0");
        when(cfg.get("discordWebhookURL", String.class, "")).thenReturn(webhookUrl);
        when(taskManager.scheduleTask(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0, Runnable.class);
            runnable.run();
            return null;
        });
        return feature;
    }
}
