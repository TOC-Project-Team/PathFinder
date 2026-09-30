package org.momu.pathfinder.navigation.runtime;

import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps track of the scheduled work that belongs to each player so it can be cancelled when their navigation
 * ends: the repeating guidance task and a running beacon search.
 */
public final class NavigationTasks {
    private static final NavigationTasks INSTANCE = new NavigationTasks();

    private final Map<UUID, BukkitTask> guidanceTasks = new HashMap<>();
    private final Map<UUID, BukkitTask[]> beaconSearches = new HashMap<>();

    private NavigationTasks() {
    }

    public static NavigationTasks getInstance() {
        return INSTANCE;
    }

    /** Registers the player's guidance task, cancelling the previous one. */
    public void setGuidance(UUID playerId, BukkitTask task) {
        cancel(guidanceTasks.put(playerId, task));
    }

    public void cancelGuidance(UUID playerId) {
        cancel(guidanceTasks.remove(playerId));
    }

    /** Registers a beacon search and its timeout, cancelling any earlier search. */
    public void setBeaconSearch(UUID playerId, BukkitTask search, BukkitTask timeout) {
        cancelAll(beaconSearches.put(playerId, new BukkitTask[] { search, timeout }));
    }

    public void cancelBeaconSearch(UUID playerId) {
        cancelAll(beaconSearches.remove(playerId));
    }

    public void cancelEverything() {
        guidanceTasks.values().forEach(NavigationTasks::cancel);
        guidanceTasks.clear();
        beaconSearches.values().forEach(NavigationTasks::cancelAll);
        beaconSearches.clear();
    }

    private static void cancelAll(BukkitTask[] tasks) {
        if (tasks != null) {
            for (BukkitTask task : tasks) {
                cancel(task);
            }
        }
    }

    private static void cancel(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
