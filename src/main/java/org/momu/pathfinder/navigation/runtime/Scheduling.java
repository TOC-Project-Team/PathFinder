package org.momu.pathfinder.navigation.runtime;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.momu.pathfinder.bootstrap.PathFinderPlugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Scheduler wrappers built on Paper's region-aware schedulers, so the same code runs on Paper and Folia:
 * <ul>
 *     <li>work on a player (messages, menus, reading blocks around them) runs on that player's
 *     {@link Entity#getScheduler() entity scheduler}, which is the main thread on Paper and the thread of the
 *     region that owns the player on Folia,</li>
 *     <li>work at a location runs on the region that owns it,</li>
 *     <li>server-wide work runs on the global region,</li>
 *     <li>work that does not touch the world runs on the async scheduler.</li>
 * </ul>
 * Every wrapper quietly does nothing (and returns {@code null}) while PathFinder is disabled or shutting down,
 * so callbacks that arrive late never throw.
 */
public final class Scheduling {
    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");
    private static final long MILLIS_PER_TICK = 50L;

    private Scheduling() {
    }

    /**
     * Whether the server is Folia. Folia ticks regions on several threads and refuses to read the world from
     * any other thread, so the few places that read blocks off the main thread on Paper do it differently.
     */
    public static boolean isFolia() {
        return FOLIA;
    }

    public static boolean isPluginEnabled() {
        PathFinderPlugin plugin = PathFinderPlugin.getInstance();
        return plugin != null && plugin.isEnabled();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Entities
    // ---------------------------------------------------------------------------------------------------------

    /** Runs on the thread that owns the entity on the next tick. Dropped if the entity is removed first. */
    public static ScheduledTask runFor(Entity entity, Runnable task) {
        if (!isPluginEnabled() || entity == null) {
            return null;
        }
        try {
            return entity.getScheduler().run(PathFinderPlugin.getInstance(), ignored -> task.run(), null);
        } catch (IllegalPluginAccessException ignored) {
            return null;
        }
    }

    public static ScheduledTask runForLater(Entity entity, Runnable task, long delayTicks) {
        if (!isPluginEnabled() || entity == null) {
            return null;
        }
        try {
            return entity.getScheduler().runDelayed(PathFinderPlugin.getInstance(), ignored -> task.run(), null,
                    Math.max(1L, delayTicks));
        } catch (IllegalPluginAccessException ignored) {
            return null;
        }
    }

    /**
     * Repeats on the thread that owns the entity, following it between regions. Stops by itself when the entity
     * is removed (e.g. the player leaves).
     */
    public static ScheduledTask runForTimer(Entity entity, Consumer<ScheduledTask> task, long delayTicks,
                                            long periodTicks) {
        if (!isPluginEnabled() || entity == null) {
            return null;
        }
        try {
            return entity.getScheduler().runAtFixedRate(PathFinderPlugin.getInstance(), task, null,
                    Math.max(1L, delayTicks), Math.max(1L, periodTicks));
        } catch (IllegalPluginAccessException ignored) {
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Regions and the global region
    // ---------------------------------------------------------------------------------------------------------

    /** Runs on the thread that owns the chunk; the chunk does not need to be loaded. */
    public static void runAt(World world, int chunkX, int chunkZ, Runnable task) {
        if (!isPluginEnabled() || world == null) {
            return;
        }
        try {
            Bukkit.getRegionScheduler().execute(PathFinderPlugin.getInstance(), world, chunkX, chunkZ, task);
        } catch (IllegalPluginAccessException ignored) {
            // Disabled meanwhile.
        }
    }

    /** Runs on the global region (the main thread on Paper) on the next tick. */
    public static ScheduledTask runGlobal(Runnable task) {
        if (!isPluginEnabled()) {
            return null;
        }
        try {
            return Bukkit.getGlobalRegionScheduler().run(PathFinderPlugin.getInstance(), ignored -> task.run());
        } catch (IllegalPluginAccessException ignored) {
            return null;
        }
    }

    public static ScheduledTask runGlobalLater(Runnable task, long delayTicks) {
        if (!isPluginEnabled()) {
            return null;
        }
        try {
            return Bukkit.getGlobalRegionScheduler().runDelayed(PathFinderPlugin.getInstance(),
                    ignored -> task.run(), Math.max(1L, delayTicks));
        } catch (IllegalPluginAccessException ignored) {
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Async
    // ---------------------------------------------------------------------------------------------------------

    public static ScheduledTask runAsync(Runnable task) {
        return runAsync(ignored -> task.run());
    }

    /** Async variant for tasks that check {@link ScheduledTask#isCancelled()} while they run. */
    public static ScheduledTask runAsync(Consumer<ScheduledTask> task) {
        if (!isPluginEnabled()) {
            return null;
        }
        try {
            return Bukkit.getAsyncScheduler().runNow(PathFinderPlugin.getInstance(), task);
        } catch (IllegalPluginAccessException ignored) {
            return null;
        }
    }

    public static ScheduledTask runLaterAsync(Runnable task, long delayTicks) {
        if (!isPluginEnabled()) {
            return null;
        }
        try {
            return Bukkit.getAsyncScheduler().runDelayed(PathFinderPlugin.getInstance(), ignored -> task.run(),
                    Math.max(1L, delayTicks) * MILLIS_PER_TICK, TimeUnit.MILLISECONDS);
        } catch (IllegalPluginAccessException ignored) {
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // World access
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Whether the block at {@code location} can be read right now without loading its chunk. Reading a block in
     * an unloaded chunk loads it synchronously on Paper and throws on Folia.
     */
    public static boolean isLoaded(Location location) {
        World world = location == null ? null : location.getWorld();
        return world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
