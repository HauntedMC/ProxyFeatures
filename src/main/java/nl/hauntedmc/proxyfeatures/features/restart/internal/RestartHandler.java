package nl.hauntedmc.proxyfeatures.features.restart.internal;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import nl.hauntedmc.proxyfeatures.features.restart.Restart;
import nl.hauntedmc.proxyfeatures.framework.lifecycle.FeatureTaskManager;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class RestartHandler {

    public enum ScheduleResult {
        SUCCESS,
        ALREADY_SCHEDULED,
        COUNTDOWN_RUNNING,
        NOT_IN_FUTURE
    }

    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd-MM-uuuu HH:mm");

    private final Restart feature;
    private final ProxyServer proxy;
    private final FeatureTaskManager taskManager;

    private final AtomicBoolean countdownRunning = new AtomicBoolean(false);
    private final AtomicInteger remainingSeconds = new AtomicInteger(0);

    private final AtomicBoolean scheduleActive = new AtomicBoolean(false);
    private volatile ZonedDateTime scheduledAt;
    private volatile ZonedDateTime lastAnnouncedHourStart;

    private boolean useChat;
    private boolean useTitles;
    private int finalDelaySeconds;
    private final Set<Integer> warnTimes = new HashSet<>();
    private Title.Times titleTimes;

    private ZoneId scheduleZone;
    private Duration scheduleCheckInterval;
    private int scheduleAnnounceHoursBefore;

    public RestartHandler(Restart feature) {
        this.feature = feature;
        this.proxy = ProxyFeatures.getProxyInstance();
        this.taskManager = feature.getLifecycleManager().getTaskManager();
        reloadConfig();
    }

    public void shutdown() {
        countdownRunning.set(false);
        scheduleActive.set(false);
        scheduledAt = null;
        lastAnnouncedHourStart = null;
    }

    public synchronized boolean startCountdown() {
        if (!countdownRunning.compareAndSet(false, true)) return false;

        reloadConfig();

        int start = asInt(feature.getConfigHandler().get("countdown_seconds"), 60);
        if (start < 0) start = 0;
        remainingSeconds.set(start);

        countdownTick();
        return true;
    }

    public void forceRestart() {
        countdownRunning.set(false);
        scheduleActive.set(false);
        scheduledAt = null;
        lastAnnouncedHourStart = null;
        performRestart();
    }

    public synchronized ScheduleResult scheduleRestart(ZonedDateTime target) {
        reloadConfig();

        if (countdownRunning.get()) return ScheduleResult.COUNTDOWN_RUNNING;
        if (scheduledAt != null) return ScheduleResult.ALREADY_SCHEDULED;

        ZonedDateTime normalized = target.withZoneSameInstant(scheduleZone);
        ZonedDateTime now = ZonedDateTime.now(scheduleZone);
        if (!normalized.isAfter(now)) return ScheduleResult.NOT_IN_FUTURE;

        scheduledAt = normalized;
        lastAnnouncedHourStart = null;

        if (scheduleActive.compareAndSet(false, true)) {
            scheduleMonitorTick();
        }

        return ScheduleResult.SUCCESS;
    }

    public synchronized boolean cancelScheduledRestart() {
        if (scheduledAt == null) return false;
        scheduledAt = null;
        lastAnnouncedHourStart = null;
        scheduleActive.set(false);
        return true;
    }

    public ZonedDateTime getScheduledAt() {
        return scheduledAt;
    }

    public String formatDateTime(ZonedDateTime dateTime) {
        if (dateTime == null) return "";
        return DISPLAY_FORMAT.format(dateTime);
    }

    private void reloadConfig() {
        Object useChatObj = feature.getConfigHandler().get("broadcast_use_chat");
        Object useTitlesObj = feature.getConfigHandler().get("broadcast_use_titles");
        this.useChat = !(useChatObj instanceof Boolean b) || b;
        this.useTitles = !(useTitlesObj instanceof Boolean b) || b;

        Object finalDelayObj = feature.getConfigHandler().get("final_delay_seconds");
        this.finalDelaySeconds = asInt(finalDelayObj, 5);
        if (finalDelaySeconds < 0) finalDelaySeconds = 0;

        int fadeIn = asInt(feature.getConfigHandler().get("title_fade_in_ms"), 500);
        int stay = asInt(feature.getConfigHandler().get("title_stay_ms"), 2000);
        int fadeOut = asInt(feature.getConfigHandler().get("title_fade_out_ms"), 500);
        if (fadeIn < 0) fadeIn = 0;
        if (stay < 0) stay = 0;
        if (fadeOut < 0) fadeOut = 0;
        this.titleTimes = Title.Times.times(
                Duration.ofMillis(fadeIn),
                Duration.ofMillis(stay),
                Duration.ofMillis(fadeOut)
        );

        warnTimes.clear();
        Object warnTimesObj = feature.getConfigHandler().get("warn_times");
        if (warnTimesObj instanceof List<?>) {
            for (Object o : (List<?>) warnTimesObj) {
                int v = asInt(o, -1);
                if (v >= 0) warnTimes.add(v);
            }
        } else {
            warnTimes.addAll(List.of(60, 30, 15, 10, 5, 4, 3, 2, 1));
        }

        this.scheduleZone = readZone(feature.getConfigHandler().get("schedule_time_zone"));
        int intervalSeconds = asInt(feature.getConfigHandler().get("schedule_check_interval_seconds"), 5);
        if (intervalSeconds < 1) intervalSeconds = 1;
        this.scheduleCheckInterval = Duration.ofSeconds(intervalSeconds);

        this.scheduleAnnounceHoursBefore = asInt(feature.getConfigHandler().get("schedule_announce_hours_before"), 5);
        if (scheduleAnnounceHoursBefore < 0) scheduleAnnounceHoursBefore = 0;
    }

    private ZoneId readZone(Object zoneObj) {
        if (zoneObj instanceof String s) {
            String v = s.trim();
            if (v.isEmpty() || "system".equalsIgnoreCase(v)) return ZoneId.systemDefault();
            try {
                return ZoneId.of(v);
            } catch (Exception ignored) {
                feature.getPlugin().getLogger().warn("Invalid schedule_time_zone value '{}', using system default.", v);
                return ZoneId.systemDefault();
            }
        }
        return ZoneId.systemDefault();
    }

    private void countdownTick() {
        if (!countdownRunning.get()) return;

        int secs = remainingSeconds.get();

        if (secs > 0 && warnTimes.contains(secs)) {
            broadcastWarning(secs);
        }

        if (secs == 0) {
            broadcastFinal();
            countdownRunning.set(false);

            if (finalDelaySeconds <= 0) {
                performRestart();
            } else {
                taskManager.scheduleDelayedTask(this::performRestart, Duration.ofSeconds(finalDelaySeconds));
            }
            return;
        }

        remainingSeconds.decrementAndGet();
        taskManager.scheduleDelayedTask(this::countdownTick, Duration.ofSeconds(1));
    }

    private void scheduleMonitorTick() {
        if (!scheduleActive.get()) return;

        ZonedDateTime target = scheduledAt;
        if (target == null) {
            scheduleActive.set(false);
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(scheduleZone);

        tryHourlyAnnouncement(now, target);

        if (!now.isBefore(target)) {
            scheduledAt = null;
            lastAnnouncedHourStart = null;
            scheduleActive.set(false);

            boolean started = startCountdown();
            if (!started) {
                feature.getPlugin().getLogger().warn("Scheduled restart reached time but countdown was already running.");
            }
            return;
        }

        taskManager.scheduleDelayedTask(this::scheduleMonitorTick, scheduleCheckInterval);
    }

    private void tryHourlyAnnouncement(ZonedDateTime now, ZonedDateTime target) {
        if (scheduleAnnounceHoursBefore <= 0) return;

        ZonedDateTime windowStart = target.minusHours(scheduleAnnounceHoursBefore);
        ZonedDateTime hourStart = now.withMinute(0).withSecond(0).withNano(0);

        ZonedDateTime last = lastAnnouncedHourStart;
        if (last != null && !hourStart.isAfter(last)) return;

        lastAnnouncedHourStart = hourStart;

        if (hourStart.isBefore(windowStart)) return;
        if (!hourStart.isBefore(target)) return;

        broadcastScheduleAnnouncement(target);
    }

    private void broadcastScheduleAnnouncement(ZonedDateTime target) {
        String datetime = formatDateTime(target);
        for (Player p : proxy.getAllPlayers()) {
            Component chat = feature.getLocalizationHandler()
                    .getMessage("restart.schedule.announce_chat")
                    .with("datetime", datetime)
                    .forAudience(p)
                    .build();
            p.sendMessage(chat);
        }
    }

    private void broadcastWarning(int seconds) {
        for (Player p : proxy.getAllPlayers()) {
            if (useChat) {
                Component chat = feature.getLocalizationHandler()
                        .getMessage("restart.warn_chat")
                        .with("seconds", seconds)
                        .forAudience(p)
                        .build();
                p.sendMessage(chat);
            }
            if (useTitles) {
                Component title = feature.getLocalizationHandler()
                        .getMessage("restart.warn_title")
                        .forAudience(p)
                        .build();
                Component subtitle = feature.getLocalizationHandler()
                        .getMessage("restart.warn_subtitle")
                        .with("seconds", seconds)
                        .forAudience(p)
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
                        .build();
                Component subtitle = feature.getLocalizationHandler()
                        .getMessage("restart.final_subtitle")
                        .forAudience(p)
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
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }
}
