package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import com.velocitypowered.api.scheduler.ScheduledTask;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Centralized scheduler for feature-scoped tasks (Velocity).
 * Goals:
 * - Track every scheduled task so we can cancel all on feature shutdown.
 * - For one-shot tasks, automatically remove the finished task from tracking.
 * - Safe to modify from any thread (CopyOnWriteArrayList).
 * Time units:
 * - Uses {@link Duration} for delays and periods.
 */
public class FeatureTaskManager {

    private final ProxyFeatures plugin;
    private final List<ScheduledTask> scheduledTasks = new CopyOnWriteArrayList<>();

    public FeatureTaskManager(ProxyFeatures plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /* ----------------------------------------------------------------------
     * Public API — thin wrappers over generic helpers
     * ---------------------------------------------------------------------- */

    /**
     * Schedules a one-time task to run immediately.
     */
    public ScheduledTask scheduleTask(Runnable task) {
        Objects.requireNonNull(task, "task");
        return scheduleOnce(r -> plugin.getScheduler()
                .buildTask(plugin, r)
                .schedule(), task);
    }

    /**
     * Runs a one-time task with a delay.
     */
    public ScheduledTask scheduleDelayedTask(Runnable task, Duration delay) {
        Objects.requireNonNull(task, "task");
        Duration d = clampDelay(delay);
        return scheduleOnce(r -> plugin.getScheduler()
                .buildTask(plugin, r)
                .delay(d)
                .schedule(), task);
    }

    /**
     * Runs a repeating task with no initial delay (first run ASAP).
     */
    public ScheduledTask scheduleRepeatingTask(Runnable task, Duration period) {
        Objects.requireNonNull(task, "task");
        Duration p = clampPeriod(period);
        return scheduleRepeating(r -> plugin.getScheduler()
                .buildTask(plugin, r)
                .delay(Duration.ZERO) // start immediately
                .repeat(p)
                .schedule(), task);
    }

    /**
     * Runs a repeating task with an initial delay.
     */
    public ScheduledTask scheduleRepeatingTask(Runnable task, Duration delay, Duration period) {
        Objects.requireNonNull(task, "task");
        Duration d = clampDelay(delay);
        Duration p = clampPeriod(period);
        return scheduleRepeating(r -> plugin.getScheduler()
                .buildTask(plugin, r)
                .delay(d)
                .repeat(p)
                .schedule(), task);
    }

    /* ----------------------------------------------------------------------
     * Management
     * ---------------------------------------------------------------------- */

    /**
     * Cancels a specific task and removes it from tracking.
     */
    public void cancelTask(ScheduledTask task) {
        if (task != null) {
            task.cancel();
            scheduledTasks.remove(task);
        }
    }

    /**
     * Cancels all scheduled tasks.
     */
    public void cancelAllTasks() {
        for (ScheduledTask task : scheduledTasks) {
            task.cancel();
        }
        scheduledTasks.clear();
    }

    /**
     * Returns the number of active tasks.
     */
    public int getActiveTaskCount() {
        return scheduledTasks.size();
    }

    /* ----------------------------------------------------------------------
     * Internals — generic helpers to maximize reuse
     * ---------------------------------------------------------------------- */

    /**
     * One-shot scheduling wrapper that:
     * - wraps the runnable to auto-remove itself on completion
     * - tracks the ScheduledTask handle
     */
    private ScheduledTask scheduleOnce(Function<Runnable, ScheduledTask> submitter, Runnable task) {
        AtomicReference<ScheduledTask> ref = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean(false);
        Runnable wrapped = () -> {
            try {
                task.run();
            } finally {
                completed.set(true);
                ScheduledTask scheduled = ref.get();
                if (scheduled != null) {
                    scheduledTasks.remove(scheduled);
                }
            }
        };
        ScheduledTask scheduled = submitter.apply(wrapped);
        ref.set(scheduled);
        scheduledTasks.add(scheduled);
        if (completed.get()) {
            scheduledTasks.remove(scheduled);
        }
        return scheduled;
    }

    /**
     * Repeating scheduling wrapper that:
     * - does NOT auto-remove (call cancelTask / cancelAllTasks to remove)
     * - tracks the ScheduledTask handle
     */
    private ScheduledTask scheduleRepeating(Function<Runnable, ScheduledTask> submitter, Runnable task) {
        ScheduledTask scheduled = submitter.apply(task);
        scheduledTasks.add(scheduled);
        return scheduled;
    }

    /**
     * Clamp delay to >= 0 (negative becomes ZERO).
     */
    private static Duration clampDelay(Duration d) {
        if (d == null || d.isNegative()) return Duration.ZERO;
        return d;
    }

    /**
     * Clamp period to at least 1 second to avoid accidental hot-loop schedules.
     */
    private static Duration clampPeriod(Duration p) {
        if (p == null || p.isZero() || p.isNegative()) return Duration.ofMillis(1000);
        return p;
    }

}
