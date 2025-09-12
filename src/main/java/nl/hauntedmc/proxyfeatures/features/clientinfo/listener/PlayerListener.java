package nl.hauntedmc.proxyfeatures.features.clientinfo.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerSettingsChangedEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.PlayerSettings;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.proxyfeatures.features.clientinfo.ClientInfo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerListener {

    private final ClientInfo feature;
    private final ConcurrentHashMap<UUID, ScheduledTask> pendingTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ScheduledTask> switchTasks = new ConcurrentHashMap<>();

    public PlayerListener(ClientInfo feature) {
        this.feature = feature;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        ScheduledTask task = pendingTasks.remove(player.getUniqueId());
        if (task != null) {
            feature.getLifecycleManager().getTaskManager().cancelTask(task);
        }
    }

    /**
     * On gamemode switch, wait 10 seconds then run recommendations.
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        scheduleSettingCheck(player, 5000);
    }

    /**
     * Debounce settings changes: wait 5 seconds after last change before checking.
     */
    @Subscribe
    public void onSettingsChanged(PlayerSettingsChangedEvent event) {
        Player player = event.getPlayer();
        scheduleSettingCheck(player, 5000);
    }

    private void scheduleSettingCheck(Player player, int delay) {
        UUID uuid = player.getUniqueId();
        // Cancel any previous switch task
        ScheduledTask pending = pendingTasks.remove(uuid);
        if (pending != null) {
            feature.getLifecycleManager().getTaskManager().cancelTask(pending);
        }
        // Schedule recommendation after 10 seconds (10000 ms)
        ScheduledTask task = feature.getLifecycleManager()
                .getTaskManager()
                .scheduleDelayedTask(() -> runRecommendationCheck(player), Duration.ofMillis(delay));
        pendingTasks.put(uuid, task);
    }

    /**
     * Performs the same recommendation logic after debounce delay.
     */
    private void runRecommendationCheck(Player player) {
        UUID uuid = player.getUniqueId();
        pendingTasks.remove(uuid);

        PlayerSettings settings = player.getPlayerSettings();
        int idealViewDistance = 5;
        PlayerSettings.ChatMode idealChat = PlayerSettings.ChatMode.SHOWN;
        PlayerSettings.ParticleStatus idealParticles = PlayerSettings.ParticleStatus.ALL;

        boolean foundRecommendation = false;
        List<Component> recommendationMessage = new ArrayList<>();
        recommendationMessage.add(feature.getLocalizationHandler()
                .getMessage("clientinfo.header").forAudience(player).build());

        // Check view distance
        if (settings.getViewDistance() < idealViewDistance) {
            foundRecommendation = true;
            recommendationMessage.add(feature.getLocalizationHandler()
                    .getMessage("clientinfo.recommendation")
                    .withPlaceholders(Map.of(
                            "setting_name", "Render Distance",
                            "setting_found", String.valueOf(settings.getViewDistance()),
                            "setting_recommended", String.valueOf(idealViewDistance)))
                    .forAudience(player)
                    .build());
        }

        // Check chat mode
        if (settings.getChatMode() != idealChat) {
            foundRecommendation = true;
            recommendationMessage.add(feature.getLocalizationHandler()
                    .getMessage("clientinfo.recommendation")
                    .withPlaceholders(Map.of(
                            "setting_name", "Chat Mode",
                            "setting_found", settings.getChatMode().name(),
                            "setting_recommended", idealChat.name()))
                    .forAudience(player)
                    .build());
        }

        // Check particle setting
        if (settings.getParticleStatus() != idealParticles) {
            foundRecommendation = true;
            recommendationMessage.add(feature.getLocalizationHandler()
                    .getMessage("clientinfo.recommendation")
                    .withPlaceholders(Map.of(
                            "setting_name", "Particles",
                            "setting_found", settings.getParticleStatus().name(),
                            "setting_recommended", idealParticles.name()))
                    .forAudience(player)
                    .build());
        }

        if (foundRecommendation) {
            recommendationMessage.forEach(player::sendMessage);
        }
    }
}