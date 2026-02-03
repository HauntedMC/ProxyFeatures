package nl.hauntedmc.proxyfeatures.features.clientinfo.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerSettingsChangedEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import nl.hauntedmc.proxyfeatures.features.clientinfo.ClientInfo;
import nl.hauntedmc.proxyfeatures.features.clientinfo.internal.ClientInfoAdvisor;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerListener {

    private final ClientInfo feature;
    private final ClientInfoAdvisor advisor;

    private final ConcurrentHashMap<UUID, ScheduledTask> pendingTasks = new ConcurrentHashMap<>();

    public PlayerListener(ClientInfo feature, ClientInfoAdvisor advisor) {
        this.feature = feature;
        this.advisor = advisor;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        ScheduledTask task = pendingTasks.remove(uuid);
        if (task != null) {
            feature.getLifecycleManager().getTaskManager().cancelTask(task);
        }

        advisor.onDisconnect(uuid);
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();

        // Ensure DB-backed toggle is loaded once per session
        advisor.loadPlayerSettings(player);

        // Debounced check after server change
        scheduleSettingCheck(player.getUniqueId());
    }

    @Subscribe
    public void onSettingsChanged(PlayerSettingsChangedEvent event) {
        Player player = event.getPlayer();
        scheduleSettingCheck(player.getUniqueId());
    }

    private void scheduleSettingCheck(UUID uuid) {
        // Cancel previous pending
        ScheduledTask pending = pendingTasks.remove(uuid);
        if (pending != null) {
            feature.getLifecycleManager().getTaskManager().cancelTask(pending);
        }

        long delayMillis = advisor.config().notifyDebounceMillis();
        ScheduledTask task = feature.getLifecycleManager()
                .getTaskManager()
                .scheduleDelayedTask(() -> advisor.maybeNotify(uuid), Duration.ofMillis(delayMillis));

        pendingTasks.put(uuid, task);
    }
}
