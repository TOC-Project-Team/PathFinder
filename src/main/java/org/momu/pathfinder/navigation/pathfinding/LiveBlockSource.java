package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * Reads blocks straight from the world. Chunks that are not loaded read as {@link TerrainCell#UNLOADED} instead
 * of being loaded. Used off the main thread on Paper, and on Folia only from the thread that owns the region.
 */
final class LiveBlockSource implements BlockSource {
    private final World world;
    private final BlockFlagCache loadedChunks = new BlockFlagCache(64);

    LiveBlockSource(World world) {
        this.world = world;
    }

    @Override
    public TerrainCell cell(int x, int y, int z) {
        if (!isChunkLoaded(x >> 4, z >> 4)) {
            return TerrainCell.UNLOADED;
        }
        try {
            return TerrainCell.of(world.getBlockAt(x, y, z));
        } catch (IllegalStateException e) {
            // Folia: the chunk was unloaded by the region that owns it after the check above.
            return TerrainCell.UNLOADED;
        }
    }

    @Override
    public BlockData blockData(int x, int y, int z) {
        if (!isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        try {
            return world.getBlockAt(x, y, z).getBlockData();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private boolean isChunkLoaded(int chunkX, int chunkZ) {
        long key = BlockKey.of(chunkX, 0, chunkZ);
        byte cached = loadedChunks.get(key);
        if (cached != BlockFlagCache.MISSING) {
            return cached == 2;
        }
        boolean loaded;
        try {
            loaded = world.isChunkLoaded(chunkX, chunkZ);
        } catch (Throwable ignored) {
            loaded = false;
        }
        loadedChunks.put(key, loaded ? (byte) 2 : (byte) 1);
        return loaded;
    }
}
