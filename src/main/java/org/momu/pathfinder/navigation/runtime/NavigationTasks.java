package org.momu.pathfinder.navigation.runtime;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of the scheduled work that belongs to each player so it can be cancelled when their navigation
 * ends: the repeating guidance task and a running beacon search. Safe to use from any thread, since on Folia
 * a navigation can be stopped from a region other than the player's.
 */
public final class NavigationTasks {
    private static final NavigationTasks INSTANCE = new NavigationTasks();

    private final Map<UUID, ScheduledTask> guidanceTasks = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask[]> beaconSearches = new ConcurrentHashMap<>();

    private NavigationTasks() {
    }

    public static NavigationTasks getInstance() {
        return INSTANCE;
    }

    /** Registers the player's guidance task, cancelling the previous one. */
    public void setGuidance(UUID playerId, ScheduledTask task) {
        cancel(guidanceTasks.put(playerId, task));
    }

    public void cancelGuidance(UUID playerId) {
        cancel(guidanceTasks.remove(playerId));
    }

    /** Registers a beacon search and its timeout, cancelling any earlier search. */
    public void setBeaconSearch(UUID playerId, ScheduledTask search, ScheduledTask timeout) {
        cancelAll(beaconSearches.put(playerId, new ScheduledTask[] { search, timeout }));
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

    private static void cancelAll(ScheduledTask[] tasks) {
        if (tasks != null) {
            for (ScheduledTask task : tasks) {
                cancel(task);
            }
        }
    }

    private static void cancel(ScheduledTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }
}
