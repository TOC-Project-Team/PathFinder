package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.momu.pathfinder.config.PathfinderConfig;

import java.util.ArrayList;
import java.util.List;

import static org.momu.pathfinder.navigation.pathfinding.BlockTypes.*;

/**
 * A* search over block positions for a walking player.
 *
 * <p>From each node the search tries:
 * <ul>
 *     <li>walking steps to the 8 neighbors (and straight up/down), one block up or a few blocks down,
 *     see {@link #tryWalkingStep}</li>
 *     <li>jumps across gaps of 2 to {@code max_block_jump_distance} blocks, see {@link #tryBlockJumps}</li>
 * </ul>
 * Steps that need a block broken are allowed but cost {@code break_block_cost} per block. All costs come
 * from {@link PathfinderConfig}. If the goal cannot be reached within {@code max_iterations}, the path to the
 * explored node closest to the goal is returned instead.
 *
 * <p>Runs off the main thread; it only reads blocks.
 */
public final class AStarPathfinder {
    /** Walking directions: the 8 neighbors plus "stay in this column" for climbing and dropping. */
    private static final int[] STEP_DX = { 0, 1, -1, 0, 1, -1, 1, -1, 0 };
    private static final int[] STEP_DZ = { 1, 0, 0, -1, 1, 1, -1, -1, 0 };
    /** Block jumps only go in the four cardinal directions. */
    private static final int[] JUMP_DX = { 0, 1, -1, 0 };
    private static final int[] JUMP_DZ = { 1, 0, 0, -1 };

    private final Location start;
    private final Location goal;
    private final TerrainView terrain = new TerrainView();
    private final IndexedOpenSet open = new IndexedOpenSet(1024);
    private final NodeIndex nodesByBlock = new NodeIndex(2048);
    private final List<PathNode> allNodes = new ArrayList<>(1024);

    private AStarPathfinder(Location start, Location goal) {
        this.start = start;
        this.goal = goal.getBlock().getLocation();
    }

    /**
     * Finds a path between two locations in the same world.
     *
     * @return the path from start to goal (or to the closest reachable point), never empty
     */
    public static List<PathNode> findPath(Location start, Location goal) {
        return new AStarPathfinder(start, goal).search();
    }

    private List<PathNode> search() {
        Location startBlock = start.getBlock().getLocation();
        PathNode startNode = new PathNode(startBlock, null, 0, heuristic(startBlock), MoveType.HORIZONTAL);
        open.add(startNode);
        register(BlockKey.of(startBlock), startNode);

        double maxRadiusSquared = (double) PathfinderConfig.MAX_SEARCH_RADIUS * PathfinderConfig.MAX_SEARCH_RADIUS;
        int iterations = 0;
        while (!open.isEmpty() && iterations < PathfinderConfig.MAX_ITERATIONS) {
            iterations++;
            PathNode current = open.poll();
            current.closed = true;

            if (current.location.distanceSquared(goal) < 1.0) {
                return PathPostProcessor.buildPath(current);
            }
            if (current.location.distanceSquared(start) > maxRadiusSquared) {
                continue;
            }

            for (int i = 0; i < STEP_DX.length; i++) {
                tryWalkingDirection(current, STEP_DX[i], STEP_DZ[i]);
            }
            tryBlockJumps(current);
        }
        return PathPostProcessor.buildPath(closestToGoal());
    }

    private PathNode closestToGoal() {
        PathNode closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (PathNode node : allNodes) {
            double distance = node.location.distanceSquared(goal);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = node;
            }
        }
        return closest;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Walking steps
    // ---------------------------------------------------------------------------------------------------------

    private void tryWalkingDirection(PathNode current, int dx, int dz) {
        if (dx != 0 && dz != 0 && !canCutCorner(current.location, dx, dz)) {
            return;
        }

        int lowestStep = (dx == 0 && dz == 0) ? -PathfinderConfig.MAX_SAFE_FALL_HEIGHT : -2;
        if (Math.abs(current.location.getY() - goal.getY()) > 10) {
            lowestStep = Math.max(lowestStep, -5);
        }

        for (int dy = lowestStep; dy <= 1; dy++) {
            Location next = current.location.clone().add(dx, dy, dz);
            long key = BlockKey.of(next);
            PathNode existing = nodesByBlock.get(key);
            if (existing != null && existing.closed) {
                continue;
            }
            Step step = tryWalkingStep(current, next, dx, dz, dy);
            if (step != null) {
                relax(current, next, key, existing, step.cost, step.moveType, step.blocksToBreak.toList());
            }
        }
    }

