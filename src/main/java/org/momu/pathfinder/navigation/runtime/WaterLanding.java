package org.momu.pathfinder.navigation.runtime;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.momu.pathfinder.navigation.pathfinding.BlockTypes;

/**
 * Moves navigation targets that sit in water to a spot the player can actually stand on.
 */
public final class WaterLanding {
    private static final int NEAR_WATER_RADIUS = 5;
    private static final int NEAR_WATER_MAX_CHECKS = 100;
    private static final int MAX_SURFACE_CLIMB = 50;
    private static final int SHORE_RADIUS = 3;
    private static final int SHORE_MAX_CHECKS = 50;

    private WaterLanding() {
    }

    /**
     * If the target is in water, returns a better target: a dry spot nearby, else the water surface above it,
     * else a dry spot on the rings around it. Targets outside water are returned unchanged.
     */
    public static Location adjustTarget(Location target) {
        if (!BlockTypes.isWater(target.getBlock())) {
            return target;
        }
        Location nearby = findSafeLandingNearWater(target);
        if (nearby != null) {
            return nearby;
        }

        Location surface = target.clone();
        int climbed = 0;
        while (BlockTypes.isWater(surface.getBlock())
                && surface.getBlockY() < surface.getWorld().getMaxHeight() - 1
                && climbed < MAX_SURFACE_CLIMB) {
            surface.add(0.0, 1.0, 0.0);
            climbed++;
        }
        if (!BlockTypes.isWater(surface.getBlock())) {
            if (BlockTypes.isWater(surface.getBlock().getRelative(0, -1, 0))) {
                surface.setY(surface.getBlockY());
            }
            return surface;
        }

        int checked = 0;
        for (int radius = 1; radius <= SHORE_RADIUS && checked < SHORE_MAX_CHECKS; radius++) {
            for (int dx = -radius; dx <= radius && checked < SHORE_MAX_CHECKS; dx++) {
                for (int dz = -radius; dz <= radius && checked < SHORE_MAX_CHECKS; dz++) {
                    checked++;
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    Location candidate = target.clone().add(dx, 0.0, dz);
                    if (!BlockTypes.isWater(candidate.getBlock()) && isSafeLanding(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return target;
    }

    /**
     * Looks for a dry place to stand: directly above the water, then on growing rings around it (within two
     * blocks up or down).
     *
     * @return the landing spot, or {@code null} if none was found
     */
    public static Location findSafeLandingNearWater(Location water) {
        if (water == null) {
            return null;
        }
        World world = water.getWorld();
        int x = water.getBlockX();
        int y = water.getBlockY();
        int z = water.getBlockZ();

        Location aboveWater = new Location(world, x, y + 1, z);
        if (isSafeLanding(aboveWater)) {
            return aboveWater;
        }

        int checked = 0;
        for (int radius = 1; radius <= NEAR_WATER_RADIUS && checked < NEAR_WATER_MAX_CHECKS; radius++) {
            for (int dx = -radius; dx <= radius && checked < NEAR_WATER_MAX_CHECKS; dx++) {
                for (int dz = -radius; dz <= radius && checked < NEAR_WATER_MAX_CHECKS; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    for (int dy = -2; dy <= 2 && checked < NEAR_WATER_MAX_CHECKS; dy++) {
                        checked++;
                        Location candidate = new Location(world, x + dx, y + dy, z + dz);
                        if (isSafeLanding(candidate) && !BlockTypes.isWater(candidate.getBlock())) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Whether a player fits at this spot (doors, ladders and scaffolding count as open) on solid ground. */
    public static boolean isSafeLanding(Location location) {
        if (location == null) {
            return false;
        }
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        boolean groundHolds = ground.getType().isSolid() || BlockTypes.isLadder(ground)
                || BlockTypes.isScaffolding(ground);
        boolean dangerous = BlockTypes.isLavaOrFire(feet) || BlockTypes.isLavaOrFire(head)
                || BlockTypes.isLavaOrFire(ground);
        return fitsPlayer(feet) && fitsPlayer(head) && groundHolds && !dangerous;
    }

    private static boolean fitsPlayer(Block block) {
        return block.isPassable() || BlockTypes.isAnyDoor(block) || BlockTypes.isLadder(block)
                || BlockTypes.isScaffolding(block);
    }
}
