package org.momu.pathfinder.navigation.runtime;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.momu.pathfinder.navigation.pathfinding.BlockTypes;

/**
 * Detects players who are falling or flying, for whom no path is drawn until they land.
 */
final class Airborne {
    private static final double RISING_VELOCITY = 0.05;
    private static final double FAST_FALL_VELOCITY = -5.5;
    private static final int GROUND_SCAN_DEPTH = 10;

    private Airborne() {
    }

    static boolean isAirborne(Player player) {
        Location location = player.getLocation();
        double verticalVelocity = player.getVelocity().getY();
        if (verticalVelocity > RISING_VELOCITY) {
            return false;
        }
        Block feet = location.getBlock();
        if (canStandOn(feet.getRelative(0, -1, 0))) {
            return false;
        }
        for (int depth = 2; depth <= GROUND_SCAN_DEPTH; depth++) {
            if (canStandOn(feet.getRelative(0, -depth, 0)) && verticalVelocity >= FAST_FALL_VELOCITY) {
                return false;
            }
        }
        return true;
    }

    private static boolean canStandOn(Block block) {
        return block.getType().isSolid() || BlockTypes.isWater(block) || BlockTypes.isLadder(block)
                || BlockTypes.isScaffolding(block) || BlockTypes.isAnyDoor(block);
    }
}