    /** A diagonal step must not clip through the two blocks at the corner. */
    private boolean canCutCorner(Location from, int dx, int dz) {
        Location side1 = from.clone().add(dx, 0, 0);
        Location side2 = from.clone().add(0, 0, dz);
        Location diagonal = from.clone().add(dx, 0, dz);
        return terrain.isStandable(side1) && terrain.isStandable(side2) && terrain.isStandable(diagonal)
                && headFitsForCorner(side1) && headFitsForCorner(side2) && headFitsForCorner(diagonal);
    }

    private static boolean headFitsForCorner(Location feet) {
        Block head = feet.getBlock().getRelative(0, 1, 0);
        return head.isPassable() || isAnyDoor(head) || isBanner(head);
    }

    private record Step(MoveType moveType, BreakList blocksToBreak, double cost) {
    }

    /**
     * Checks one walking step and prices it.
     *
     * @return the step, or {@code null} if the player cannot make it
     */
    private Step tryWalkingStep(PathNode current, Location next, int dx, int dz, int dy) {
        MoveType move;
        if (dy > 0) {
            move = (dx != 0 || dz != 0) ? MoveType.JUMP : MoveType.UP;
        } else if (dy < 0) {
            move = MoveType.DOWN;
        } else {
            move = MoveType.HORIZONTAL;
        }

        Block nextFeet = next.getBlock();
        Block nextHead = nextFeet.getRelative(0, 1, 0);
        Block nextCeiling = nextFeet.getRelative(0, 2, 0);
        BreakList breaks = new BreakList();

        switch (move) {
            case HORIZONTAL -> {
                if (!checkWalkHeadroom(nextHead, breaks)) {
                    return null;
                }
            }
            case DOWN -> {
                move = checkDescent(current.location, next, breaks);
                if (move == null) {
                    return null;
                }
            }
            case UP -> {
                if (!checkClimb(current.location.getBlock(), nextFeet, nextHead, nextCeiling, breaks)) {
                    return null;
                }
            }
            case JUMP -> {
                if (!checkStepUp(current.location.getBlock(), nextFeet, nextHead, breaks)) {
                    return null;
                }
            }
            default -> {
            }
        }
        if (move == MoveType.FALL && !isLowBlockButNotStair(nextFeet) && TerrainRules.shouldBreak(nextFeet)) {
            breaks.add(nextFeet);
        }

        boolean diagonal = dx != 0 && dz != 0;
        double cost = diagonal ? PathfinderConfig.DIAGONAL_COST : PathfinderConfig.STRAIGHT_COST;
        cost += turnPenalty(current, dx, dz);

        if (move == MoveType.HORIZONTAL && !checkWalkHeadPath(nextFeet, nextHead, breaks)) {
            return null;
        }
        if (!terrain.isStandable(next) && !clearFeet(nextFeet, breaks)) {
            return null;
        }
        if (!breaks.isEmpty()) {
            cost += PathfinderConfig.BREAK_BLOCK_COST * breaks.size();
        }

        if (terrain.isInsideWater(next)) {
            cost += PathfinderConfig.WATER_COST * 5.0;
        }
        if (terrain.isOnWaterSurface(next)) {
            cost += PathfinderConfig.WATER_COST;
        }
        if (isAnyDoor(nextFeet) || isBanner(nextFeet) || isFenceGate(nextFeet)) {
            cost += PathfinderConfig.DOOR_COST;
        }
        if (!canCrossFence(nextFeet)) {
            return null;
        }
        if (isTrapdoor(nextFeet)) {
            cost += PathfinderConfig.TRAPDOOR_COST;
        }
        if (isTrapdoor(nextHead)) {
            cost += PathfinderConfig.TRAPDOOR_COST;
        }
        if (isScaffolding(nextFeet)) {
            cost += PathfinderConfig.SCAFFOLDING_COST;
        }
        if (move == MoveType.JUMP) {
            cost += PathfinderConfig.JUMP_COST;
        }
        if (move == MoveType.UP) {
            cost += PathfinderConfig.VERTICAL_COST;
        } else if (move == MoveType.FALL) {
            cost += PathfinderConfig.FALL_COST;
            int fallHeight = current.location.getBlockY() - next.getBlockY();
            if (fallHeight > 1) {
                cost += (fallHeight - 1) * 0.1;
            }
        }
        return new Step(move, breaks, cost);
    }

