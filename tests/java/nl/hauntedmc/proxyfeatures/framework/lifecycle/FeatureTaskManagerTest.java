package nl.hauntedmc.proxyfeatures.framework.lifecycle;

import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import com.velocitypowered.api.scheduler.TaskStatus;
import nl.hauntedmc.proxyfeatures.ProxyFeatures;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureTaskManagerTest {

    @Test
    void oneShotTaskAutoRemovesAfterExecution() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        FakeScheduler scheduler = new FakeScheduler();
        when(plugin.getScheduler()).thenReturn(scheduler);

        FeatureTaskManager manager = new FeatureTaskManager(plugin);
        manager.scheduleTask(() -> {
        });
        assertEquals(1, manager.getActiveTaskCount());

        scheduler.runLastRunnable();
        assertEquals(0, manager.getActiveTaskCount());
    }

    @Test
    void oneShotTaskAutoRemovesEvenWhenExecutedBeforeScheduleReturns() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        FakeScheduler scheduler = new FakeScheduler();
        scheduler.runImmediatelyOnSchedule = true;
        when(plugin.getScheduler()).thenReturn(scheduler);

        FeatureTaskManager manager = new FeatureTaskManager(plugin);
        manager.scheduleTask(() -> {
        });

        assertEquals(0, manager.getActiveTaskCount());
    }

    @Test
    void delayedTaskClampsNegativeAndNullToZero() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        FakeScheduler scheduler = new FakeScheduler();
        when(plugin.getScheduler()).thenReturn(scheduler);
        FeatureTaskManager manager = new FeatureTaskManager(plugin);

        manager.scheduleDelayedTask(() -> {
        }, Duration.ofSeconds(-5));
        assertEquals(Duration.ZERO, scheduler.lastDelay);

        manager.scheduleDelayedTask(() -> {
        }, null);
        assertEquals(Duration.ZERO, scheduler.lastDelay);

        Duration positive = Duration.ofSeconds(2);
        manager.scheduleDelayedTask(() -> {
        }, positive);
        assertEquals(positive, scheduler.lastDelay);
    }

    @Test
    void repeatingTasksClampInvalidPeriodAndTrackUntilCancelled() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        FakeScheduler scheduler = new FakeScheduler();
        when(plugin.getScheduler()).thenReturn(scheduler);
        FeatureTaskManager manager = new FeatureTaskManager(plugin);

        ScheduledTask first = manager.scheduleRepeatingTask(() -> {
        }, Duration.ZERO);
        assertEquals(Duration.ofMillis(1000), scheduler.lastRepeat);
        assertEquals(1, manager.getActiveTaskCount());

        manager.cancelTask(first);
        assertEquals(0, manager.getActiveTaskCount());
        assertTrue(((FakeTask) first).cancelled.get());
    }

    @Test
    void repeatingTaskWithDelayUsesDelayAndPeriodClamps() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        FakeScheduler scheduler = new FakeScheduler();
        when(plugin.getScheduler()).thenReturn(scheduler);
        FeatureTaskManager manager = new FeatureTaskManager(plugin);

        manager.scheduleRepeatingTask(() -> {
        }, Duration.ofSeconds(-1), Duration.ofSeconds(-1));
        assertEquals(Duration.ZERO, scheduler.lastDelay);
        assertEquals(Duration.ofMillis(1000), scheduler.lastRepeat);
    }

    @Test
    void cancelAllCancelsTrackedTasksAndNullCancelIsIgnored() {
        ProxyFeatures plugin = mock(ProxyFeatures.class);
        FakeScheduler scheduler = new FakeScheduler();
        when(plugin.getScheduler()).thenReturn(scheduler);
        FeatureTaskManager manager = new FeatureTaskManager(plugin);

        manager.scheduleTask(() -> {
        });
        manager.scheduleRepeatingTask(() -> {
        }, Duration.ofSeconds(1));

        assertEquals(2, manager.getActiveTaskCount());
        manager.cancelTask(null);
        manager.cancelAllTasks();
        assertEquals(0, manager.getActiveTaskCount());
        assertTrue(scheduler.tasks.stream().allMatch(task -> task.cancelled.get()));
    }

    private static final class FakeScheduler implements Scheduler {
        private final List<FakeTask> tasks = new ArrayList<>();
        private Runnable lastRunnable;
        private Duration lastDelay;
        private Duration lastRepeat;
        private boolean runImmediatelyOnSchedule;

        @Override
        public TaskBuilder buildTask(Object plugin, Runnable runnable) {
            this.lastRunnable = runnable;
            return new TaskBuilderImpl(this);
        }

        @Override
        public TaskBuilder buildTask(Object plugin, Consumer<ScheduledTask> consumer) {
            return new TaskBuilderImpl(this);
        }

        @Override
        public Collection<ScheduledTask> tasksByPlugin(Object plugin) {
            return List.copyOf(tasks);
        }

        void runLastRunnable() {
            if (lastRunnable != null) {
                lastRunnable.run();
            }
        }
    }

    private static final class TaskBuilderImpl implements Scheduler.TaskBuilder {
        private final FakeScheduler owner;

        private TaskBuilderImpl(FakeScheduler owner) {
            this.owner = owner;
        }

        @Override
        public Scheduler.TaskBuilder delay(long time, TimeUnit unit) {
            owner.lastDelay = Duration.ofMillis(unit.toMillis(time));
            return this;
        }

        @Override
        public Scheduler.TaskBuilder repeat(long time, TimeUnit unit) {
            owner.lastRepeat = Duration.ofMillis(unit.toMillis(time));
            return this;
        }

        @Override
        public Scheduler.TaskBuilder clearDelay() {
            owner.lastDelay = null;
            return this;
        }

        @Override
        public Scheduler.TaskBuilder clearRepeat() {
            owner.lastRepeat = null;
            return this;
        }

        @Override
        public ScheduledTask schedule() {
            FakeTask task = new FakeTask();
            owner.tasks.add(task);
            if (owner.runImmediatelyOnSchedule && owner.lastRunnable != null) {
                owner.lastRunnable.run();
            }
            return task;
        }
    }

    private static final class FakeTask implements ScheduledTask {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public Object plugin() {
            return null;
        }

        @Override
        public TaskStatus status() {
            return TaskStatus.SCHEDULED;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }
    }
}
