package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.Location;

/**
 * Per-search cache over {@link TerrainRules}. The search asks the same questions about a block many times, so
 * answers are remembered for the lifetime of one {@link AStarPathfinder#findPath} call.
 */
final class TerrainView {
    private static final byte FALSE = 1;
    private static final byte TRUE = 2;

    private static final byte WATER_KNOWN = 1;
    private static final byte WATER_INSIDE = 2;
    private static final byte WATER_SURFACE = 4;

    private final BlockFlagCache standable = new BlockFlagCache(2048);
    private final BlockFlagCache water = new BlockFlagCache(2048);

    boolean isStandable(Location location) {
        long key = BlockKey.of(location);
        byte cached = standable.get(key);
        if (cached != BlockFlagCache.MISSING) {
            return cached == TRUE;
        }
        boolean result = TerrainRules.isStandable(location, this);
        standable.put(key, result ? TRUE : FALSE);
        return result;
    }

    boolean isInsideWater(Location location) {
        return (waterFlags(location) & WATER_INSIDE) != 0;
    }

    boolean isOnWaterSurface(Location location) {
        return (waterFlags(location) & WATER_SURFACE) != 0;
    }

    private byte waterFlags(Location location) {
        long key = BlockKey.of(location);
        byte cached = water.get(key);
        if (cached != BlockFlagCache.MISSING) {
            return cached;
        }
        byte flags = WATER_KNOWN;
        if (TerrainRules.isInsideWater(location)) {
            flags |= WATER_INSIDE;
        }
        if (TerrainRules.isOnWaterSurface(location)) {
            flags |= WATER_SURFACE;
        }
        water.put(key, flags);
        return flags;
    }
}
