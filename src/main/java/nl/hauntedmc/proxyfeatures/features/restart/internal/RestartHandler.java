package nl.hauntedmc.proxyfeatures.features.restart.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import nl.hauntedmc.commonlib.localization.MessageType;
import nl.hauntedmc.proxyfeatures.features.restart.Restart;
import nl.hauntedmc.proxyfeatures.lifecycle.FeatureTaskManager;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RestartHandler {

    private final Restart feature;
    private final ProxyServer proxy;
    private final FeatureTaskManager taskManager;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger remainingSeconds = new AtomicInteger(0);

    private boolean useChat;
    private boolean useTitles;
    private int finalDelaySeconds;
    private final Set<Integer> warnTimes = new HashSet<>();
    private Title.Times titleTimes;

    public RestartHandler(Restart feature) {
        this.feature = feature;
        this.proxy = feature.getPlugin().getProxy();
        this.taskManager = feature.getLifecycleManager().getTaskManager();
        reloadConfig();
    }

    private void reloadConfig() {
        Object useChatObj = feature.getConfigHandler().getSetting("broadcast_use_chat");
        Object useTitlesObj = feature.getConfigHandler().getSetting("broadcast_use_titles");
        this.useChat = !(useChatObj instanceof Boolean b) || b;
        this.useTitles = !(useTitlesObj instanceof Boolean b) || b;

        Object finalDelayObj = feature.getConfigHandler().getSetting("final_delay_seconds");
        this.finalDelaySeconds = asInt(finalDelayObj, 5);
        if (finalDelaySeconds < 0) finalDelaySeconds = 0;

        int fadeIn = asInt(feature.getConfigHandler().getSetting("title_fade_in_ms"), 500);
        int stay = asInt(feature.getConfigHandler().getSetting("title_stay_ms"), 2000);
        int fadeOut = asInt(feature.getConfigHandler().getSetting("title_fade_out_ms"), 500);
        if (fadeIn < 0) fadeIn = 0;
        if (stay < 0) stay = 0;
        if (fadeOut < 0) fadeOut = 0;
        this.titleTimes = Title.Times.times(
                Duration.ofMillis(fadeIn),
                Duration.ofMillis(stay),
                Duration.ofMillis(fadeOut)
        );

        warnTimes.clear();
        Object warnTimesObj = feature.getConfigHandler().getSetting("warn_times");
        if (warnTimesObj instanceof List<?>) {
            for (Object o : (List<?>) warnTimesObj) {
                int v = asInt(o, -1);
                if (v >= 0) warnTimes.add(v);
            }
        } else {
            warnTimes.addAll(List.of(60, 30, 15, 10, 5, 4, 3, 2, 1));
        }
    }

    public synchronized boolean startCountdown() {
        if (!running.compareAndSet(false, true)) return false;
        reloadConfig();
        int start = asInt(feature.getConfigHandler().getSetting("countdown_seconds"), 60);
        if (start < 0) start = 0;
        remainingSeconds.set(start);

        taskManager.scheduleRepeatingTask(() -> {
            if (!running.get()) return;

            int secs = remainingSeconds.get();

            if (secs > 0 && warnTimes.contains(secs)) {
                broadcastWarning(secs);
            }

            if (secs == 0) {
                broadcastFinal();
                running.set(false);
                if (finalDelaySeconds <= 0) {
                    performRestart();
                } else {
                    taskManager.scheduleDelayedTask(this::performRestart, Duration.ofSeconds(finalDelaySeconds));
                }
                return;
            }

            remainingSeconds.decrementAndGet();
        }, Duration.ofSeconds(1));

        return true;
    }

    public void forceRestart() {
        running.set(false);
        performRestart();
    }

    private void broadcastWarning(int seconds) {
        for (Player p : proxy.getAllPlayers()) {
            if (useChat) {
                Component chat = feature.getLocalizationHandler()
                        .getMessage("restart.warn_chat")
                        .withPlaceholders(java.util.Map.of("seconds", Integer.toString(seconds)))
                        .forAudience(p)
                        .build();
                p.sendMessage(chat);
            }
            if (useTitles) {
                Component title = feature.getLocalizationHandler()
                        .getMessage("restart.warn_title")
                        .forAudience(p)
                        .ofType(MessageType.MiniMessage)
                        .build();
                Component subtitle = feature.getLocalizationHandler()
                        .getMessage("restart.warn_subtitle")
                        .withPlaceholders(java.util.Map.of("seconds", Integer.toString(seconds)))
                        .forAudience(p)
                        .ofType(MessageType.MiniMessage)
                        .build();
                p.showTitle(Title.title(title, subtitle, titleTimes));
            }
        }
    }

    private void broadcastFinal() {
        for (Player p : proxy.getAllPlayers()) {
            if (useChat) {
                Component chat = feature.getLocalizationHandler()
                        .getMessage("restart.final_chat")
                        .forAudience(p)
                        .build();
                p.sendMessage(chat);
            }
            if (useTitles) {
                Component title = feature.getLocalizationHandler()
                        .getMessage("restart.final_title")
                        .forAudience(p)
                        .ofType(MessageType.MiniMessage)
                        .build();
                Component subtitle = feature.getLocalizationHandler()
                        .getMessage("restart.final_subtitle")
                        .forAudience(p)
                        .ofType(MessageType.MiniMessage)
                        .build();
                p.showTitle(Title.title(title, subtitle, titleTimes));
            }
        }
    }

    private void performRestart() {
        try {
            for (Player p : proxy.getAllPlayers()) {
                Component reason = feature.getLocalizationHandler()
                        .getMessage("restart.kick")
                        .forAudience(p)
                        .ofType(MessageType.MiniMessage)
                        .build();
                p.disconnect(reason);
            }
            feature.getPlugin().getLogger().info("Initiating Velocity shutdown.");
            proxy.shutdown();
        } catch (Throwable t) {
            feature.getPlugin().getLogger().error("Failed to shutdown proxy, forcing exit.", t);
            System.exit(0);
        }
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return def;
    }
}
