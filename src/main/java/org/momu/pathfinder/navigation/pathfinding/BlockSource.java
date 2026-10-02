package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.block.data.BlockData;

/**
 * Where a path search reads the world from: the live world ({@link LiveBlockSource}) or chunk copies
 * ({@link TerrainSnapshot}). Heights outside the world are handled by {@link TerrainView} before asking.
 */
interface BlockSource {
    /** The block at (x, y, z), or {@link TerrainCell#UNLOADED} if it cannot be read. */
    TerrainCell cell(int x, int y, int z);

    /** The block's data, or {@code null} if it cannot be read. */
    BlockData blockData(int x, int y, int z);
}