    /** Extra cost for changing direction, which keeps paths straight. */
    private static double turnPenalty(PathNode current, int dx, int dz) {
        if (current.parent == null) {
            return 0;
        }
        int prevX = current.dirX;
        int prevZ = current.dirZ;
        if ((prevX == 0 && prevZ == 0) || (dx == prevX && dz == prevZ)) {
            return 0;
        }
        if (dx * prevX + dz * prevZ == 0) {
            return PathfinderConfig.RIGHT_ANGLE_TURN_COST;
        }
        boolean wasDiagonal = prevX != 0 && prevZ != 0;
        boolean isDiagonal = dx != 0 && dz != 0;
        return wasDiagonal != isDiagonal ? PathfinderConfig.DIAGONAL_TURN_COST : 0;
    }

    /** Walking forward: the head must fit through (doors, banners and open trapdoors count as open). */
    private static boolean checkWalkHeadroom(Block nextHead, BreakList breaks) {
        boolean headOpen = nextHead.isPassable() || isAnyDoor(nextHead) || isBanner(nextHead)
                || isPassableTrapdoor(nextHead);
        return headOpen || breakOrReject(nextHead, breaks);
    }

    /**
     * Second headroom check for walking forward. Stricter than {@link #checkWalkHeadroom}: a closed trapdoor
     * at head height is not accepted here. Kelp standing in water is ignored.
     */
    private static boolean checkWalkHeadPath(Block nextFeet, Block nextHead, BreakList breaks) {
        boolean kelpInWater = isKelp(nextHead) && isWater(nextFeet);
        if (kelpInWater || !blocksBody(nextHead)) {
            return true;
        }
        return breakOrReject(nextHead, breaks);
    }

    private static boolean blocksBody(Block block) {
        return !block.isPassable() && block.getType().isSolid() && !isAnyDoor(block) && !isBanner(block);
    }

    /**
     * Stepping down into a lower column. Always turns into a {@link MoveType#FALL}; a drop higher than
     * {@code max_safe_fall_height} is shortened by breaking blocks below the player.
     *
     * @return {@link MoveType#FALL}, or {@code null} if the player cannot get down here
     */
    private static MoveType checkDescent(Location from, Location to, BreakList breaks) {
        if (!collectDescentClearance(from, to, breaks)) {
            return null;
        }
        int fallHeight = from.getBlockY() - to.getBlockY();
        if (fallHeight > PathfinderConfig.MAX_SAFE_FALL_HEIGHT) {
            if (isWater(to.getBlock())) {
                return null;
            }
            int breakDepth = fallHeight - PathfinderConfig.MAX_SAFE_FALL_HEIGHT;
            Block fromFeet = from.getBlock();
            for (int depth = 1; depth <= breakDepth; depth++) {
                Block below = fromFeet.getRelative(0, -depth, 0);
                if (!TerrainRules.isBreakable(below)) {
                    return null;
                }
                breaks.add(below);
            }
        }
        return MoveType.FALL;
    }

    /**
     * Checks the whole volume the player passes through while dropping into the lower column: from the landing
     * spot up to one block above the player's head.
     */
    private static boolean collectDescentClearance(Location from, Location to, BreakList breaks) {
        int highestY = Math.max(from.getBlockY() + 1, to.getBlockY() + 2);
        for (int y = to.getBlockY(); y <= highestY; y++) {
            Block block = to.getWorld().getBlockAt(to.getBlockX(), y, to.getBlockZ());
            if (TerrainRules.isPassableForDescent(block)) {
                continue;
            }
            if (!TerrainRules.shouldBreak(block)) {
                return false;
            }
            breaks.add(block);
        }
        return true;
    }

    /** Climbing straight up works only on ladders, scaffolding and water columns. */
    private static boolean checkClimb(Block fromFeet, Block nextFeet, Block nextHead, Block nextCeiling,
                                      BreakList breaks) {
        boolean ladder = isLadder(fromFeet) && isLadder(nextFeet);
        boolean scaffolding = isScaffolding(fromFeet) && isScaffolding(nextFeet);
        boolean water = isWater(fromFeet) && isWater(nextFeet);
        if (!ladder && !scaffolding && !water) {
            return false;
        }
        if (!scaffolding) {
            if (blocksClimb(nextHead) && !isScaffolding(nextHead) && !breakOrReject(nextHead, breaks)) {
                return false;
            }
            if (blocksClimb(nextCeiling) && !isScaffolding(nextCeiling) && !breakOrReject(nextCeiling, breaks)) {
                return false;
            }
        }
        return !blocksClimb(fromFeet) || breakOrReject(fromFeet, breaks);
    }

