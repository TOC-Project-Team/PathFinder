package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.Location;
import org.bukkit.World;
import org.momu.pathfinder.config.PathfinderConfig;
import org.momu.pathfinder.navigation.pathfinding.TerrainView.Stand;

import java.util.ArrayList;
import java.util.List;

import static org.momu.pathfinder.navigation.pathfinding.PlayerBody.*;

/**
 * A* search over block positions for a walking player.
 *
 * <p>Instead of guessing from block names or {@link org.bukkit.Material#isSolid()} (which reports pressure plates
 * and signs as solid, and carpets as not), every move pushes the player's 0.6 x 1.8 box through the real
 * collision shapes of the blocks on the way (see {@link TerrainView}). Carpets and slabs are simply walked
 * over, pressure plates walked through, stairs climbed step by step, fences and walls are too tall to step on,
 * and blocks added in newer Minecraft versions need no code change.</p>
 *
 * <p>From each node the search tries:
 * <ul>
 *     <li>walking to the 8 neighbors, stepping up ledges up to {@link PlayerBody#STEP_HEIGHT}, jumping up to
 *     {@link PlayerBody#JUMP_HEIGHT} and dropping down, see {@link #sweep}</li>
 *     <li>climbing ladders, vines and scaffolding (also jumping up to grab one), swimming up and down, and
 *     climbing or digging down, see {@link #climbUp} and {@link #descend}</li>
 *     <li>jumping across gaps to blocks at other heights, in any direction, see {@link #gapJumps}</li>
 * </ul>
 * Moves that need a block broken are only tried when walking and jumping cannot get past, and cost
 * {@code break_block_cost} per block. All costs come from {@link PathfinderConfig}. If the goal cannot be
 * reached within {@code max_iterations}, the path to the explored node closest to the goal is returned instead.
 *
 * <p>Runs off the main thread; it only reads blocks, and treats unloaded chunks as walls.</p>
 */
public final class AStarPathfinder {
    /** Largest gap (in blocks between the two blocks' edges) a sprint jump clears on flat ground. */
    private static final double MAX_FLAT_GAP = 3.0;
    /** Largest gap when landing higher or lower than the take-off: a jump of at most 3 blocks. */
    private static final double MAX_HEIGHT_CHANGE_GAP = 2.0;
    /** Height differences up to this (carpets, snow layers) still count as a flat jump. */
    private static final double FLAT_JUMP_TOLERANCE = 0.25;
    private static final int MAX_JUMP_REACH = 5;
    private static final int SWEEP_SAMPLES = 4;
    /** Extra blocks scanned below the safe fall height to find water, slime or a ladder to land in. */
    private static final int SOFT_LANDING_SCAN = 20;
    private static final double NONE = Double.NEGATIVE_INFINITY;

