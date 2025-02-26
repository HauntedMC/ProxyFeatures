package nl.hauntedmc.proxyfeatures.lifecycle;

import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import com.velocitypowered.api.scheduler.ScheduledTask;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class FeatureTaskManager {

    private final ProxyFeatures plugin;
    private final List<ScheduledTask> scheduledTasks = new CopyOnWriteArrayList<>();

    public FeatureTaskManager(ProxyFeatures plugin) {
        this.plugin = plugin;
    }

    /**
     * Schedules a one-time task to run immediately.
     */
    public ScheduledTask scheduleTask(Runnable task) {
        // Delegate to scheduleDelayedTask with zero delay.
        return scheduleDelayedTask(task, 0);
    }

    /**
     * Runs a one-time task with a delay.
     *
     * @param delay Delay in ticks.
     */
    public ScheduledTask scheduleDelayedTask(Runnable task, long delay) {
        AtomicReference<ScheduledTask> taskRef = new AtomicReference<>();
        Runnable wrappedTask = () -> {
            try {
                task.run();
            } finally {
                scheduledTasks.remove(taskRef.get());
            }
        };

        ScheduledTask scheduledTask = plugin.getScheduler()
                .buildTask(plugin, wrappedTask)
                .delay(delay, TimeUnit.MILLISECONDS)
                .schedule();
        taskRef.set(scheduledTask);
        scheduledTasks.add(scheduledTask);
        return scheduledTask;
    }

    /**
     * Runs a repeating task with no initial delay.
     *
     * @param period The period between executions in ticks.
     */
    public ScheduledTask scheduleRepeatingTask(Runnable task, long period) {
        return scheduleDelayedRepeatingTask(task, 0, period);
    }

    /**
     * Runs a repeating task with an initial delay.
     *
     * @param delay  The initial delay in ticks.
     * @param period The period between executions in ticks.
     */
    public ScheduledTask scheduleDelayedRepeatingTask(Runnable task, long delay, long period) {
        ScheduledTask scheduledTask = plugin.getScheduler()
                .buildTask(plugin, task)
                .delay(delay, TimeUnit.MILLISECONDS)
                .repeat(period, TimeUnit.MILLISECONDS)
                .schedule();
        scheduledTasks.add(scheduledTask);
        return scheduledTask;
    }

    /**
     * Cancels a specific task.
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
}
