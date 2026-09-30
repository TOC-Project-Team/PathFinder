package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import static org.momu.pathfinder.navigation.pathfinding.BlockTypes.*;

/**
 * Stateless questions about the world that the search asks for a single position.
 * {@link TerrainView} adds per-search caching on top of the expensive ones.
 */
final class TerrainRules {
    /** Neighbors checked for nearby lava: the four sides and the block below. */
    private static final int[][] HAZARD_DIRECTIONS = {
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, -1, 0 }
    };
    /** Blocks at least this hard (obsidian and up) are never broken. */
    private static final float UNBREAKABLE_HARDNESS = 50.0f;

    private TerrainRules() {
    }

    /**
     * Whether a player can stand with their feet at {@code location}: feet and head fit, the ground holds them,
     * and nothing dangerous is at or next to the spot.
     */
    static boolean isStandable(Location location, TerrainView view) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        if (isObstacle(feet) || isObstacle(head)
                || isLavaOrFire(feet) || isLavaOrFire(head) || isLavaOrFire(ground)) {
            return false;
        }
        if (view.isInsideWater(location) && !isWater(head) && !head.isPassable()) {
            return false;
        }
        if (isNextToLavaOrFire(feet)) {
            return false;
        }

        boolean feetFit = (feet.isPassable() && !isObstacle(feet)) || isOpenable(feet)
                || isWater(feet)
                || (isLowBlockButNotStair(feet) && head.isPassable() && !isObstacle(head));
        boolean headFits = (head.isPassable() && !isObstacle(head)) || isOpenable(head);
        boolean groundHolds = ground.getType().isSolid() || isWater(ground) || isLadder(ground) || isScaffolding(ground);
        return feetFit && headFits && groundHolds;
    }

    /** Blocks a player can move through by opening or climbing them. */
    private static boolean isOpenable(Block block) {
        return isAnyDoor(block) || isBanner(block) || isPassableTrapdoor(block) || isLadder(block)
                || isScaffolding(block) || isFenceGate(block);
    }

    private static boolean isNextToLavaOrFire(Block block) {
        for (int[] direction : HAZARD_DIRECTIONS) {
            if (isLavaOrFire(block.getRelative(direction[0], direction[1], direction[2]))) {
                return true;
            }
        }
        return false;
    }

    /** Submerged: water at the feet and either above them or at least two blocks deep below. */
    static boolean isInsideWater(Location location) {
        Block feet = location.getBlock();
        if (!isWater(feet)) {
            return false;
        }
        if (isWater(feet.getRelative(0, 1, 0))) {
            return true;
        }
        return isWater(feet.getRelative(0, -1, 0)) && isWater(feet.getRelative(0, -2, 0));
    }

    /** Near the water surface: standing on water, just under it, or in water one block deep with air above. */
    static boolean isOnWaterSurface(Location location) {
        Block feet = location.getBlock();
        Block below = feet.getRelative(0, -1, 0);
        Block above = feet.getRelative(0, 1, 0);

        boolean feetOpen = feet.isPassable() || !isWater(feet);
        boolean onTop = feetOpen && isWater(below);
        boolean justUnder = feetOpen && isWater(above) && isWater(below);
        boolean shallow = isWater(feet) && above.isPassable() && !isWater(above);
        return onTop || justUnder || shallow;
    }

    /** A spot where the player can swim along the surface with their head above water. */
    static boolean isTraversableWaterSurface(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block below = feet.getRelative(0, -1, 0);

        boolean feetWater = isWater(feet);
        boolean headWater = isWater(head);
        boolean belowWater = isWater(below);

        boolean feetOpen = (feet.isPassable() && !isObstacle(feet)) || feetWater || (belowWater && isKelp(feet));
        boolean headOpen = head.isPassable() && !headWater && !isObstacle(head);
        return feetOpen && headOpen && ((!feetWater && belowWater) || (feetWater && !headWater));
    }

    /** Room above the player's head for the arc of a jump. */
    static boolean hasJumpClearance(Block feet) {
        for (int y = 1; y <= 2; y++) {
            Block block = feet.getRelative(0, y, 0);
            if (!block.isPassable() && block.getType().isSolid() && !isAnyDoor(block) && !isBanner(block)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a solid block in the player's way may be broken to get through. */
    static boolean shouldBreak(Block block) {
        Material type = block.getType();
        if (type.isAir() || block.isPassable() || !type.isSolid()) {
            return false;
        }
        if (isAnyDoor(block) || isBanner(block) || isTrapdoor(block) || isScaffolding(block) || isFenceGate(block)) {
            return false;
        }
        if (isLowBlockButNotStair(block)) {
            return false;
        }
        return isBreakable(block);
    }

    /** Whether breaking this block is allowed at all (not too hard, not structural, not next to lava). */
    static boolean isBreakable(Block block) {
        Material type = block.getType();
        if (type.isAir() || block.isPassable()) {
            return false;
        }
        if (!type.isSolid() || type.getHardness() >= UNBREAKABLE_HARDNESS) {
            return false;
        }
        if (isAnyDoor(block) || isBanner(block) || isTrapdoor(block)) {
            return false;
        }
        if (isLowBlockButNotStair(block)) {
            Block above = block.getRelative(0, 1, 0);
            if (above.isPassable() && !isObstacle(above)) {
                return false;
            }
        }
        if (isKelp(block) && isWater(block.getRelative(0, -1, 0))) {
            return false;
        }
        // Fence + carpet is a walkable bridge; keep both parts intact.
        if (isCarpet(block) && isFence(block.getRelative(0, -1, 0)) && block.getRelative(0, 1, 0).isPassable()) {
            return false;
        }
        if (isFence(block) && isCarpet(block.getRelative(0, 1, 0)) && block.getRelative(0, 2, 0).isPassable()) {
            return false;
        }
        if (isUnbreakable(block)) {
            return false;
        }
        for (int[] direction : HAZARD_DIRECTIONS) {
            if (isLava(block.getRelative(direction[0], direction[1], direction[2]))) {
                return false;
            }
        }
        return true;
    }

    /** Whether the player's body can pass through this block while stepping down into it. */
    static boolean isPassableForDescent(Block block) {
        if (isObstacle(block) || isLavaOrFire(block)) {
            return false;
        }
        return block.isPassable() || isAnyDoor(block) || isBanner(block) || isPassableTrapdoor(block)
                || isLadder(block) || isScaffolding(block) || isFenceGate(block) || isWater(block)
                || isLowBlockButNotStair(block);
    }
}