    private static final int[] STEP_DX = { 0, 1, -1, 0, 1, -1, 1, -1 };
    private static final int[] STEP_DZ = { 1, 0, 0, -1, 1, 1, -1, -1 };
    private static final int[][] BREAK_NEIGHBORS = {
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, -1, 0 }, { 0, 1, 0 }
    };

    private final Location start;
    private final int goalX, goalY, goalZ;
    private final World world;
    private final TerrainView terrain;
    private final IndexedOpenSet open = new IndexedOpenSet(1024);
    private final NodeIndex nodesByBlock = new NodeIndex(2048);
    private final List<PathNode> allNodes = new ArrayList<>(1024);
    private final MoveResult move = new MoveResult();
    private TerrainCell[] region = new TerrainCell[64];
    /** A solid goal (a beacon, a portal frame) is reached by standing next to or on it. */
    private boolean goalObstructed;
    private boolean goalUnderwater;

    private AStarPathfinder(Location start, Location goal) {
        this.start = start;
        this.goalX = goal.getBlockX();
        this.goalY = goal.getBlockY();
        this.goalZ = goal.getBlockZ();
        this.world = start.getWorld();
        this.terrain = new TerrainView(world);
    }

    /**
     * Finds a path between two locations in the same world.
     *
     * @return the path from start to goal (or to the closest reachable point), never empty
     */
    public static List<PathNode> findPath(Location start, Location goal) {
        if (start.getWorld() == null || !start.getWorld().equals(goal.getWorld())) {
            List<PathNode> path = new ArrayList<>(1);
            path.add(new PathNode(start.getBlock().getLocation(), null, 0, 0, MoveType.HORIZONTAL));
            return path;
        }
        return new AStarPathfinder(start, goal).search();
    }

    private List<PathNode> search() {
        int sx = start.getBlockX(), sy = start.getBlockY(), sz = start.getBlockZ();
        Stand goalStand = terrain.stand(goalX, goalY, goalZ);
        goalObstructed = !goalStand.valid;
        goalUnderwater = goalStand.valid && goalStand.is(Stand.SUBMERGED);

        Stand startStand = terrain.stand(sx, sy, sz);
        PathNode startNode = new PathNode(new Location(world, sx, sy, sz), null, 0, heuristic(sx, sy, sz),
                MoveType.HORIZONTAL);
        // Off-centre on an edge the start may not look standable; trust where the player actually is.
        startNode.feetY = startStand.valid ? startStand.feet : Math.max(sy, Math.min(start.getY(), sy + 0.99));
        startNode.kind = startStand.valid ? startStand.kind : Stand.GROUND;
        startNode.floorFlags = startStand.floorFlags;
        open.add(startNode);
        register(BlockKey.of(sx, sy, sz), startNode);

        long maxRadius = PathfinderConfig.MAX_SEARCH_RADIUS;
        long maxRadiusSquared = maxRadius * maxRadius;
        int iterations = 0;
        while (!open.isEmpty() && iterations < PathfinderConfig.MAX_ITERATIONS) {
            iterations++;
            PathNode current = open.poll();
            current.closed = true;

            if (isGoal(current)) {
                return PathPostProcessor.buildPath(current);
            }
            long dx = current.x - sx, dy = current.y - sy, dz = current.z - sz;
            if (dx * dx + dy * dy + dz * dz > maxRadiusSquared) {
                continue;
            }
            expand(current);
        }
        return PathPostProcessor.buildPath(closestToGoal());
    }

    private boolean isGoal(PathNode node) {
        if (node.x == goalX && node.y == goalY && node.z == goalZ) {
            return true;
        }
        return goalObstructed && Math.abs(node.x - goalX) <= 1 && Math.abs(node.y - goalY) <= 1
                && Math.abs(node.z - goalZ) <= 1;
    }

    private PathNode closestToGoal() {
        PathNode closest = null;
        long closestDistance = Long.MAX_VALUE;
        for (PathNode node : allNodes) {
            long dx = node.x - goalX, dy = node.y - goalY, dz = node.z - goalZ;
            long distance = dx * dx + dy * dy + dz * dz;
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = node;
            }
        }
        return closest;
    }

    private void register(long key, PathNode node) {
        nodesByBlock.put(key, node);
        allNodes.add(node);
    }

    private void expand(PathNode current) {
        for (int i = 0; i < STEP_DX.length; i++) {
            int dx = STEP_DX[i];
            int dz = STEP_DZ[i];
            if (!horizontalMove(current, dx, dz)) {
                continue;
            }
            double cost = (dx != 0 && dz != 0) ? PathfinderConfig.DIAGONAL_COST : PathfinderConfig.STRAIGHT_COST;
            if (move.moveType == MoveType.JUMP) {
                cost += PathfinderConfig.JUMP_COST;
            } else if (move.moveType == MoveType.FALL) {
                cost += fallCost(move.drop);
            }
            relax(current, cost, dx, dz);
        }

        if (climbUp(current)) {
            double cost = PathfinderConfig.STRAIGHT_COST + PathfinderConfig.VERTICAL_COST;
            if ((current.kind & (Stand.CLIMB | Stand.SWIM)) == 0) {
                cost += PathfinderConfig.JUMP_COST;
            }
            relax(current, cost, 0, 0);
        }

        if (descend(current)) {
            double cost = PathfinderConfig.STRAIGHT_COST + (move.moveType == MoveType.DOWN
                    ? PathfinderConfig.VERTICAL_COST
                    : fallCost(move.drop));
            relax(current, cost, 0, 0);
        }

        if (canStartGapJump(current)) {
            gapJumps(current);
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Costs
    // ---------------------------------------------------------------------------------------------------------

    /** Records the move in {@link #move} as a way to reach its end block, if it is cheaper than known ways. */
    private void relax(PathNode current, double moveCost, int dirX, int dirZ) {
        long key = BlockKey.of(move.x, move.y, move.z);
        PathNode existing = nodesByBlock.get(key);
        if (existing != null && existing.closed) {
            return;
        }

        moveCost += turnCost(current, dirX, dirZ) + arrivalCost();
        if (move.breakCount > 0) {
            moveCost += PathfinderConfig.BREAK_BLOCK_COST * move.breakCount;
        }
        double nextG = current.g + moveCost;

        if (existing != null) {
            if (nextG < existing.g) {
                existing.parent = current;
                existing.g = nextG;
                existing.f = nextG + existing.h;
                existing.apply(move, dirX, dirZ);
                open.decreaseKey(existing);
            }
            return;
        }

        PathNode node = new PathNode(new Location(world, move.x, move.y, move.z), current, nextG,
                heuristic(move.x, move.y, move.z), move.moveType);
        node.apply(move, dirX, dirZ);
        open.add(node);
        register(key, node);
    }

    private static double turnCost(PathNode current, int dirX, int dirZ) {
        if (current.parent == null || (dirX == 0 && dirZ == 0)) {
            return 0.0;
        }
        int prevDirX = current.dirX;
        int prevDirZ = current.dirZ;
        if ((prevDirX == 0 && prevDirZ == 0) || (dirX == prevDirX && dirZ == prevDirZ)) {
            return 0.0;
        }
        if (dirX * prevDirX + dirZ * prevDirZ == 0) {
            return PathfinderConfig.RIGHT_ANGLE_TURN_COST;
        }
        if ((dirX != 0 && dirZ != 0) != (prevDirX != 0 && prevDirZ != 0)) {
            return PathfinderConfig.DIAGONAL_TURN_COST;
        }
        return 0.0;
    }

    /** Costs for where {@link #move} ends up: water, doors, trapdoors, scaffolding and slow floors. */
    private double arrivalCost() {
        double cost = 0.0;
        if ((move.kind & Stand.SWIM) != 0) {
            cost += (move.kind & Stand.SUBMERGED) != 0
                    ? PathfinderConfig.WATER_COST * 5.0
                    : PathfinderConfig.WATER_COST;
        }
        TerrainCell feet = terrain.cell(move.x, move.y, move.z);
        TerrainCell head = terrain.cell(move.x, move.y + 1, move.z);
        if (move.passedDoor || feet.has(BlockTypes.DOOR | BlockTypes.FENCE_GATE)
                || head.has(BlockTypes.DOOR | BlockTypes.FENCE_GATE)) {
            cost += PathfinderConfig.DOOR_COST;
        }
        if (feet.has(BlockTypes.TRAPDOOR)) {
            cost += PathfinderConfig.TRAPDOOR_COST;
        }
        if (head.has(BlockTypes.TRAPDOOR)) {
            cost += PathfinderConfig.TRAPDOOR_COST;
        }
        if (move.passedTrapdoor && !feet.has(BlockTypes.TRAPDOOR) && !head.has(BlockTypes.TRAPDOOR)) {
            cost += PathfinderConfig.TRAPDOOR_COST;
        }
        if (feet.has(BlockTypes.PLATFORM)) {
            cost += PathfinderConfig.SCAFFOLDING_COST;
        }
        if ((move.floorFlags & BlockTypes.SLOW) != 0) {
            cost += PathfinderConfig.STRAIGHT_COST;
        }
        return cost;
    }

    private static double fallCost(double drop) {
        double cost = PathfinderConfig.FALL_COST;
        int blocks = (int) Math.ceil(drop - EPS);
        if (blocks > 1) {
            cost += (blocks - 1) * 0.1;
        }
        return cost;
    }

    private double heuristic(int x, int y, int z) {
        double estimate = Math.abs(x - goalX) + Math.abs(z - goalZ) + Math.abs(y - goalY) * 1.5;
        if (terrain.cell(x, y, z).water) {
            estimate += terrain.cell(x, y + 1, z).water
                    ? PathfinderConfig.WATER_COST * 2.0
                    : PathfinderConfig.WATER_COST;
        }
        if (goalUnderwater) {
            estimate += PathfinderConfig.WATER_COST * 2.0;
        }
        // Tiny position-based tie breaker so equal-cost routes are explored in a stable order.
        return estimate + (x * 0.001 + z * 0.0001) % 0.01;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Walking, stepping, jumping up and dropping down
    // ---------------------------------------------------------------------------------------------------------

    /** Height the player can rise while moving sideways out of the node. */
    private static double riseReach(PathNode node) {
        if ((node.kind & (Stand.SWIM | Stand.CLIMB)) != 0) {
            return JUMP_HEIGHT;
        }
        if ((node.kind & Stand.GROUND) != 0 && (node.floorFlags & BlockTypes.NO_JUMP) == 0) {
            return JUMP_HEIGHT;
        }
        return STEP_HEIGHT;
    }

    private boolean horizontalMove(PathNode from, int dx, int dz) {
        double reach = riseReach(from);
        if (sweep(from, dx, dz, STEP_HEIGHT, false)) {
            return true;
        }
        if (move.status != MoveResult.Status.BLOCKED) {
            return false;
        }
        if (reach > STEP_HEIGHT && sweep(from, dx, dz, reach, false)) {
            return true;
        }
        // Breaking is the last resort, only when walking or jumping cannot get past the obstacle.
        if (sweep(from, dx, dz, STEP_HEIGHT, true)) {
            return true;
        }
        return reach > STEP_HEIGHT && sweep(from, dx, dz, reach, true);
    }

    private boolean sweep(PathNode from, int dx, int dz, double maxRise, boolean allowBreak) {
        move.reset();
        while (true) {
            if (sweepOnce(from, dx, dz, maxRise)) {
                return true;
            }
            if (move.status != MoveResult.Status.BLOCKED || !allowBreak || !tryBreak(from)) {
                return false;
            }
        }
    }

    /** Adds the block {@link #move} is blocked by to the blocks it breaks, if that block may be broken. */
    private boolean tryBreak(PathNode from) {
        if (move.breakCount >= MoveResult.MAX_BREAKS) {
            return false;
        }
        int x = move.blockX, y = move.blockY, z = move.blockZ;
        TerrainCell cell = terrain.cell(x, y, z, from, move);
        if (cell.boxes.length == 0 || cell.has(BlockTypes.UNBREAKABLE | BlockTypes.PLATFORM | BlockTypes.OPENABLE)) {
            return false;
        }
        for (int[] offset : BREAK_NEIGHBORS) {
            if (terrain.cell(x + offset[0], y + offset[1], z + offset[2]).has(BlockTypes.LAVA)) {
                return false;
            }
        }
        move.breaks[move.breakCount++] = BlockKey.of(x, y, z);
        return true;
    }

    /**
     * Moves the player's box from the centre of the current block to the centre of the neighboring column,
     * stepping up ledges (up to {@code maxRise}) and following the floor down, then drops it onto whatever is
     * below.
     */
    private boolean sweepOnce(PathNode from, int dx, int dz, double maxRise) {
        int ax = from.x, az = from.z;
        int bx = ax + dx, bz = az + dz;
        double startFeet = from.feetY;
        boolean jumping = maxRise > STEP_HEIGHT + EPS;

        // Collect every block the box can touch on the way once.
        int xLo = Math.min(ax, bx), zLo = Math.min(az, bz);
        int xSize = Math.abs(dx) + 1, zSize = Math.abs(dz) + 1;
        int yLo = (int) Math.floor(startFeet) - 2;
        // Walking up stairs can rise a full block within one move, so leave room above the reach.
        int ySize = (int) Math.floor(startFeet + Math.max(maxRise, JUMP_HEIGHT) + HEIGHT) - yLo + 2;
        if (region.length < xSize * zSize * ySize) {
            region = new TerrainCell[xSize * zSize * ySize];
        }
        for (int ix = 0; ix < xSize; ix++) {
            for (int iz = 0; iz < zSize; iz++) {
                for (int iy = 0; iy < ySize; iy++) {
                    region[(ix * zSize + iz) * ySize + iy] = terrain.cell(xLo + ix, yLo + iy, zLo + iz, from, move);
                }
            }
        }

        double feet = startFeet;
        double peak = startFeet;
        boolean airborne = false;
        move.passedDoor = false;
        move.passedTrapdoor = false;

        for (int sample = 0; sample <= SWEEP_SAMPLES; sample++) {
            double t = (double) sample / SWEEP_SAMPLES;
            double px = ax + 0.5 + dx * t;
            double pz = az + 0.5 + dz * t;

            // Step up onto whatever the box runs into, as long as it is low enough.
            for (int guard = 0; ; guard++) {
                double stepTop = NONE;
                for (int ix = 0; ix < xSize; ix++) {
                    for (int iz = 0; iz < zSize; iz++) {
                        int cellX = xLo + ix, cellZ = zLo + iz;
                        if (!cellOverlapsFootprint(cellX, cellZ, px, pz)) {
                            continue;
                        }
                        for (int iy = 0; iy < ySize; iy++) {
                            TerrainCell c = region[(ix * zSize + iz) * ySize + iy];
                            int cellY = yLo + iy;
                            boolean bodyInCell = cellY + 1 > feet + EPS && cellY < feet + HEIGHT - EPS;
                            if (bodyInCell && c.has(BlockTypes.BODY_HAZARD)) {
                                return move.failed();
                            }
                            if (c.has(BlockTypes.PLATFORM)) {
                                continue;
                            }
                            boolean openable = c.has(BlockTypes.OPENABLE);
                            double[] boxes = c.boxes;
                            for (int k = 0; k < boxes.length; k += 6) {
                                double top = cellY + boxes[k + 4];
                                if (top <= feet + EPS || cellY + boxes[k + 1] >= feet + HEIGHT - EPS
                                        || !overlapsFootprint(boxes, k, cellX, cellZ, px, pz)) {
                                    continue;
                                }
                                double rise = top - feet;
                                if (openable) {
                                    // Walk onto a low closed trapdoor, open everything else on the way.
                                    if (rise <= STEP_HEIGHT + EPS && !airborne) {
                                        stepTop = Math.max(stepTop, top);
                                    } else if (c.has(BlockTypes.TRAPDOOR)) {
                                        move.passedTrapdoor = true;
                                    } else {
                                        move.passedDoor = true;
                                    }
                                    continue;
                                }
                                boolean canRise = jumping
                                        ? top - startFeet <= maxRise + EPS
                                        : !airborne && rise <= STEP_HEIGHT + EPS;
                                if (!canRise) {
                                    return move.blocked(cellX, cellY, cellZ);
                                }
                                stepTop = Math.max(stepTop, top);
                            }
                        }
                    }
                }
                if (stepTop <= feet + EPS) {
                    break;
                }
                if (guard >= 4) {
                    return move.failed();
                }
                feet = stepTop;
                peak = Math.max(peak, feet);
                airborne = false;
                if (feet + HEIGHT > yLo + ySize) {
                    return move.failed();
                }
            }

            // Follow the floor down as long as it only drops by a step.
            double support = NONE;
            for (int ix = 0; ix < xSize; ix++) {
                for (int iz = 0; iz < zSize; iz++) {
                    int cellX = xLo + ix, cellZ = zLo + iz;
                    if (!cellOverlapsFootprint(cellX, cellZ, px, pz)) {
                        continue;
                    }
                    for (int iy = 0; iy < ySize; iy++) {
                        support = region[(ix * zSize + iz) * ySize + iy].highestTop(cellX, yLo + iy, cellZ,
                                px, pz, feet - STEP_HEIGHT - EPS, feet + EPS, support);
                    }
                }
            }
            if (support > NONE) {
                feet = support;
                airborne = false;
            } else {
                airborne = true;
            }
        }

        // Walking up stairs rises in half steps; only a real jump needs to go straight up first.
        boolean jumped = jumping && peak - startFeet > STEP_HEIGHT + EPS;
        if (jumped && !hasHeadroom(from, ax, az, startFeet, peak)) {
            return false;
        }
        // A fall is measured from where the player left the floor (or from the top of the jump).
        return land(from, bx, bz, feet, jumped ? Math.max(peak, feet) : feet, jumped);
    }

    /** Whether the player can rise from {@code startFeet} to {@code peak} in their own column. */
    private boolean hasHeadroom(PathNode from, int x, int z, double startFeet, double peak) {
        double px = x + 0.5, pz = z + 0.5;
        int topY = (int) Math.floor(peak + HEIGHT - EPS);
        for (int cellY = (int) Math.floor(startFeet); cellY <= topY; cellY++) {
            TerrainCell c = terrain.cell(x, cellY, z, from, move);
            if (c.has(BlockTypes.PLATFORM | BlockTypes.OPENABLE)) {
                continue;
            }
            if (c.has(BlockTypes.BODY_HAZARD)) {
                return move.failed();
            }
            double[] boxes = c.boxes;
            for (int k = 0; k < boxes.length; k += 6) {
                if (cellY + boxes[k + 4] <= startFeet + EPS || cellY + boxes[k + 1] >= peak + HEIGHT - EPS
                        || !overlapsFootprint(boxes, k, x, z, px, pz)) {
                    continue;
                }
                return move.blocked(x, cellY, z);
            }
        }
        return true;
    }

    /**
     * Lets the player drop down the column at (x, z) from {@code feet} and records where they end up: on the
     * first floor, in water, or on a ladder, vine or scaffolding that catches them.
     */
    private boolean land(PathNode from, int x, int z, double feet, double fallStart, boolean jumped) {
        double px = x + 0.5, pz = z + 0.5;
        int maxScan = Math.max(0, PathfinderConfig.MAX_SAFE_FALL_HEIGHT) + SOFT_LANDING_SCAN + 2;
        int cellY = (int) Math.floor(feet + EPS);
        double landing = NONE;
        boolean caught = false;

        for (int scanned = 0; scanned <= maxScan; scanned++, cellY--) {
            if (cellY <= terrain.minY) {
                return move.failed();
            }
            TerrainCell c = terrain.cell(x, cellY, z, from, move);
            TerrainCell below = terrain.cell(x, cellY - 1, z, from, move);
            double top = c.highestTop(x, cellY, z, px, pz, cellY - EPS, feet + EPS, NONE);
            top = below.highestTop(x, cellY - 1, z, px, pz, cellY - EPS, feet + EPS, top);
            if (top > NONE) {
                landing = top;
                break;
            }
            if (c.has(BlockTypes.BODY_HAZARD)) {
                return move.failed();
            }
            if (c.water || c.has(BlockTypes.CLIMBABLE)) {
                landing = cellY;
                caught = true;
                break;
            }
        }
        if (landing == NONE) {
            return move.failed();
        }

        int landY = caught ? cellY : (int) Math.floor(landing + EPS);
        Stand stand = terrain.stand(x, landY, z, from, move);
        if (!stand.valid || Math.abs(stand.feet - landing) > 0.05) {
            return move.failed();
        }

        double drop = fallStart - stand.feet;
        if (drop > STEP_HEIGHT + EPS && !caught && !stand.is(Stand.SWIM | Stand.CLIMB)
                && drop > safeFallHeight(stand.floorFlags) + EPS) {
            return move.failed();
        }

        MoveType moveType;
        if (jumped) {
            moveType = MoveType.JUMP;
        } else if (drop > STEP_HEIGHT + EPS) {
            // Stepping off a ledge onto a ladder or vine is climbing down, not falling.
            moveType = caught && stand.is(Stand.CLIMB) ? MoveType.DOWN : MoveType.FALL;
        } else {
            moveType = MoveType.HORIZONTAL;
        }
        return move.land(x, landY, z, stand, moveType, Math.max(0.0, drop));
    }

    /** Largest fall (in blocks) that is acceptable when landing on a block with the given flags. */
    private static double safeFallHeight(int floorFlags) {
        double safe = Math.max(0, PathfinderConfig.MAX_SAFE_FALL_HEIGHT);
        if ((floorFlags & BlockTypes.NO_FALL_DAMAGE) != 0) {
            return safe + SOFT_LANDING_SCAN;
        }
        // Fall damage is (distance - 3) scaled by the block's damage multiplier.
        double multiplier = (floorFlags & BlockTypes.FALL_DAMAGE_20) != 0 ? 0.2
                : (floorFlags & BlockTypes.FALL_DAMAGE_50) != 0 ? 0.5 : 1.0;
        if (multiplier < 1.0 && safe > 3.0) {
            return Math.max(safe, 3.0 + (safe - 3.0) / multiplier);
        }
        return safe;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Climbing, swimming and digging in the same column
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Climbing a ladder, vine or scaffolding, swimming up, stepping out on top of scaffolding or onto the top of
     * a ladder, or jumping up from the ground to grab a ladder or vine that starts above the player's feet.
     */
    private boolean climbUp(PathNode from) {
        move.reset();
        boolean holding = (from.kind & (Stand.CLIMB | Stand.SWIM)) != 0;
        boolean canJump = (from.kind & Stand.GROUND) != 0 && (from.floorFlags & BlockTypes.NO_JUMP) == 0;
        if (!holding && !canJump) {
            return move.failed();
        }
        int x = from.x, y = from.y + 1, z = from.z;
        while (true) {
            Stand stand = terrain.stand(x, y, z, from, move);
            if (stand.valid) {
                if (stand.feet <= from.feetY + EPS) {
                    return move.failed();
                }
                // From the ground, a jump only gets the feet into a climbable block, it is not a free ride up.
                if (!holding && (!stand.is(Stand.CLIMB) || stand.feet - from.feetY > JUMP_HEIGHT + EPS)) {
                    return move.failed();
                }
                return move.land(x, y, z, stand, MoveType.UP, 0.0);
            }
            if (!stand.isBlocked()) {
                return move.failed();
            }
            move.blocked(stand.obstacleX, stand.obstacleY, stand.obstacleZ);
            if (!tryBreak(from)) {
                return false;
            }
        }
    }

    /** Climbing or swimming down, sneaking down scaffolding, or digging through the floor. */
    private boolean descend(PathNode from) {
        move.reset();
        int x = from.x, z = from.z;
        double px = x + 0.5, pz = z + 0.5;
        double feet = from.feetY;

        // Anything solid right under the feet has to be dug through first.
        for (int attempt = 0; ; attempt++) {
            int solidY = Integer.MIN_VALUE;
            for (int cellY = from.y - 1; cellY <= from.y && solidY == Integer.MIN_VALUE; cellY++) {
                TerrainCell c = terrain.cell(x, cellY, z, from, move);
                if (c.has(BlockTypes.PLATFORM | BlockTypes.OPENABLE)) {
                    continue;
                }
                if (c.highestTop(x, cellY, z, px, pz, feet - EPS, feet + EPS, NONE) > NONE) {
                    solidY = cellY;
                }
            }
            if (solidY == Integer.MIN_VALUE) {
                break;
            }
            move.blocked(x, solidY, z);
            if (attempt > 0 || !tryBreak(from)) {
                return false;
            }
        }

        double below = feet - 0.01;
        double landing = NONE;
        int cellY = (int) Math.floor(below);
        boolean caught = false;
        int maxScan = Math.max(0, PathfinderConfig.MAX_SAFE_FALL_HEIGHT) + SOFT_LANDING_SCAN + 2;
        for (int scanned = 0; scanned <= maxScan; scanned++, cellY--) {
            if (cellY <= terrain.minY) {
                return move.failed();
            }
            TerrainCell c = terrain.cell(x, cellY, z, from, move);
            TerrainCell under = terrain.cell(x, cellY - 1, z, from, move);
            double top = c.highestTop(x, cellY, z, px, pz, cellY - EPS, below, NONE);
            top = under.highestTop(x, cellY - 1, z, px, pz, cellY - EPS, below, top);
            if (top > NONE) {
                landing = top;
                break;
            }
            if (cellY < from.y && c.has(BlockTypes.BODY_HAZARD)) {
                return move.failed();
            }
            if (cellY < from.y && (c.water || c.has(BlockTypes.CLIMBABLE))) {
                landing = cellY;
                caught = true;
                break;
            }
        }
        if (landing == NONE) {
            return move.failed();
        }
        int landY = caught ? cellY : (int) Math.floor(landing + EPS);
        if (landY >= from.y) {
            return move.failed();
        }
        Stand stand = terrain.stand(x, landY, z, from, move);
        if (!stand.valid || Math.abs(stand.feet - landing) > 0.05) {
            return move.failed();
        }
        double drop = feet - stand.feet;
        boolean held = caught || ((from.kind | stand.kind) & (Stand.SWIM | Stand.CLIMB)) != 0;
        if (!held && drop > safeFallHeight(stand.floorFlags) + EPS) {
            return move.failed();
        }
        return move.land(x, landY, z, stand, held ? MoveType.DOWN : MoveType.FALL, drop);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Gap jumps (simple parkour)
    // ---------------------------------------------------------------------------------------------------------

    private static boolean canStartGapJump(PathNode node) {
        return PathfinderConfig.MAX_BLOCK_JUMP_DISTANCE >= 2
                && (node.kind & Stand.GROUND) != 0
                && (node.kind & Stand.SWIM) == 0
                && (node.floorFlags & (BlockTypes.NO_JUMP | BlockTypes.SLOW)) == 0;
    }

    private void gapJumps(PathNode current) {
        int reach = Math.min(PathfinderConfig.MAX_BLOCK_JUMP_DISTANCE, MAX_JUMP_REACH);
        int minY = current.y - Math.max(0, PathfinderConfig.MAX_SAFE_FALL_HEIGHT) - 1;

        // Jumps start at an edge: the block right next to the player in the jump direction must not be
        // walkable (a gap, a drop or an obstacle). Most positions are nowhere near an edge.
        boolean[] edge = new boolean[9];
        boolean anyEdge = false;
        for (int sx = -1; sx <= 1; sx++) {
            for (int sz = -1; sz <= 1; sz++) {
                if ((sx != 0 || sz != 0) && !hasWalkingFloor(current.x + sx, current.z + sz, current)) {
                    edge[(sx + 1) * 3 + sz + 1] = true;
                    anyEdge = true;
                }
            }
        }
        if (!anyEdge) {
            return;
        }

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int steps = Math.max(Math.abs(dx), Math.abs(dz));
                if (steps < 2) {
                    continue;
                }
                int firstX = (int) Math.floor((double) dx / steps + 0.5);
                int firstZ = (int) Math.floor((double) dz / steps + 0.5);
                if (!edge[(firstX + 1) * 3 + firstZ + 1]) {
                    continue;
                }
                double gap = Math.hypot(Math.max(0, Math.abs(dx) - 1), Math.max(0, Math.abs(dz) - 1));
                if (gap > MAX_FLAT_GAP + EPS) {
                    continue;
                }
                int tx = current.x + dx, tz = current.z + dz;
                for (int ty = current.y + 1; ty >= minY; ty--) {
                    Stand stand = terrain.stand(tx, ty, tz);
                    if (!stand.valid || !stand.is(Stand.GROUND) || stand.is(Stand.SWIM)) {
                        continue;
                    }
                    double rise = stand.feet - current.feetY;
                    double maxGap = Math.abs(rise) > FLAT_JUMP_TOLERANCE ? MAX_HEIGHT_CHANGE_GAP : MAX_FLAT_GAP;
                    if (rise > 1.0 + EPS || gap > maxGap + EPS) {
                        continue;
                    }
                    double drop = -rise;
                    if (drop > safeFallHeight(stand.floorFlags) + EPS || !jumpArcClear(current, tx, tz, stand.feet)) {
                        continue;
                    }
                    move.reset();
                    move.land(tx, ty, tz, stand, MoveType.BLOCK_JUMP, Math.max(0.0, drop));
                    double distance = Math.hypot(dx, dz);
                    double cost = PathfinderConfig.BLOCK_JUMP_COST * distance * distance;
                    if (drop > STEP_HEIGHT + EPS) {
                        cost += PathfinderConfig.FALL_COST;
                    }
                    relax(current, cost, Integer.signum(dx), Integer.signum(dz));
                }
            }
        }
    }

    private boolean hasWalkingFloor(int x, int z, PathNode from) {
        for (int y = from.y - 1; y <= from.y + 1; y++) {
            Stand stand = terrain.stand(x, y, z);
            if (stand.valid && stand.is(Stand.GROUND) && !stand.is(Stand.SWIM)
                    && Math.abs(stand.feet - from.feetY) <= STEP_HEIGHT + EPS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Follows a jump arc (apex {@link PlayerBody#JUMP_HEIGHT} above the take-off) from the current block to the
     * landing block and makes sure the player's box never touches a block, hazard or water on the way.
     */
    private boolean jumpArcClear(PathNode from, int tx, int tz, double landFeet) {
        double sx = from.x + 0.5, sz = from.z + 0.5;
        double ddx = tx - from.x, ddz = tz - from.z;
        double rise = landFeet - from.feetY;
        double apex = JUMP_HEIGHT;
        // y(t) = start + a*t - b*t^2 with y(1) = start + rise and a peak of start + apex.
        double a = 2.0 * apex + 2.0 * Math.sqrt(Math.max(0.0, apex * apex - apex * rise));
        double b = a - rise;
        int samples = Math.max(8, (int) Math.ceil(Math.hypot(ddx, ddz) * 6.0));

        for (int sample = 1; sample < samples; sample++) {
            double t = (double) sample / samples;
            double px = sx + ddx * t, pz = sz + ddz * t;
            double feet = from.feetY + a * t - b * t * t;
            int x0 = (int) Math.floor(px - HALF_WIDTH + EPS), x1 = (int) Math.floor(px + HALF_WIDTH - EPS);
            int z0 = (int) Math.floor(pz - HALF_WIDTH + EPS), z1 = (int) Math.floor(pz + HALF_WIDTH - EPS);
            int y0 = (int) Math.floor(feet + EPS) - 1, y1 = (int) Math.floor(feet + HEIGHT - EPS);
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = y0; y <= y1; y++) {
                        TerrainCell c = terrain.cell(x, y, z, from, null);
                        boolean bodyInCell = y + 1 > feet + EPS && y < feet + HEIGHT - EPS;
                        if (bodyInCell && (c.water || c.has(BlockTypes.BODY_HAZARD))) {
                            return false;
                        }
                        if (c.has(BlockTypes.PLATFORM)) {
                            continue;
                        }
                        double[] boxes = c.boxes;
                        for (int k = 0; k < boxes.length; k += 6) {
                            if (y + boxes[k + 4] <= feet + EPS || y + boxes[k + 1] >= feet + HEIGHT - EPS
                                    || !overlapsFootprint(boxes, k, x, z, px, pz)) {
                                continue;
                            }
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}
