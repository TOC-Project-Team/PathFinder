package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.momu.pathfinder.navigation.runtime.ChunkSnapshots;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Copies of the chunks one path search reads, for searching off the main thread on Folia, which refuses block
 * reads from threads that do not own the region. {@link #around} copies the chunks next to the player on their
 * own thread; the search then asks the regions that own any other chunk it reaches for a copy, together with
 * the chunks around it, and waits for them. Chunks that are not loaded read as {@link TerrainCell#UNLOADED}.
 *
 * <p>One instance serves one search on one thread at a time.
 */
public final class TerrainSnapshot implements BlockSource {
    /** Chunks copied up front in every direction from the player's chunk. */
    private static final int NEAR_RADIUS = 1;
    /** A chunk that is not copied within this time reads as not loaded. */
    private static final long CHUNK_WAIT_MILLIS = 1000L;
    /** Once a search has waited this long in total, it stops asking for more chunks. */
    private static final long TOTAL_WAIT_NANOS = TimeUnit.SECONDS.toNanos(3);

    private final World world;
    /** Copied chunks by chunk key; {@code null} values are chunks that cannot be read. */
    private final Map<Long, ChunkSnapshot> chunks = new HashMap<>();
    private final Map<Long, CompletableFuture<ChunkSnapshot>> requested = new HashMap<>();
    private long lastKey = Long.MIN_VALUE;
    private ChunkSnapshot lastChunk;
    private long waitedNanos;

    private TerrainSnapshot(World world) {
        this.world = world;
    }

    /**
     * Copies the loaded chunks around {@code location} that the current thread owns. Call it on the thread that
     * owns {@code location} (the player's thread), then search on another thread.
     */
    public static TerrainSnapshot around(Location location) {
        World world = location.getWorld();
        TerrainSnapshot snapshot = new TerrainSnapshot(world);
        int centerX = location.getBlockX() >> 4;
        int centerZ = location.getBlockZ() >> 4;
        for (int chunkX = centerX - NEAR_RADIUS; chunkX <= centerX + NEAR_RADIUS; chunkX++) {
            for (int chunkZ = centerZ - NEAR_RADIUS; chunkZ <= centerZ + NEAR_RADIUS; chunkZ++) {
                if (Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
                    snapshot.chunks.put(ChunkSnapshots.key(chunkX, chunkZ),
                            ChunkSnapshots.snapshot(world, chunkX, chunkZ, false));
                }
            }
        }
        return snapshot;
    }

    @Override
    public TerrainCell cell(int x, int y, int z) {
        ChunkSnapshot chunk = chunk(x >> 4, z >> 4);
        if (chunk == null) {
            return TerrainCell.UNLOADED;
        }
        Material type = chunk.getBlockType(x & 15, y, z & 15);
        if ((BlockTypes.flags(type) & BlockTypes.AIR) != 0) {
            return TerrainCell.AIR;
        }
        return TerrainCell.of(type, chunk.getBlockData(x & 15, y, z & 15), new Location(world, x, y, z));
    }

    @Override
    public BlockData blockData(int x, int y, int z) {
        ChunkSnapshot chunk = chunk(x >> 4, z >> 4);
        return chunk == null ? null : chunk.getBlockData(x & 15, y, z & 15);
    }

    private ChunkSnapshot chunk(int chunkX, int chunkZ) {
        long key = ChunkSnapshots.key(chunkX, chunkZ);
        if (key == lastKey) {
            return lastChunk;
        }
        ChunkSnapshot chunk;
        if (chunks.containsKey(key)) {
            chunk = chunks.get(key);
        } else {
            chunk = fetch(chunkX, chunkZ, key);
            chunks.put(key, chunk);
        }
        lastKey = key;
        lastChunk = chunk;
        return chunk;
    }

    /** Asks for the chunk and the ones around it that were not asked for yet, then waits for this one. */
    private ChunkSnapshot fetch(int chunkX, int chunkZ, long key) {
        if (!requested.containsKey(key)) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    long neighbor = ChunkSnapshots.key(chunkX + dx, chunkZ + dz);
                    if (!chunks.containsKey(neighbor) && !requested.containsKey(neighbor)) {
                        requested.put(neighbor, ChunkSnapshots.ifLoaded(world, chunkX + dx, chunkZ + dz));
                    }
                }
            }
        }
        CompletableFuture<ChunkSnapshot> future = requested.remove(key);
        if (future.isDone() || waitedNanos >= TOTAL_WAIT_NANOS || Bukkit.isPrimaryThread()) {
            // Never wait on a server thread: the region we wait for might be the one we are blocking.
            return future.getNow(null);
        }
        long start = System.nanoTime();
        try {
            return future.get(CHUNK_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException e) {
            return null;
        } finally {
            waitedNanos += System.nanoTime() - start;
        }
    }
}
