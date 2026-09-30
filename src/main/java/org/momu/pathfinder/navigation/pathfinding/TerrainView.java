package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.World;

import static org.momu.pathfinder.navigation.pathfinding.PlayerBody.*;

/**
 * Per-search view of the world. Every block is read at most once per {@link AStarPathfinder#findPath} call and
 * turned into a {@link TerrainCell}; standing positions derived from those cells are cached as {@link Stand}s.
 */
final class TerrainView {
    /** Neighbors checked for nearby lava: the four sides and the block below. */
    private static final int[][] LAVA_NEIGHBORS = {
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, -1, 0 }
    };

    final World world;
    final int minY;
    final int maxY;
    private final BlockMap<TerrainCell> cells = new BlockMap<>(8192);
    private final BlockMap<Stand> stands = new BlockMap<>(4096);
    private final BlockFlagCache loadedChunks = new BlockFlagCache(64);

    TerrainView(World world) {
        this.world = world;
        this.minY = world.getMinHeight();
        this.maxY = world.getMaxHeight();
    }

    TerrainCell cell(int x, int y, int z) {
        long key = BlockKey.of(x, y, z);
        TerrainCell cell = cells.get(key);
        if (cell == null) {
            cell = load(x, y, z);
            cells.put(key, cell);
        }
        return cell;
    }

    /** Same as {@link #cell(int, int, int)}, but blocks broken to get here or by the move read as air. */
    TerrainCell cell(int x, int y, int z, PathNode from, MoveResult move) {
        if ((from != null && from.brokenKeys.length > 0) || (move != null && move.breakCount > 0)) {
            long key = BlockKey.of(x, y, z);
            if ((from != null && from.isBroken(key)) || (move != null && move.isBroken(key))) {
                return TerrainCell.AIR;
            }
        }
        return cell(x, y, z);
    }

    private TerrainCell load(int x, int y, int z) {
        if (y < minY) {
            return TerrainCell.VOID;
        }
        if (y >= maxY) {
            return TerrainCell.AIR;
        }
        if (!isChunkLoaded(x >> 4, z >> 4)) {
            return TerrainCell.UNLOADED;
        }
        return TerrainCell.of(world.getBlockAt(x, y, z));
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

    // ---------------------------------------------------------------------------------------------------------
    // Standing positions
    // ---------------------------------------------------------------------------------------------------------

    /** Whether (and how) the player can be at a block position with their feet in it. */
    static final class Stand {
        /** Standing on something. */
        static final int GROUND = 1;
        /** Feet in water. */
        static final int SWIM = 1 << 1;
        /** Feet in a ladder, vine or scaffolding. */
        static final int CLIMB = 1 << 2;
        /** Head in water as well. */
        static final int SUBMERGED = 1 << 3;

        static final Stand INVALID = new Stand(false, 0.0, 0, 0, 0, Integer.MIN_VALUE, 0);

        final boolean valid;
        /** Height of the player's feet. */
        final double feet;
        final int kind;
        /** {@link BlockTypes} flags of the block the player stands on. */
        final int floorFlags;
        /** Position of the first block in the way when the position is blocked. */
        final int obstacleX, obstacleY, obstacleZ;

        private Stand(boolean valid, double feet, int kind, int floorFlags, int obstacleX, int obstacleY,
                      int obstacleZ) {
            this.valid = valid;
            this.feet = feet;
            this.kind = kind;
            this.floorFlags = floorFlags;
            this.obstacleX = obstacleX;
            this.obstacleY = obstacleY;
            this.obstacleZ = obstacleZ;
        }

        boolean is(int kindFlag) {
            return (kind & kindFlag) != 0;
        }

        /** Invalid only because a block is in the player's way (see {@link #obstacleY}). */
        boolean isBlocked() {
            return !valid && obstacleY != Integer.MIN_VALUE;
        }
    }

    Stand stand(int x, int y, int z) {
        long key = BlockKey.of(x, y, z);
        Stand stand = stands.get(key);
        if (stand == null) {
            stand = computeStand(x, y, z, null, null);
            stands.put(key, stand);
        }
        return stand;
    }

    /** {@link #stand(int, int, int)} for a move that breaks blocks; uncached because breaking changes it. */
    Stand stand(int x, int y, int z, PathNode from, MoveResult move) {
        if ((from == null || from.brokenKeys.length == 0) && (move == null || move.breakCount == 0)) {
            return stand(x, y, z);
        }
        return computeStand(x, y, z, from, move);
    }

    private Stand computeStand(int x, int y, int z, PathNode from, MoveResult move) {
        if (y <= minY || y >= maxY) {
            return Stand.INVALID;
        }
        TerrainCell feet = cell(x, y, z, from, move);
        TerrainCell below = cell(x, y - 1, z, from, move);
        double px = x + 0.5;
        double pz = z + 0.5;

        // The floor is the highest box top under the footprint that lies within this block's height.
        double support = below.highestTop(x, y - 1, z, px, pz, y - EPS, y + 1 - EPS, Double.NEGATIVE_INFINITY);
        int floorFlags = support > Double.NEGATIVE_INFINITY ? below.flags : 0;
        double inFeetBlock = feet.highestTop(x, y, z, px, pz, y - EPS, y + 1 - EPS, Double.NEGATIVE_INFINITY);
        if (inFeetBlock > support) {
            support = inFeetBlock;
            floorFlags = feet.flags;
        }

        int kind = 0;
        double feetY = y;
        if (support > Double.NEGATIVE_INFINITY) {
            kind |= Stand.GROUND;
            feetY = support;
            if ((floorFlags & BlockTypes.FLOOR_HAZARD) != 0) {
                return Stand.INVALID;
            }
        }
        if (feet.water) {
            kind |= Stand.SWIM;
        }
        if (feet.has(BlockTypes.CLIMBABLE)) {
            kind |= Stand.CLIMB;
        }
        if (kind == 0) {
            return Stand.INVALID;
        }

        int topY = (int) Math.floor(feetY + HEIGHT - EPS);
        for (int cellY = y - 1; cellY <= topY; cellY++) {
            TerrainCell c = cellY == y ? feet : cellY == y - 1 ? below : cell(x, cellY, z, from, move);
            if (cellY >= y && c.has(BlockTypes.BODY_HAZARD)) {
                return Stand.INVALID;
            }
            // Scaffolding is walked through, and doors, trapdoors and gates are opened.
            if (c.has(BlockTypes.PLATFORM | BlockTypes.OPENABLE)) {
                continue;
            }
            double[] boxes = c.boxes;
            for (int k = 0; k < boxes.length; k += 6) {
                if (cellY + boxes[k + 4] <= feetY + EPS || cellY + boxes[k + 1] >= feetY + HEIGHT - EPS
                        || !overlapsFootprint(boxes, k, x, z, px, pz)) {
                    continue;
                }
                return new Stand(false, 0.0, 0, 0, x, cellY, z);
            }
        }

        for (int[] offset : LAVA_NEIGHBORS) {
            if (cell(x + offset[0], y + offset[1], z + offset[2]).has(BlockTypes.LAVA)) {
                return Stand.INVALID;
            }
        }

        if ((kind & Stand.SWIM) != 0 && cell(x, y + 1, z, from, move).water) {
            kind |= Stand.SUBMERGED;
        }
        return new Stand(true, feetY, kind, floorFlags, 0, Integer.MIN_VALUE, 0);
    }
}
