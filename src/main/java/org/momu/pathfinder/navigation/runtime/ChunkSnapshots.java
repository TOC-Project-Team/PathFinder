package org.momu.pathfinder.navigation.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Copies chunks for work that reads blocks off the main thread. Folia lets only the thread that owns a region
 * read its blocks, so each snapshot is taken on the thread that owns the chunk (the main thread on Paper) and is
 * then read from any thread.
 */
public final class ChunkSnapshots {
    /** Chunk requests waiting at the same time, so a large scan does not take all its chunks in one tick. */
    private static final int MAX_IN_FLIGHT = 16;
    private static final long POLL_MILLIS = 50L;

    private ChunkSnapshots() {
    }

    /**
     * Snapshots the chunks in {@code chunkKeys} (see {@link Chunk#getChunkKey(int, int)}). Chunks that are not
     * loaded are loaded first, and generated only if {@code generate} is set. Blocks until every chunk is done
     * or {@code cancelled} returns {@code true}; never call it from a server thread.
     *
     * @return snapshots by chunk key; chunks that could not be read (not generated, or cancelled) are missing
     */
    public static Map<Long, ChunkSnapshot> take(World world, Iterable<Long> chunkKeys, boolean generate,
                                                BooleanSupplier cancelled) {
        Map<Long, ChunkSnapshot> snapshots = new ConcurrentHashMap<>();
        Semaphore permits = new Semaphore(MAX_IN_FLIGHT);
        try {
            for (long key : chunkKeys) {
                while (!permits.tryAcquire(POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                    if (cancelled.getAsBoolean() || !Scheduling.isPluginEnabled()) {
                        return snapshots;
                    }
                }
                if (cancelled.getAsBoolean()) {
                    return snapshots;
                }
                load(world, chunkX(key), chunkZ(key), generate).whenComplete((snapshot, error) -> {
                    if (snapshot != null) {
                        snapshots.put(key, snapshot);
                    }
                    permits.release();
                });
            }
            while (!permits.tryAcquire(MAX_IN_FLIGHT, POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                if (cancelled.getAsBoolean() || !Scheduling.isPluginEnabled()) {
                    return snapshots;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return snapshots;
    }

    /** One chunk; completes with {@code null} if it is not generated and {@code generate} is off. */
    private static CompletableFuture<ChunkSnapshot> load(World world, int chunkX, int chunkZ, boolean generate) {
        CompletableFuture<ChunkSnapshot> result = new CompletableFuture<>();
        world.getChunkAtAsync(chunkX, chunkZ, generate).whenComplete((chunk, error) -> {
            try {
                if (error != null || chunk == null) {
                    result.complete(null);
                } else if (Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
                    // The future usually completes on the thread that owns the chunk.
                    result.complete(snapshot(world, chunkX, chunkZ, true));
                } else {
                    Scheduling.runAt(world, chunkX, chunkZ,
                            () -> result.complete(snapshot(world, chunkX, chunkZ, true)));
                }
            } catch (RuntimeException e) {
                result.complete(null);
            }
        });
        return result;
    }

    /**
     * Snapshots a chunk if it is loaded, without loading it. The future completes with {@code null} if the chunk
     * is not loaded, and never if PathFinder is disabled before the region gets to it.
     */
    public static CompletableFuture<ChunkSnapshot> ifLoaded(World world, int chunkX, int chunkZ) {
        CompletableFuture<ChunkSnapshot> result = new CompletableFuture<>();
        Scheduling.runAt(world, chunkX, chunkZ, () -> result.complete(snapshot(world, chunkX, chunkZ, false)));
        return result;
    }

    /**
     * Snapshots a chunk right away. Must run on the thread that owns the chunk. Without {@code load}, returns
     * {@code null} if the chunk is not loaded; with it, loads the chunk if it was unloaded since it was loaded
     * for us.
     */
    public static ChunkSnapshot snapshot(World world, int chunkX, int chunkZ, boolean load) {
        try {
            if (!load && !world.isChunkLoaded(chunkX, chunkZ)) {
                return null;
            }
            return world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static long key(int chunkX, int chunkZ) {
        return Chunk.getChunkKey(chunkX, chunkZ);
    }

    private static int chunkX(long key) {
        return (int) key;
    }

    private static int chunkZ(long key) {
        return (int) (key >>> 32);
    }
}