    private static boolean blocksClimb(Block block) {
        return !block.isPassable() && block.getType().isSolid()
                && !isAnyDoor(block) && !isBanner(block) && !isIronTrapdoor(block);
    }

    /** Stepping up one block while moving sideways. */
    private static boolean checkStepUp(Block fromFeet, Block nextFeet, Block nextHead, BreakList breaks) {
        Block belowNext = nextFeet.getRelative(0, -1, 0);
        if (isBanner(nextFeet) || isBanner(nextHead) || isBanner(belowNext)) {
            return false;
        }

        // A player on a slab or other low block cannot jump a full block higher.
        Block fromGround = fromFeet.getRelative(0, -1, 0);
        boolean onScaffolding = isScaffolding(fromGround) || isScaffolding(fromFeet);
        boolean onLowBlock = isLowBlockButNotStair(fromFeet) || isLowBlockButNotStair(fromGround);
        if (onLowBlock && !onScaffolding) {
            return false;
        }

        if (isFenceGate(nextFeet) || isFenceGate(belowNext)) {
            return false;
        }
        // Fences are too tall to step onto unless a carpet makes them walkable.
        if (isFence(nextFeet) && !isCarpet(nextHead)) {
            return false;
        }
        if (isFence(belowNext) && !isCarpet(nextFeet)) {
            return false;
        }

        boolean headBlocked = blocksClimb(nextHead) && !isScaffolding(nextHead) && !isFenceGate(nextHead)
                && !isLowBlock(nextHead);
        if (headBlocked && !breakOrReject(nextHead, breaks)) {
            return false;
        }
        Block fromHead = fromFeet.getRelative(0, 1, 0);
        if (blocksClimb(fromHead) && !breakOrReject(fromHead, breaks)) {
            return false;
        }
        Block fromCeiling = fromFeet.getRelative(0, 2, 0);
        return !blocksClimb(fromCeiling) || breakOrReject(fromCeiling, breaks);
    }

    /** If the landing spot itself is not standable, the block at the feet may be broken. */
    private static boolean clearFeet(Block nextFeet, BreakList breaks) {
        boolean kelpInWater = isKelp(nextFeet) && isWater(nextFeet.getRelative(0, -1, 0));
        if (kelpInWater) {
            return true;
        }
        if (!isAnyDoor(nextFeet) && !isBanner(nextFeet) && !isLowBlockButNotStair(nextFeet)
                && TerrainRules.shouldBreak(nextFeet)) {
            breaks.add(nextFeet);
            return true;
        }
        return false;
    }

    /** A fence at or below the feet can only be crossed if it carries a carpet with room above. */
    private static boolean canCrossFence(Block nextFeet) {
        Block below = nextFeet.getRelative(0, -1, 0);
        Block fence;
        if (isFence(nextFeet)) {
            fence = nextFeet;
        } else if (isFence(below)) {
            fence = below;
        } else {
            return true;
        }
        Block carpet = fence.getRelative(0, 1, 0);
        return isCarpet(carpet) && carpet.getRelative(0, 1, 0).isPassable();
    }

