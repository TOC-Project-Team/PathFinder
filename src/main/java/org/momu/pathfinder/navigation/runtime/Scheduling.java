package org.momu.pathfinder.navigation.runtime;

import org.bukkit.Bukkit;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.momu.pathfinder.bootstrap.PathFinderPlugin;

/**
 * Scheduler wrappers that quietly do nothing while PathFinder is disabled or shutting down, so callbacks that
 * arrive late never throw.
 */
public final class Scheduling {
    private Scheduling() {
    }

    public static boolean isPluginEnabled() {
        PathFinderPlugin plugin = PathFinderPlugin.getInstance();
        return plugin != null && plugin.isEnabled();
    }

    /** Runs on the main thread on the next tick. */
    public static BukkitTask runSync(Runnable task) {
        if (!isPluginEnabled()) {
            return null;
        }
        try {
            return Bukkit.getScheduler().runTask(PathFinderPlugin.getInstance(), task);
        } catch (IllegalPluginAccessException ignored) {
            return null;
        }
    }

    public static BukkitTask runLater(Runnable task, long delayTicks) {
        if (!isPluginEnabled()) {
            return null;
        }
        try {
            return Bukkit.getScheduler().runTaskLater(PathFinderPlugin.getInstance(), task, delayTicks);
        } catch (IllegalPluginAccessException ignored) {
            return null;
        }
    }

    public static BukkitTask runAsync(Runnable task) {
        return runAsync(asRunnable(task));
    }

    /** Async variant for tasks that check {@link BukkitRunnable#isCancelled()} while they run. */
    public static BukkitTask runAsync(BukkitRunnable task) {
        if (!isPluginEnabled()) {
            return null;
        }
        try {
            return task.runTaskAsynchronously(PathFinderPlugin.getInstance());
        } catch (IllegalPluginAccessException ignored) {
            return null;
        }
    }

    public static BukkitTask runLaterAsync(Runnable task, long delayTicks) {
        if (!isPluginEnabled()) {
            return null;
        }
        try {
            return Bukkit.getScheduler().runTaskLaterAsynchronously(PathFinderPlugin.getInstance(), task, delayTicks);
        } catch (IllegalPluginAccessException ignored) {
            return null;
        }
    }

    public static BukkitTask runTimer(BukkitRunnable task, long delayTicks, long periodTicks) {
        if (!isPluginEnabled()) {
            return null;
        }
        try {
            return task.runTaskTimer(PathFinderPlugin.getInstance(), delayTicks, periodTicks);
        } catch (IllegalPluginAccessException ignored) {
            return null;
        }
    }

    private static BukkitRunnable asRunnable(Runnable task) {
        return new BukkitRunnable() {
            @Override
            public void run() {
                task.run();
            }
        };
    }
}