    private static boolean breakOrReject(Block block, BreakList breaks) {
        if (!TerrainRules.shouldBreak(block)) {
            return false;
        }
        breaks.add(block);
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Block jumps
    // ---------------------------------------------------------------------------------------------------------

    private void tryBlockJumps(PathNode current) {
        Block feet = current.location.getBlock();
        Block ground = feet.getRelative(0, -1, 0);
        boolean onScaffolding = isScaffolding(ground) || isScaffolding(feet);
        boolean onLowBlock = (isLowBlockButNotStair(feet) || isLowBlockButNotStair(ground)) && !onScaffolding;
        if (TerrainRules.isTraversableWaterSurface(current.location) || onLowBlock
                || !TerrainRules.hasJumpClearance(feet)) {
            return;
        }

        for (int direction = 0; direction < JUMP_DX.length; direction++) {
            int dirX = JUMP_DX[direction];
            int dirZ = JUMP_DZ[direction];
            for (int distance = 2; distance <= PathfinderConfig.MAX_BLOCK_JUMP_DISTANCE; distance++) {
                tryBlockJump(current, dirX, dirZ, distance);
            }
        }
    }

    private void tryBlockJump(PathNode current, int dirX, int dirZ, int distance) {
        Location target = current.location.clone().add(dirX * distance, 0, dirZ * distance);
        long key = BlockKey.of(target);
        PathNode existing = nodesByBlock.get(key);
        if (existing != null && existing.closed) {
            return;
        }
        if (TerrainRules.isTraversableWaterSurface(target) || !terrain.isStandable(target)) {
            return;
        }
        if (isJumpArcBlocked(current.location, dirX, dirZ, distance)) {
            return;
        }
        // A two-block "jump" over walkable ground is just two normal steps.
        if (distance == 2 && terrain.isStandable(current.location.clone().add(dirX, 0, dirZ))) {
            return;
        }
        Block targetFeet = target.getBlock();
        Block belowTarget = targetFeet.getRelative(0, -1, 0);
        if (isFenceGate(targetFeet) || isFenceGate(belowTarget) || isBanner(targetFeet) || isBanner(belowTarget)) {
            return;
        }

        double cost = PathfinderConfig.BLOCK_JUMP_COST * distance * distance;
        relax(current, target, key, existing, cost, MoveType.BLOCK_JUMP, null);
    }

    /** Whether anything in the blocks between take-off and landing (feet to above the head) blocks the jump. */
    private static boolean isJumpArcBlocked(Location from, int dirX, int dirZ, int distance) {
        for (int step = 1; step < distance; step++) {
            Block path = from.clone().add(dirX * step, 0, dirZ * step).getBlock();
            Block belowPath = path.getRelative(0, -1, 0);
            boolean jumpableFence = isJumpableFence(path);

            if (isBanner(path) || isBanner(belowPath)) {
                return true;
            }
            if (!path.isPassable() && path.getType().isSolid() && !jumpableFence && !isAnyDoor(path)) {
                return true;
            }
            if ((isFence(path) && !jumpableFence) || isFenceGate(path) || isFenceGate(belowPath)) {
                return true;
            }
            if (isWater(path) || isObstacle(path) || isLavaOrFire(path)) {
                return true;
            }
            for (int y = 1; y <= 2; y++) {
                Block above = path.getRelative(0, y, 0);
                if (isBanner(above)) {
                    return true;
                }
                if (!above.isPassable() && above.getType().isSolid() && !isAnyDoor(above)) {
                    return true;
                }
                if (isObstacle(above) || isLavaOrFire(above)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Bookkeeping
    // ---------------------------------------------------------------------------------------------------------

    /** Records a step to {@code next}, creating its node or improving it if this route is cheaper. */
    private void relax(PathNode current, Location next, long key, PathNode existing, double stepCost,
                       MoveType moveType, List<Location> blocksToBreak) {
        double g = current.g + stepCost;
        if (existing == null) {
            PathNode node = new PathNode(next, current, g, heuristic(next), moveType);
            node.setBlocksToBreak(blocksToBreak);
            open.add(node);
            register(key, node);
        } else if (g < existing.g) {
            existing.parent = current;
            existing.g = g;
            existing.f = g + existing.h;
            existing.setBlocksToBreak(blocksToBreak);
            existing.moveType = moveType;
            existing.dirX = next.getBlockX() - current.location.getBlockX();
            existing.dirZ = next.getBlockZ() - current.location.getBlockZ();
            open.decreaseKey(existing);
        }
    }

    private void register(long key, PathNode node) {
        nodesByBlock.put(key, node);
        allNodes.add(node);
    }

    private double heuristic(Location from) {
        int dx = Math.abs(from.getBlockX() - goal.getBlockX());
        int dy = Math.abs(from.getBlockY() - goal.getBlockY());
        int dz = Math.abs(from.getBlockZ() - goal.getBlockZ());
        double estimate = dx + dz + dy * 1.5;

        if (terrain.isInsideWater(from)) {
            estimate += PathfinderConfig.WATER_COST * 2.0;
        }
        if (terrain.isInsideWater(goal)) {
            estimate += PathfinderConfig.WATER_COST * 2.0;
        }
        if (terrain.isOnWaterSurface(from)) {
            estimate += PathfinderConfig.WATER_COST;
        }
        // Tiny position-based tie-breaker so equally good nodes are expanded in a stable order.
        double tieBreaker = (from.getBlockX() * 0.001 + from.getBlockZ() * 0.0001) % 0.01;
        return estimate + tieBreaker;
    }

    /** Blocks to break for one step, in the order they were found, without duplicates. */
    private static final class BreakList {
        private List<Location> blocks;

        void add(Block block) {
            Location location = block.getLocation();
            if (blocks == null) {
                blocks = new ArrayList<>(3);
            } else if (blocks.contains(location)) {
                return;
            }
            blocks.add(location);
        }

        boolean isEmpty() {
            return blocks == null || blocks.isEmpty();
        }

        int size() {
            return blocks == null ? 0 : blocks.size();
        }

        List<Location> toList() {
            return blocks;
        }
    }
}
