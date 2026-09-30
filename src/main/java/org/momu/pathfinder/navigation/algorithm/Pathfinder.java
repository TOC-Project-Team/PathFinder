package org.momu.pathfinder.navigation.algorithm;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.momu.pathfinder.bootstrap.PathFinderPlugin;
import org.momu.pathfinder.config.PathfinderConfig;

import java.util.*;
import java.util.function.Supplier;

/**
 * A* search over the block grid that moves a player-sized box (0.6 x 1.8) through the world.
 *
 * <p>Instead of guessing from block names or {@link Material#isSolid()} (which reports pressure
 * plates and signs as solid, and carpets as not), every block is read through its real collision
 * shape. That way carpets and slabs are simply walked over, pressure plates are walked through,
 * stairs are climbed step by step, fences and walls are too tall to step on, and blocks added in
 * newer Minecraft versions are handled without a code change. On top of that geometry the search
 * knows about climbing (ladders, every kind of vine, scaffolding), swimming, doors the player can
 * open, hazards and gap jumps between blocks.</p>
 */
public class Pathfinder {

    public static void loadConfig(PathFinderPlugin plugin) {
        PathfinderConfig.loadConfig(plugin);
    }

    public static void clearBannerCache() {
        // Material classifications are immutable for the lifetime of the server.
    }

    public static int getMaxSearchRadius() {
        return PathfinderConfig.MAX_SEARCH_RADIUS;
    }

    public static final int MOVE_HORIZONTAL = 0;
    public static final int MOVE_UP = 1;
    public static final int MOVE_DOWN = 2;
    public static final int MOVE_JUMP = 3;
    public static final int MOVE_FALL = 4;
    public static final int MOVE_BLOCK_JUMP = 5;
    public static final int MOVE_WATER_SURFACE = 6;

    // ---------------------------------------------------------------------------------------------
    // Player physics (vanilla values)
    // ---------------------------------------------------------------------------------------------

    private static final double EPS = 1.0E-4;
    private static final double HALF_WIDTH = 0.3;
    private static final double PLAYER_HEIGHT = 1.8;
    /** Ledges up to this height are walked onto without jumping (slabs, stairs, carpets, snow...). */
    private static final double STEP_HEIGHT = 0.6;
    /**
     * Highest ledge reachable with a jump (the apex is ~1.2522 blocks). The same reach applies when
     * leaving the top of a ladder, vine or scaffolding, or when climbing out of water.
     */
    private static final double JUMP_HEIGHT = 1.25;
    /** Largest gap (in blocks between the two blocks' edges) a sprint jump clears on flat ground. */
    private static final double MAX_FLAT_GAP = 3.0;
    /** Largest gap when landing up to half a block higher. */
    private static final double MAX_HALF_UP_GAP = 2.5;
    /** Largest gap when landing a full block higher. */
    private static final double MAX_FULL_UP_GAP = 2.0;
    private static final int MAX_PARKOUR_REACH = 5;
    private static final int SWEEP_SAMPLES = 4;
    private static final int MAX_BREAKS_PER_MOVE = 3;
    /** Extra blocks scanned below the safe fall height to find water, slime or a ladder to land in. */
    private static final int SOFT_LANDING_SCAN = 20;
    private static final double NEG = Double.NEGATIVE_INFINITY;

    private static final int[] MOVE_DX = { 0, 1, -1, 0, 1, -1, 1, -1 };
    private static final int[] MOVE_DZ = { 1, 0, 0, -1, 1, 1, -1, -1 };
    private static final int[][] LAVA_NEIGHBOURS = {
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, -1, 0 }
    };
    private static final int[][] BREAK_NEIGHBOURS = {
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, -1, 0 }, { 0, 1, 0 }
    };

    // ---------------------------------------------------------------------------------------------
    // Material classification
    // ---------------------------------------------------------------------------------------------

    private static final int M_KNOWN = 1;
    private static final int M_AIR = 1 << 1;
    private static final int M_WATER = 1 << 2;
    private static final int M_LAVA = 1 << 3;
    private static final int M_CLIMBABLE = 1 << 4;
    /** Scaffolding: solid when standing on top of it, walk-through and climbable from inside. */
    private static final int M_PLATFORM = 1 << 5;
    /** Doors, trapdoors and fence gates the player can open by hand. */
    private static final int M_OPENABLE = 1 << 6;
    private static final int M_DOOR = 1 << 7;
    private static final int M_TRAPDOOR = 1 << 8;
    private static final int M_FENCE_GATE = 1 << 9;
    /** Hurts, traps or teleports the player when the body is inside it. */
    private static final int M_BODY_HAZARD = 1 << 10;
    /** Hurts or drops the player when stood on. */
    private static final int M_FLOOR_HAZARD = 1 << 11;
    private static final int M_NO_JUMP = 1 << 12;
    private static final int M_SLOW = 1 << 13;
    private static final int M_UNBREAKABLE = 1 << 14;
    private static final int M_NO_FALL_DAMAGE = 1 << 15;
    private static final int M_FALL_DAMAGE_20 = 1 << 16;
    private static final int M_FALL_DAMAGE_50 = 1 << 17;
    private static final int M_LADDER = 1 << 18;
    private static final int M_BANNER = 1 << 19;

    private static final Material[] MATERIALS = Material.values();
    private static final int[] MATERIAL_FLAGS = new int[MATERIALS.length];
    /** 0 = unknown, 1 = cannot be waterlogged, 2 = can be waterlogged. */
    private static final byte[] WATERLOGGABLE = new byte[MATERIALS.length];

    private static final double[] EMPTY_BOXES = new double[0];
    private static final double[] FULL_BOX = { 0, 0, 0, 1, 1, 1 };

    private static int materialFlags(Material material) {
        int ordinal = material.ordinal();
        int flags = MATERIAL_FLAGS[ordinal];
        if (flags == 0) {
            flags = classify(material);
            MATERIAL_FLAGS[ordinal] = flags;
        }
        return flags;
    }

    private static int classify(Material material) {
        String name = material.name();
        int flags = M_KNOWN;
        if (name.equals("AIR") || name.equals("CAVE_AIR") || name.equals("VOID_AIR")) {
            return flags | M_AIR;
        }

        if (name.equals("WATER") || name.equals("BUBBLE_COLUMN") || name.equals("KELP")
                || name.equals("KELP_PLANT") || name.equals("SEAGRASS") || name.equals("TALL_SEAGRASS")) {
            flags |= M_WATER;
        }
        if (name.equals("LAVA") || name.equals("LAVA_CAULDRON")) {
            flags |= M_LAVA | M_BODY_HAZARD | M_FLOOR_HAZARD;
        }

        if (isTagged(() -> Tag.CLIMBABLE, material) || name.equals("LADDER") || name.equals("VINE")
                || name.endsWith("_VINES") || name.endsWith("_VINES_PLANT") || name.equals("SCAFFOLDING")) {
            flags |= M_CLIMBABLE;
        }
        if (name.equals("LADDER")) {
            flags |= M_LADDER;
        }
        if (name.equals("SCAFFOLDING")) {
            flags |= M_PLATFORM;
        }

        boolean trapdoor = isTagged(() -> Tag.TRAPDOORS, material) || name.endsWith("TRAPDOOR");
        boolean door = !trapdoor && (isTagged(() -> Tag.DOORS, material) || name.endsWith("_DOOR"));
        boolean gate = isTagged(() -> Tag.FENCE_GATES, material) || name.endsWith("FENCE_GATE");
        if (trapdoor) {
            flags |= M_TRAPDOOR;
        }
        if (door) {
            flags |= M_DOOR;
        }
        if (gate) {
            flags |= M_FENCE_GATE;
        }
        if ((trapdoor || door || gate) && !name.startsWith("IRON_")) {
            flags |= M_OPENABLE;
        }
        if (name.contains("BANNER")) {
            flags |= M_BANNER;
        }

        if (isTagged(() -> Tag.FIRE, material) || name.equals("FIRE") || name.equals("SOUL_FIRE")) {
            flags |= M_BODY_HAZARD;
        }
        if (isTagged(() -> Tag.CAMPFIRES, material) || name.endsWith("CAMPFIRE")) {
            flags |= M_BODY_HAZARD | M_FLOOR_HAZARD;
        }
        if (isTagged(() -> Tag.PORTALS, material) || name.equals("NETHER_PORTAL") || name.equals("END_PORTAL")
                || name.equals("END_GATEWAY")) {
            flags |= M_BODY_HAZARD;
        }
        switch (name) {
            case "CACTUS":
                flags |= M_BODY_HAZARD | M_FLOOR_HAZARD;
                break;
            case "SWEET_BERRY_BUSH":
            case "WITHER_ROSE":
            case "COBWEB":
            case "POWDER_SNOW":
                flags |= M_BODY_HAZARD;
                break;
            case "MAGMA_BLOCK":
            case "POINTED_DRIPSTONE":
            case "BIG_DRIPLEAF":
                flags |= M_FLOOR_HAZARD;
                break;
            case "HONEY_BLOCK":
                flags |= M_NO_JUMP | M_SLOW | M_FALL_DAMAGE_20;
                break;
            case "SOUL_SAND":
                flags |= M_SLOW;
                break;
            case "SLIME_BLOCK":
                flags |= M_NO_FALL_DAMAGE;
                break;
            case "HAY_BLOCK":
                flags |= M_FALL_DAMAGE_20;
                break;
            default:
                break;
        }
        if (isTagged(() -> Tag.BEDS, material) || name.endsWith("_BED")) {
            flags |= M_FALL_DAMAGE_50;
        }

        float hardness;
        try {
            hardness = material.getHardness();
        } catch (Throwable ignored) {
            hardness = 1.0f;
        }
        if (hardness < 0 || hardness >= 50 || trapdoor || door || gate
                || name.equals("BEDROCK") || name.equals("BARRIER") || name.equals("LIGHT")
                || name.endsWith("SPAWNER") || name.equals("VAULT") || name.equals("END_PORTAL_FRAME")
                || name.contains("COMMAND_BLOCK") || name.equals("STRUCTURE_BLOCK") || name.equals("JIGSAW")
                || name.equals("REINFORCED_DEEPSLATE") || (flags & M_BODY_HAZARD) != 0) {
            flags |= M_UNBREAKABLE;
        }
        return flags;
    }

    /** Tag lookups are deferred so a tag missing from a newer or older server cannot break classification. */
    private static boolean isTagged(Supplier<Tag<Material>> tagSupplier, Material material) {
        try {
            Tag<Material> tag = tagSupplier.get();
            return tag != null && tag.isTagged(material);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isWaterlogged(Block block, Material type) {
        int ordinal = type.ordinal();
        byte known = WATERLOGGABLE[ordinal];
        if (known == 1) {
            return false;
        }
        BlockData data = block.getBlockData();
        boolean waterloggable = data instanceof Waterlogged;
        WATERLOGGABLE[ordinal] = waterloggable ? (byte) 2 : (byte) 1;
        return waterloggable && ((Waterlogged) data).isWaterlogged();
    }

    private static double[] collisionBoxes(Block block) {
        try {
            Collection<BoundingBox> shape = block.getCollisionShape().getBoundingBoxes();
            if (shape.isEmpty()) {
                return EMPTY_BOXES;
            }
            double[] boxes = new double[shape.size() * 6];
            int i = 0;
            for (BoundingBox box : shape) {
                boxes[i++] = box.getMinX();
                boxes[i++] = box.getMinY();
                boxes[i++] = box.getMinZ();
                boxes[i++] = box.getMaxX();
                boxes[i++] = box.getMaxY();
                boxes[i++] = box.getMaxZ();
            }
            return boxes;
        } catch (Throwable ignored) {
            return block.isPassable() ? EMPTY_BOXES : FULL_BOX;
        }
    }

    /** What the search knows about one block position. */
    private static final class Cell {
        private static final Cell AIR = new Cell(M_KNOWN | M_AIR, EMPTY_BOXES, false);
        /** Below the world: nothing to stand on and deadly to fall into. */
        private static final Cell VOID = new Cell(M_KNOWN | M_AIR | M_BODY_HAZARD, EMPTY_BOXES, false);
        /** Chunk not loaded: treated as a wall instead of loading it from the search thread. */
        private static final Cell UNLOADED = new Cell(M_KNOWN | M_UNBREAKABLE, FULL_BOX, false);

        private final int flags;
        /** Collision boxes relative to the block origin: minX, minY, minZ, maxX, maxY, maxZ per box. */
        private final double[] boxes;
        private final boolean water;

        private Cell(int flags, double[] boxes, boolean water) {
            this.flags = flags;
            this.boxes = boxes;
            this.water = water;
        }

        private boolean has(int flag) {
            return (flags & flag) != 0;
        }
    }

    private static Cell loadCell(SearchContext ctx, int x, int y, int z) {
        if (y < ctx.minY) {
            return Cell.VOID;
        }
        if (y >= ctx.maxY) {
            return Cell.AIR;
        }
        if (!ctx.isChunkLoaded(x >> 4, z >> 4)) {
            return Cell.UNLOADED;
        }
        Block block = ctx.world.getBlockAt(x, y, z);
        Material type = block.getType();
        int flags = materialFlags(type);
        if ((flags & M_AIR) != 0) {
            return Cell.AIR;
        }
        boolean water = (flags & M_WATER) != 0 || isWaterlogged(block, type);
        double[] boxes = (flags & M_PLATFORM) != 0 ? FULL_BOX : collisionBoxes(block);
        return new Cell(flags, boxes, water);
    }

    private static Cell cell(SearchContext ctx, int x, int y, int z) {
        long key = coordinateKey(x, y, z);
        Cell cell = ctx.cells.get(key);
        if (cell == null) {
            cell = loadCell(ctx, x, y, z);
            ctx.cells.put(key, cell);
        }
        return cell;
    }

    /** Same as {@link #cell} but blocks the current move breaks read as air. */
    private static Cell cell(SearchContext ctx, int x, int y, int z, Node from, MoveResult move) {
        if (from != null && from.brokenKeys.length > 0 || move != null && move.breakCount > 0) {
            long key = coordinateKey(x, y, z);
            if (isBroken(key, from, move)) {
                return Cell.AIR;
            }
        }
        return cell(ctx, x, y, z);
    }

    private static boolean isBroken(long key, Node from, MoveResult move) {
        if (from != null) {
            for (long broken : from.brokenKeys) {
                if (broken == key) {
                    return true;
                }
            }
        }
        if (move != null) {
            for (int i = 0; i < move.breakCount; i++) {
                if (move.breaks[i] == key) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean overlapsFootprint(double[] b, int k, int cellX, int cellZ, double px, double pz) {
        return cellX + b[k] < px + HALF_WIDTH - EPS && cellX + b[k + 3] > px - HALF_WIDTH + EPS
                && cellZ + b[k + 2] < pz + HALF_WIDTH - EPS && cellZ + b[k + 5] > pz - HALF_WIDTH + EPS;
    }

    private static boolean cellOverlapsFootprint(int cellX, int cellZ, double px, double pz) {
        return cellX < px + HALF_WIDTH - EPS && cellX + 1 > px - HALF_WIDTH + EPS
                && cellZ < pz + HALF_WIDTH - EPS && cellZ + 1 > pz - HALF_WIDTH + EPS;
    }

    /** Highest top of a box under the footprint with a top inside [low, high], or {@code best}. */
    private static double highestTop(Cell cell, int cellX, int cellY, int cellZ, double px, double pz,
                                     double low, double high, double best) {
        double[] b = cell.boxes;
        for (int k = 0; k < b.length; k += 6) {
            double top = cellY + b[k + 4];
            if (top < low || top > high || top <= best || !overlapsFootprint(b, k, cellX, cellZ, px, pz)) {
                continue;
            }
            best = top;
        }
        return best;
    }

    // ---------------------------------------------------------------------------------------------
    // Standing positions
    // ---------------------------------------------------------------------------------------------

    private static final int K_GROUND = 1;
    private static final int K_WATER = 1 << 1;
    private static final int K_CLIMB = 1 << 2;
    private static final int K_HEAD_WATER = 1 << 3;

    /** Whether (and how) the player can be at a block position with their feet in it. */
    private static final class Stand {
        private static final Stand INVALID = new Stand(false, 0.0, 0, 0, 0, Integer.MIN_VALUE, 0);

        private final boolean valid;
        /** Height of the player's feet. */
        private final double feet;
        private final int kind;
        /** Material flags of the block the player stands on. */
        private final int floorFlags;
        /** Position of the first obstacle when the position is blocked. */
        private final int obstacleX, obstacleY, obstacleZ;

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

        private boolean blockedAt() {
            return !valid && obstacleY != Integer.MIN_VALUE;
        }
    }

    private static Stand stand(SearchContext ctx, int x, int y, int z) {
        long key = coordinateKey(x, y, z);
        Stand stand = ctx.stands.get(key);
        if (stand == null) {
            stand = computeStand(ctx, x, y, z, null, null);
            ctx.stands.put(key, stand);
        }
        return stand;
    }

    /** {@link #stand} for a move that breaks blocks; uncached because broken blocks change the answer. */
    private static Stand stand(SearchContext ctx, int x, int y, int z, Node from, MoveResult move) {
        if ((from == null || from.brokenKeys.length == 0) && (move == null || move.breakCount == 0)) {
            return stand(ctx, x, y, z);
        }
        return computeStand(ctx, x, y, z, from, move);
    }

    private static Stand blockedStand(int x, int y, int z) {
        return new Stand(false, 0.0, 0, 0, x, y, z);
    }

    private static Stand computeStand(SearchContext ctx, int x, int y, int z, Node from, MoveResult move) {
        if (y <= ctx.minY || y >= ctx.maxY) {
            return Stand.INVALID;
        }
        Cell feet = cell(ctx, x, y, z, from, move);
        Cell below = cell(ctx, x, y - 1, z, from, move);
        double px = x + 0.5;
        double pz = z + 0.5;

        // The floor is the highest box top under the footprint that lies within this block's height.
        double support = highestTop(below, x, y - 1, z, px, pz, y - EPS, y + 1 - EPS, NEG);
        int floorFlags = support > NEG ? below.flags : 0;
        double inFeetBlock = highestTop(feet, x, y, z, px, pz, y - EPS, y + 1 - EPS, NEG);
        if (inFeetBlock > support) {
            support = inFeetBlock;
            floorFlags = feet.flags;
        }

        int kind = 0;
        double feetY = y;
        if (support > NEG) {
            kind |= K_GROUND;
            feetY = support;
            if ((floorFlags & M_FLOOR_HAZARD) != 0) {
                return Stand.INVALID;
            }
        }
        if (feet.water) {
            kind |= K_WATER;
        }
        if (feet.has(M_CLIMBABLE)) {
            kind |= K_CLIMB;
        }
        if (kind == 0) {
            return Stand.INVALID;
        }

        int topY = (int) Math.floor(feetY + PLAYER_HEIGHT - EPS);
        for (int cy = y - 1; cy <= topY; cy++) {
            Cell c = cy == y ? feet : cy == y - 1 ? below : cell(ctx, x, cy, z, from, move);
            if (cy >= y && c.has(M_BODY_HAZARD)) {
                return Stand.INVALID;
            }
            if (c.has(M_PLATFORM | M_OPENABLE)) {
                continue;
            }
            double[] b = c.boxes;
            for (int k = 0; k < b.length; k += 6) {
                if (cy + b[k + 4] <= feetY + EPS || cy + b[k + 1] >= feetY + PLAYER_HEIGHT - EPS
                        || !overlapsFootprint(b, k, x, z, px, pz)) {
                    continue;
                }
                return blockedStand(x, cy, z);
            }
        }

        for (int[] offset : LAVA_NEIGHBOURS) {
            if (cell(ctx, x + offset[0], y + offset[1], z + offset[2]).has(M_LAVA)) {
                return Stand.INVALID;
            }
        }

        if ((kind & K_WATER) != 0 && cell(ctx, x, y + 1, z, from, move).water) {
            kind |= K_HEAD_WATER;
        }
        return new Stand(true, feetY, kind, floorFlags, 0, Integer.MIN_VALUE, 0);
    }

    // ---------------------------------------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------------------------------------

    public static List<Node> findPath(Location start, Location end, Player player) {
        World world = start.getWorld();
        if (world == null || end.getWorld() == null || !world.equals(end.getWorld())) {
            return null;
        }
        SearchContext ctx = new SearchContext(world);
        IndexedOpenSet openSet = new IndexedOpenSet(1024);

        int sx = start.getBlockX(), sy = start.getBlockY(), sz = start.getBlockZ();
        int ex = end.getBlockX(), ey = end.getBlockY(), ez = end.getBlockZ();

        Stand endStand = stand(ctx, ex, ey, ez);
        // A solid target (a beacon, a portal frame) is reached by standing next to or on it.
        boolean endObstructed = !endStand.valid;
        ctx.targetInsideWater = endStand.valid && (endStand.kind & K_HEAD_WATER) != 0;

        Stand startStand = stand(ctx, sx, sy, sz);
        Node startNode = new Node(new Location(world, sx, sy, sz), null, 0,
                heuristic(ctx, sx, sy, sz, ex, ey, ez), false, MOVE_HORIZONTAL);
        startNode.feetY = startStand.valid ? startStand.feet : Math.max(sy, Math.min(start.getY(), sy + 0.99));
        startNode.kind = startStand.valid ? startStand.kind : K_GROUND;
        startNode.floorFlags = startStand.floorFlags;
        openSet.add(startNode);
        ctx.addNode(coordinateKey(sx, sy, sz), startNode);

        int iterations = 0;
        long maxRadius = PathfinderConfig.MAX_SEARCH_RADIUS;
        long maxRadiusSq = maxRadius * maxRadius;

        while (!openSet.isEmpty() && iterations < PathfinderConfig.MAX_ITERATIONS) {
            iterations++;
            Node current = openSet.poll();
            current.closed = true;

            if (isGoal(current, ex, ey, ez, endObstructed)) {
                return reconstructPath(current);
            }

            long ddx = current.x - sx, ddy = current.y - sy, ddz = current.z - sz;
            if (ddx * ddx + ddy * ddy + ddz * ddz > maxRadiusSq) {
                continue;
            }

            expand(ctx, openSet, current, ex, ey, ez);
        }

        Node closest = null;
        long closestDistance = Long.MAX_VALUE;
        for (Node node : ctx.allNodes) {
            long dx = node.x - ex, dy = node.y - ey, dz = node.z - ez;
            long distance = dx * dx + dy * dy + dz * dz;
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = node;
            }
        }

        return closest != null ? reconstructPath(closest) : null;
    }

    private static boolean isGoal(Node node, int ex, int ey, int ez, boolean endObstructed) {
        if (node.x == ex && node.y == ey && node.z == ez) {
            return true;
        }
        return endObstructed && Math.abs(node.x - ex) <= 1 && Math.abs(node.y - ey) <= 1
                && Math.abs(node.z - ez) <= 1;
    }

    private static void expand(SearchContext ctx, IndexedOpenSet openSet, Node current, int ex, int ey, int ez) {
        MoveResult move = ctx.move;

        for (int i = 0; i < MOVE_DX.length; i++) {
            int dx = MOVE_DX[i];
            int dz = MOVE_DZ[i];
            if (!horizontalMove(ctx, current, dx, dz, move)) {
                continue;
            }
            double cost = (dx != 0 && dz != 0) ? PathfinderConfig.DIAGONAL_COST : PathfinderConfig.STRAIGHT_COST;
            if (move.moveType == MOVE_JUMP) {
                cost += PathfinderConfig.JUMP_COST;
            } else if (move.moveType == MOVE_FALL) {
                cost += fallCost(move.drop);
            }
            offer(ctx, openSet, current, move, cost, dx, dz, ex, ey, ez);
        }

        if ((current.kind & (K_CLIMB | K_WATER)) != 0 && climbUp(ctx, current, move)) {
            offer(ctx, openSet, current, move, PathfinderConfig.STRAIGHT_COST + PathfinderConfig.VERTICAL_COST,
                    0, 0, ex, ey, ez);
        }

        if (descend(ctx, current, move)) {
            double cost = PathfinderConfig.STRAIGHT_COST + (move.moveType == MOVE_DOWN
                    ? PathfinderConfig.VERTICAL_COST
                    : fallCost(move.drop));
            offer(ctx, openSet, current, move, cost, 0, 0, ex, ey, ez);
        }

        if (canStartGapJump(current)) {
            gapJumps(ctx, openSet, current, ex, ey, ez);
        }
    }

    private static double fallCost(double drop) {
        double cost = PathfinderConfig.FALL_COST;
        int blocks = (int) Math.ceil(drop - EPS);
        if (blocks > 1) {
            cost += (blocks - 1) * 0.1;
        }
        return cost;
    }

    private static void offer(SearchContext ctx, IndexedOpenSet openSet, Node current, MoveResult move,
                              double moveCost, int dirX, int dirZ, int ex, int ey, int ez) {
        long key = coordinateKey(move.x, move.y, move.z);
        Node existing = ctx.nodes.get(key);
        if (existing != null && existing.closed) {
            return;
        }

        moveCost += turnCost(current, dirX, dirZ);
        moveCost += arrivalCost(ctx, move);
        if (move.breakCount > 0) {
            moveCost += PathfinderConfig.BREAK_BLOCK_COST * move.breakCount;
        }
        double nextG = current.g + moveCost;

        if (existing != null) {
            if (nextG < existing.g) {
                existing.parent = current;
                existing.g = nextG;
                existing.f = nextG + existing.h;
                existing.apply(ctx.world, move, dirX, dirZ);
                openSet.decreaseKey(existing);
            }
            return;
        }

        double nextH = heuristic(ctx, move.x, move.y, move.z, ex, ey, ez);
        Node neighbor = new Node(new Location(ctx.world, move.x, move.y, move.z), current, nextG, nextH,
                false, move.moveType);
        neighbor.apply(ctx.world, move, dirX, dirZ);
        openSet.add(neighbor);
        ctx.addNode(key, neighbor);
    }

    private static double turnCost(Node current, int dirX, int dirZ) {
        if (current.parent == null || (dirX == 0 && dirZ == 0)) {
            return 0.0;
        }
        int prevDirX = current.dirX;
        int prevDirZ = current.dirZ;
        if ((prevDirX == 0 && prevDirZ == 0) || (dirX == prevDirX && dirZ == prevDirZ)) {
            return 0.0;
        }
        int dotProduct = dirX * prevDirX + dirZ * prevDirZ;
        if (dotProduct == 0) {
            return PathfinderConfig.RIGHT_ANGLE_TURN_COST;
        }
        if ((dirX != 0 && dirZ != 0) != (prevDirX != 0 && prevDirZ != 0)) {
            return PathfinderConfig.DIAGONAL_TURN_COST;
        }
        return 0.0;
    }

    private static double arrivalCost(SearchContext ctx, MoveResult move) {
        double cost = 0.0;
        if ((move.kind & K_WATER) != 0) {
            cost += (move.kind & K_HEAD_WATER) != 0 ? PathfinderConfig.WATER_COST * 5.0 : PathfinderConfig.WATER_COST;
        }
        Cell feet = cell(ctx, move.x, move.y, move.z);
        Cell head = cell(ctx, move.x, move.y + 1, move.z);
        if (move.passedDoor || feet.has(M_DOOR | M_FENCE_GATE) || head.has(M_DOOR | M_FENCE_GATE)) {
            cost += PathfinderConfig.DOOR_COST;
        }
        if (feet.has(M_TRAPDOOR)) {
            cost += PathfinderConfig.TRAPDOOR_COST;
        }
        if (head.has(M_TRAPDOOR)) {
            cost += PathfinderConfig.TRAPDOOR_COST;
        }
        if (move.passedTrapdoor && !feet.has(M_TRAPDOOR) && !head.has(M_TRAPDOOR)) {
            cost += PathfinderConfig.TRAPDOOR_COST;
        }
        if (feet.has(M_PLATFORM)) {
            cost += PathfinderConfig.SCAFFOLDING_COST;
        }
        if ((move.floorFlags & M_SLOW) != 0) {
            cost += PathfinderConfig.STRAIGHT_COST;
        }
        return cost;
    }

    // ---------------------------------------------------------------------------------------------
    // Moves
    // ---------------------------------------------------------------------------------------------

    /** Scratch result of one move attempt; reused for every move of a search. */
    private static final class MoveResult {
        private static final int OK = 0;
        private static final int BLOCKED = 1;
        private static final int FAILED = 2;

        private int status;
        private int blockX, blockY, blockZ;
        private int x, y, z;
        private double feet;
        private int kind;
        private int floorFlags;
        private int moveType;
        private double drop;
        private boolean passedDoor;
        private boolean passedTrapdoor;
        private final long[] breaks = new long[MAX_BREAKS_PER_MOVE];
        private int breakCount;

        private boolean blocked(int x, int y, int z) {
            status = BLOCKED;
            blockX = x;
            blockY = y;
            blockZ = z;
            return false;
        }

        private boolean failed() {
            status = FAILED;
            return false;
        }

        private boolean land(int x, int y, int z, Stand stand, int moveType, double drop) {
            status = OK;
            this.x = x;
            this.y = y;
            this.z = z;
            this.feet = stand.feet;
            this.kind = stand.kind;
            this.floorFlags = stand.floorFlags;
            this.moveType = moveType;
            this.drop = drop;
            return true;
        }

        private void reset() {
            breakCount = 0;
            passedDoor = false;
            passedTrapdoor = false;
            drop = 0.0;
        }
    }

    /** Height the player can rise while moving sideways out of the given position. */
    private static double riseReach(Node node) {
        if ((node.kind & (K_WATER | K_CLIMB)) != 0) {
            return JUMP_HEIGHT;
        }
        if ((node.kind & K_GROUND) != 0 && (node.floorFlags & M_NO_JUMP) == 0) {
            return JUMP_HEIGHT;
        }
        return STEP_HEIGHT;
    }

    private static boolean horizontalMove(SearchContext ctx, Node from, int dx, int dz, MoveResult move) {
        double reach = riseReach(from);
        if (sweep(ctx, from, dx, dz, STEP_HEIGHT, false, move)) {
            return true;
        }
        if (move.status != MoveResult.BLOCKED) {
            return false;
        }
        if (reach > STEP_HEIGHT && sweep(ctx, from, dx, dz, reach, false, move)) {
            return true;
        }
        // Breaking is the last resort, only when walking or jumping cannot get past the obstacle.
        if (sweep(ctx, from, dx, dz, STEP_HEIGHT, true, move)) {
            return true;
        }
        return reach > STEP_HEIGHT && sweep(ctx, from, dx, dz, reach, true, move);
    }

    private static boolean sweep(SearchContext ctx, Node from, int dx, int dz, double maxRise, boolean allowBreak,
                                 MoveResult move) {
        move.reset();
        while (true) {
            if (sweepOnce(ctx, from, dx, dz, maxRise, move)) {
                return true;
            }
            if (move.status != MoveResult.BLOCKED || !allowBreak || !tryBreak(ctx, from, move)) {
                return false;
            }
        }
    }

    private static boolean tryBreak(SearchContext ctx, Node from, MoveResult move) {
        if (move.breakCount >= MAX_BREAKS_PER_MOVE) {
            return false;
        }
        int x = move.blockX, y = move.blockY, z = move.blockZ;
        if (!isBreakable(ctx, x, y, z, cell(ctx, x, y, z, from, move))) {
            return false;
        }
        move.breaks[move.breakCount++] = coordinateKey(x, y, z);
        return true;
    }

    private static boolean isBreakable(SearchContext ctx, int x, int y, int z, Cell cell) {
        if (cell.boxes.length == 0 || cell.has(M_UNBREAKABLE | M_PLATFORM | M_OPENABLE)) {
            return false;
        }
        for (int[] offset : BREAK_NEIGHBOURS) {
            if (cell(ctx, x + offset[0], y + offset[1], z + offset[2]).has(M_LAVA)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Moves the player's box from the centre of the current block to the centre of the neighbouring
     * column, stepping up ledges (up to {@code maxRise}) and following the floor down, then drops it
     * onto whatever is below.
     */
    private static boolean sweepOnce(SearchContext ctx, Node from, int dx, int dz, double maxRise, MoveResult move) {
        int ax = from.x, az = from.z;
        int bx = ax + dx, bz = az + dz;
        double startFeet = from.feetY;
        boolean jumping = maxRise > STEP_HEIGHT + EPS;

        int xLo = Math.min(ax, bx), zLo = Math.min(az, bz);
        int xSize = Math.abs(dx) + 1, zSize = Math.abs(dz) + 1;
        int yLo = (int) Math.floor(startFeet) - 2;
        // Walking up stairs can rise a full block within one move, so leave room above the reach.
        int ySize = (int) Math.floor(startFeet + Math.max(maxRise, JUMP_HEIGHT) + PLAYER_HEIGHT) - yLo + 2;
        Cell[] region = ctx.region(xSize * zSize * ySize);
        for (int ix = 0; ix < xSize; ix++) {
            for (int iz = 0; iz < zSize; iz++) {
                for (int iy = 0; iy < ySize; iy++) {
                    region[(ix * zSize + iz) * ySize + iy] = cell(ctx, xLo + ix, yLo + iy, zLo + iz, from, move);
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

            for (int guard = 0; ; guard++) {
                double stepTop = NEG;
                for (int ix = 0; ix < xSize; ix++) {
                    for (int iz = 0; iz < zSize; iz++) {
                        int cellX = xLo + ix, cellZ = zLo + iz;
                        if (!cellOverlapsFootprint(cellX, cellZ, px, pz)) {
                            continue;
                        }
                        for (int iy = 0; iy < ySize; iy++) {
                            Cell c = region[(ix * zSize + iz) * ySize + iy];
                            int cellY = yLo + iy;
                            boolean bodyInCell = cellY + 1 > feet + EPS && cellY < feet + PLAYER_HEIGHT - EPS;
                            if (bodyInCell && c.has(M_BODY_HAZARD)) {
                                return move.failed();
                            }
                            if (c.has(M_PLATFORM)) {
                                continue;
                            }
                            boolean openable = c.has(M_OPENABLE);
                            double[] b = c.boxes;
                            for (int k = 0; k < b.length; k += 6) {
                                double top = cellY + b[k + 4];
                                if (top <= feet + EPS || cellY + b[k + 1] >= feet + PLAYER_HEIGHT - EPS
                                        || !overlapsFootprint(b, k, cellX, cellZ, px, pz)) {
                                    continue;
                                }
                                double rise = top - feet;
                                if (openable) {
                                    // Walk onto a low closed trapdoor, open everything else on the way.
                                    if (rise <= STEP_HEIGHT + EPS && !airborne) {
                                        stepTop = Math.max(stepTop, top);
                                    } else if (c.has(M_TRAPDOOR)) {
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
                if (feet + PLAYER_HEIGHT > yLo + ySize) {
                    return move.failed();
                }
            }

            // Follow the floor down as long as it only drops by a step.
            double support = NEG;
            for (int ix = 0; ix < xSize; ix++) {
                for (int iz = 0; iz < zSize; iz++) {
                    int cellX = xLo + ix, cellZ = zLo + iz;
                    if (!cellOverlapsFootprint(cellX, cellZ, px, pz)) {
                        continue;
                    }
                    for (int iy = 0; iy < ySize; iy++) {
                        support = highestTop(region[(ix * zSize + iz) * ySize + iy], cellX, yLo + iy, cellZ,
                                px, pz, feet - STEP_HEIGHT - EPS, feet + EPS, support);
                    }
                }
            }
            if (support > NEG) {
                feet = support;
                airborne = false;
            } else {
                airborne = true;
            }
        }

        // Walking up stairs rises in half steps; only a real jump needs to go straight up first.
        boolean jumped = jumping && peak - startFeet > STEP_HEIGHT + EPS;
        if (jumped) {
            // Jumping (or climbing) needs head room above the take-off position first.
            int topY = (int) Math.floor(peak + PLAYER_HEIGHT - EPS);
            double px = ax + 0.5, pz = az + 0.5;
            for (int cellY = (int) Math.floor(startFeet); cellY <= topY; cellY++) {
                Cell c = cell(ctx, ax, cellY, az, from, move);
                if (c.has(M_PLATFORM | M_OPENABLE)) {
                    continue;
                }
                if (c.has(M_BODY_HAZARD)) {
                    return move.failed();
                }
                double[] b = c.boxes;
                for (int k = 0; k < b.length; k += 6) {
                    if (cellY + b[k + 4] <= startFeet + EPS || cellY + b[k + 1] >= peak + PLAYER_HEIGHT - EPS
                            || !overlapsFootprint(b, k, ax, az, px, pz)) {
                        continue;
                    }
                    return move.blocked(ax, cellY, az);
                }
            }
        }

        // A fall is measured from where the player left the floor (or from the top of the jump).
        return land(ctx, from, bx, bz, feet, jumped ? Math.max(peak, feet) : feet, jumped, move);
    }

    /**
     * Lets the player drop down the column at (x, z) from {@code feet} and records where they end up:
     * on the first floor, in water, or on a ladder, vine or scaffolding that catches them.
     */
    private static boolean land(SearchContext ctx, Node from, int x, int z, double feet, double fallStart,
                                boolean jumped, MoveResult move) {
        double px = x + 0.5, pz = z + 0.5;
        int maxScan = Math.max(0, PathfinderConfig.MAX_SAFE_FALL_HEIGHT) + SOFT_LANDING_SCAN + 2;
        int cellY = (int) Math.floor(feet + EPS);
        double landing = NEG;
        boolean caught = false;

        for (int scanned = 0; scanned <= maxScan; scanned++, cellY--) {
            if (cellY <= ctx.minY) {
                return move.failed();
            }
            Cell c = cell(ctx, x, cellY, z, from, move);
            Cell below = cell(ctx, x, cellY - 1, z, from, move);
            double top = highestTop(c, x, cellY, z, px, pz, cellY - EPS, feet + EPS, NEG);
            top = highestTop(below, x, cellY - 1, z, px, pz, cellY - EPS, feet + EPS, top);
            if (top > NEG) {
                landing = top;
                break;
            }
            if (c.has(M_BODY_HAZARD)) {
                return move.failed();
            }
            if (c.water || c.has(M_CLIMBABLE)) {
                landing = cellY;
                caught = true;
                break;
            }
        }
        if (landing == NEG) {
            return move.failed();
        }

        int landY = caught ? cellY : (int) Math.floor(landing + EPS);
        Stand stand = stand(ctx, x, landY, z, from, move);
        if (!stand.valid || Math.abs(stand.feet - landing) > 0.05) {
            return move.failed();
        }

        double drop = fallStart - stand.feet;
        if (drop > STEP_HEIGHT + EPS && !caught && (stand.kind & (K_WATER | K_CLIMB)) == 0
                && drop > safeFallHeight(stand.floorFlags) + EPS) {
            return move.failed();
        }

        int moveType;
        if (jumped) {
            moveType = MOVE_JUMP;
        } else if (drop > STEP_HEIGHT + EPS) {
            // Stepping off a ledge onto a ladder or vine is climbing down, not falling.
            moveType = caught && (stand.kind & K_CLIMB) != 0 ? MOVE_DOWN : MOVE_FALL;
        } else {
            moveType = MOVE_HORIZONTAL;
        }
        return move.land(x, landY, z, stand, moveType, Math.max(0.0, drop));
    }

    /** Largest fall (in blocks) that is acceptable when landing on a block with the given flags. */
    private static double safeFallHeight(int floorFlags) {
        double safe = Math.max(0, PathfinderConfig.MAX_SAFE_FALL_HEIGHT);
        if ((floorFlags & M_NO_FALL_DAMAGE) != 0) {
            return safe + SOFT_LANDING_SCAN;
        }
        // Fall damage is (distance - 3) scaled by the block's damage multiplier.
        double multiplier = (floorFlags & M_FALL_DAMAGE_20) != 0 ? 0.2
                : (floorFlags & M_FALL_DAMAGE_50) != 0 ? 0.5 : 1.0;
        if (multiplier < 1.0 && safe > 3.0) {
            return Math.max(safe, 3.0 + (safe - 3.0) / multiplier);
        }
        return safe;
    }

    /** Climbing a ladder, vine or scaffolding, swimming up, or stepping out on top of scaffolding. */
    private static boolean climbUp(SearchContext ctx, Node from, MoveResult move) {
        move.reset();
        int x = from.x, y = from.y + 1, z = from.z;
        while (true) {
            Stand stand = stand(ctx, x, y, z, from, move);
            if (stand.valid) {
                if (stand.feet <= from.feetY + EPS) {
                    return move.failed();
                }
                return move.land(x, y, z, stand, MOVE_UP, 0.0);
            }
            if (!stand.blockedAt()) {
                return move.failed();
            }
            move.blocked(stand.obstacleX, stand.obstacleY, stand.obstacleZ);
            if (!tryBreak(ctx, from, move)) {
                return false;
            }
        }
    }

    /** Climbing or swimming down, sneaking down scaffolding, or digging through the floor. */
    private static boolean descend(SearchContext ctx, Node from, MoveResult move) {
        move.reset();
        int x = from.x, z = from.z;
        double px = x + 0.5, pz = z + 0.5;
        double feet = from.feetY;

        // Anything solid right under the feet has to be dug through first.
        for (int attempt = 0; ; attempt++) {
            int solidY = Integer.MIN_VALUE;
            for (int cellY = from.y - 1; cellY <= from.y && solidY == Integer.MIN_VALUE; cellY++) {
                Cell c = cell(ctx, x, cellY, z, from, move);
                if (c.has(M_PLATFORM | M_OPENABLE)) {
                    continue;
                }
                if (highestTop(c, x, cellY, z, px, pz, feet - EPS, feet + EPS, NEG) > NEG) {
                    solidY = cellY;
                }
            }
            if (solidY == Integer.MIN_VALUE) {
                break;
            }
            move.blocked(x, solidY, z);
            if (attempt > 0 || !tryBreak(ctx, from, move)) {
                return false;
            }
        }

        double below = feet - 0.01;
        double landing = NEG;
        int cellY = (int) Math.floor(below);
        boolean caught = false;
        int maxScan = Math.max(0, PathfinderConfig.MAX_SAFE_FALL_HEIGHT) + SOFT_LANDING_SCAN + 2;
        for (int scanned = 0; scanned <= maxScan; scanned++, cellY--) {
            if (cellY <= ctx.minY) {
                return move.failed();
            }
            Cell c = cell(ctx, x, cellY, z, from, move);
            Cell under = cell(ctx, x, cellY - 1, z, from, move);
            double top = highestTop(c, x, cellY, z, px, pz, cellY - EPS, below, NEG);
            top = highestTop(under, x, cellY - 1, z, px, pz, cellY - EPS, below, top);
            if (top > NEG) {
                landing = top;
                break;
            }
            if (cellY < from.y && c.has(M_BODY_HAZARD)) {
                return move.failed();
            }
            if (cellY < from.y && (c.water || c.has(M_CLIMBABLE))) {
                landing = cellY;
                caught = true;
                break;
            }
        }
        if (landing == NEG) {
            return move.failed();
        }
        int landY = caught ? cellY : (int) Math.floor(landing + EPS);
        if (landY >= from.y) {
            return move.failed();
        }
        Stand stand = stand(ctx, x, landY, z, from, move);
        if (!stand.valid || Math.abs(stand.feet - landing) > 0.05) {
            return move.failed();
        }
        double drop = feet - stand.feet;
        boolean supported = caught || ((from.kind | stand.kind) & (K_WATER | K_CLIMB)) != 0;
        if (!supported && drop > safeFallHeight(stand.floorFlags) + EPS) {
            return move.failed();
        }
        return move.land(x, landY, z, stand, supported ? MOVE_DOWN : MOVE_FALL, drop);
    }

    // ---------------------------------------------------------------------------------------------
    // Gap jumps (simple parkour)
    // ---------------------------------------------------------------------------------------------

    private static boolean canStartGapJump(Node node) {
        return PathfinderConfig.MAX_BLOCK_JUMP_DISTANCE >= 2
                && (node.kind & K_GROUND) != 0
                && (node.kind & K_WATER) == 0
                && (node.floorFlags & (M_NO_JUMP | M_SLOW)) == 0;
    }

    private static void gapJumps(SearchContext ctx, IndexedOpenSet openSet, Node current, int ex, int ey, int ez) {
        int reach = Math.min(PathfinderConfig.MAX_BLOCK_JUMP_DISTANCE, MAX_PARKOUR_REACH);
        int minY = current.y - Math.max(0, PathfinderConfig.MAX_SAFE_FALL_HEIGHT) - 1;
        MoveResult move = ctx.move;

        // Jumps start at an edge: the block right next to the player in the jump direction must not
        // be walkable (a gap, a drop or an obstacle). Most positions are nowhere near an edge.
        boolean[] edge = new boolean[9];
        boolean anyEdge = false;
        for (int sx = -1; sx <= 1; sx++) {
            for (int sz = -1; sz <= 1; sz++) {
                if ((sx != 0 || sz != 0) && !hasWalkingFloor(ctx, current.x + sx, current.z + sz, current)) {
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
                if (gap > MAX_FLAT_GAP + EPS || gapIsDeadly(ctx, current, dx, dz, steps)) {
                    continue;
                }
                int tx = current.x + dx, tz = current.z + dz;
                for (int ty = current.y + 1; ty >= minY; ty--) {
                    Stand stand = stand(ctx, tx, ty, tz);
                    if (!stand.valid || (stand.kind & K_GROUND) == 0 || (stand.kind & K_WATER) != 0) {
                        continue;
                    }
                    double rise = stand.feet - current.feetY;
                    double maxGap = rise > STEP_HEIGHT + EPS ? MAX_FULL_UP_GAP
                            : rise > EPS ? MAX_HALF_UP_GAP : MAX_FLAT_GAP;
                    if (rise > 1.0 + EPS || gap > maxGap + EPS) {
                        continue;
                    }
                    double drop = -rise;
                    if (drop > safeFallHeight(stand.floorFlags) + EPS) {
                        continue;
                    }
                    if (!jumpArcClear(ctx, current, tx, tz, stand.feet)) {
                        continue;
                    }
                    move.reset();
                    move.land(tx, ty, tz, stand, MOVE_BLOCK_JUMP, Math.max(0.0, drop));
                    double distance = Math.hypot(dx, dz);
                    double cost = PathfinderConfig.BLOCK_JUMP_COST * distance * distance;
                    if (drop > STEP_HEIGHT + EPS) {
                        cost += PathfinderConfig.FALL_COST;
                    }
                    offer(ctx, openSet, current, move, cost, Integer.signum(dx), Integer.signum(dz), ex, ey, ez);
                }
            }
        }
    }

    /** Never suggest a jump across lava, fire or other hazards: a missed jump would be fatal. */
    private static boolean gapIsDeadly(SearchContext ctx, Node from, int dx, int dz, int steps) {
        int depth = Math.max(0, PathfinderConfig.MAX_SAFE_FALL_HEIGHT) + 2;
        for (int step = 1; step < steps; step++) {
            int x = from.x + (int) Math.floor(dx * (double) step / steps + 0.5);
            int z = from.z + (int) Math.floor(dz * (double) step / steps + 0.5);
            for (int y = from.y + 1; y >= from.y - depth; y--) {
                Cell c = cell(ctx, x, y, z);
                if (c.has(M_LAVA | M_BODY_HAZARD | M_FLOOR_HAZARD)) {
                    return true;
                }
                if (y < from.y && c.boxes.length > 0) {
                    break;
                }
            }
        }
        return false;
    }

    private static boolean hasWalkingFloor(SearchContext ctx, int x, int z, Node from) {
        for (int y = from.y - 1; y <= from.y + 1; y++) {
            Stand stand = stand(ctx, x, y, z);
            if (stand.valid && (stand.kind & K_GROUND) != 0 && (stand.kind & K_WATER) == 0
                    && Math.abs(stand.feet - from.feetY) <= STEP_HEIGHT + EPS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Follows a jump arc (apex {@link #JUMP_HEIGHT} above the take-off) from the current block to the
     * landing block and makes sure the player's box never touches a block, hazard or water on the way.
     */
    private static boolean jumpArcClear(SearchContext ctx, Node from, int tx, int tz, double landFeet) {
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
            int y0 = (int) Math.floor(feet + EPS) - 1, y1 = (int) Math.floor(feet + PLAYER_HEIGHT - EPS);
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = y0; y <= y1; y++) {
                        Cell c = cell(ctx, x, y, z, from, null);
                        boolean bodyInCell = y + 1 > feet + EPS && y < feet + PLAYER_HEIGHT - EPS;
                        if (bodyInCell && (c.water || c.has(M_BODY_HAZARD))) {
                            return false;
                        }
                        if (c.has(M_PLATFORM)) {
                            continue;
                        }
                        double[] boxes = c.boxes;
                        for (int k = 0; k < boxes.length; k += 6) {
                            if (y + boxes[k + 4] <= feet + EPS || y + boxes[k + 1] >= feet + PLAYER_HEIGHT - EPS
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

    // ---------------------------------------------------------------------------------------------
    // Path post-processing
    // ---------------------------------------------------------------------------------------------

    private static List<Node> reconstructPath(Node node) {
        List<Node> path = new ArrayList<>();
        while (node != null) {
            path.add(node);
            node = node.parent;
        }
        Collections.reverse(path);
        path = smoothWaterSurfacePath(path);
        for (Node pathNode : path) {
            pathNode.populateDisplayFlags();
        }
        return path;
    }

    private static List<Node> smoothWaterSurfacePath(List<Node> path) {
        if (path.size() < 3) {
            return path;
        }

        List<Node> smoothed = new ArrayList<>(path.size());
        smoothed.add(path.get(0));
        int currentIndex = 0;

        while (currentIndex < path.size() - 1) {
            Node current = path.get(currentIndex);
            int nextIndex = currentIndex + 1;

            if (isTraversableWaterSurface(current.location)) {
                int farthestVisible = currentIndex;
                int surfaceY = current.location.getBlockY();

                for (int candidate = currentIndex + 1; candidate < path.size(); candidate++) {
                    Node candidateNode = path.get(candidate);
                    if (candidateNode.location.getBlockY() != surfaceY
                            || !isTraversableWaterSurface(candidateNode.location)) {
                        break;
                    }
                    if (hasDirectWaterSurfacePath(current.location, candidateNode.location)) {
                        farthestVisible = candidate;
                    }
                }

                if (farthestVisible > currentIndex + 1) {
                    path.get(farthestVisible).moveType = MOVE_WATER_SURFACE;
                    nextIndex = farthestVisible;
                }
            }

            smoothed.add(path.get(nextIndex));
            currentIndex = nextIndex;
        }

        return smoothed;
    }

    private static boolean hasDirectWaterSurfacePath(Location start, Location end) {
        if (start.getWorld() == null || start.getWorld() != end.getWorld()
                || start.getBlockY() != end.getBlockY()) {
            return false;
        }

        double startX = start.getBlockX() + 0.5;
        double startZ = start.getBlockZ() + 0.5;
        double deltaX = end.getBlockX() + 0.5 - startX;
        double deltaZ = end.getBlockZ() + 0.5 - startZ;
        int samples = Math.max(1, (int) Math.ceil(Math.hypot(deltaX, deltaZ) * 4.0));

        for (int sample = 0; sample <= samples; sample++) {
            double ratio = (double) sample / samples;
            Location point = new Location(start.getWorld(),
                    startX + deltaX * ratio,
                    start.getBlockY(),
                    startZ + deltaZ * ratio);
            if (!isTraversableWaterSurface(point)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isTraversableWaterSurface(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block below = feet.getRelative(0, -1, 0);

        boolean feetWater = isWater(feet);
        boolean headWater = isWater(head);
        boolean belowWater = isWater(below);

        boolean feetOpen = feetWater || (feet.isPassable() && !hasFlag(feet, M_BODY_HAZARD));
        boolean headOpen = !headWater && head.isPassable() && !hasFlag(head, M_BODY_HAZARD);

        return feetOpen && headOpen
                && ((!feetWater && belowWater) || (feetWater && !headWater));
    }

    private static double heuristic(SearchContext ctx, int x, int y, int z, int ex, int ey, int ez) {
        int dx = Math.abs(x - ex);
        int dy = Math.abs(y - ey);
        int dz = Math.abs(z - ez);

        double baseHeuristic = dx + dz + dy * 1.5;

        if (cell(ctx, x, y, z).water) {
            baseHeuristic += cell(ctx, x, y + 1, z).water
                    ? PathfinderConfig.WATER_COST * 2.0
                    : PathfinderConfig.WATER_COST;
        }
        if (ctx.targetInsideWater) {
            baseHeuristic += PathfinderConfig.WATER_COST * 2.0;
        }

        double perturbation = (x * 0.001 + z * 0.0001) % 0.01;
        return baseHeuristic + perturbation;
    }

    private static long coordinateKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | (long) y & 0xFFFL;
    }

    // ---------------------------------------------------------------------------------------------
    // Block helpers used by the navigation runtime
    // ---------------------------------------------------------------------------------------------

    private static boolean hasFlag(Block block, int flag) {
        return (materialFlags(block.getType()) & flag) != 0;
    }

    /** Water, bubble columns, underwater plants and waterlogged blocks. */
    public static boolean isWater(Block block) {
        Material type = block.getType();
        if ((materialFlags(type) & M_WATER) != 0) {
            return true;
        }
        return (materialFlags(type) & M_AIR) == 0 && isWaterlogged(block, type);
    }

    public static boolean isPlayerInAir(Player player) {
        if (player == null) return false;
        Location playerLoc = player.getLocation();
        double verticalVelocity = player.getVelocity().getY();
        if (verticalVelocity > 0.05) return false;
        if (isBlockSupportive(playerLoc.getBlock()) || isBlockSupportive(playerLoc.getBlock().getRelative(0, -1, 0))) {
            return false;
        }
        for (int i = 2; i <= 10; i++) {
            Block blockBelow = playerLoc.getBlock().getRelative(0, -i, 0);
            if (isBlockSupportive(blockBelow)) {
                if (verticalVelocity >= -5.5) return false;
            }
        }
        return true;
    }

    private static boolean isBlockSupportive(Block block) {
        if (hasFlag(block, M_AIR)) {
            return false;
        }
        return hasFlag(block, M_CLIMBABLE | M_WATER) || !block.isPassable() || isWater(block);
    }

    public static boolean isDoor(Block block) {
        return hasFlag(block, M_DOOR);
    }

    public static boolean isDoorPassable(Block block) {
        return hasFlag(block, M_DOOR) && hasFlag(block, M_OPENABLE);
    }

    public static boolean isBanner(Block block) {
        return hasFlag(block, M_BANNER);
    }

    public static boolean isBannerPassable(Block block) {
        return isBanner(block);
    }

    public static boolean isTrapdoor(Block block) {
        return hasFlag(block, M_TRAPDOOR);
    }

    public static boolean isIronTrapdoor(Block block) {
        return hasFlag(block, M_TRAPDOOR) && !hasFlag(block, M_OPENABLE);
    }

    public static boolean isTrapdoorPassable(Block block) {
        return hasFlag(block, M_TRAPDOOR) && hasFlag(block, M_OPENABLE);
    }

    public static boolean isLadder(Block block) {
        return hasFlag(block, M_LADDER);
    }

    /** Ladders, every kind of vine and scaffolding. */
    public static boolean isClimbable(Block block) {
        return hasFlag(block, M_CLIMBABLE);
    }

    public static boolean isScaffolding(Block block) {
        return hasFlag(block, M_PLATFORM);
    }

    public static boolean isFence(Block block) {
        String name = block.getType().name();
        return name.contains("FENCE") && !name.contains("GATE");
    }

    public static boolean isFenceGate(Block block) {
        return hasFlag(block, M_FENCE_GATE);
    }

    public static boolean isCarpet(Block block) {
        return block.getType().name().contains("CARPET");
    }

    public static boolean isCompletelyPassable(Block block) {
        return block.isPassable() || hasFlag(block, M_OPENABLE | M_BANNER);
    }

    public static boolean isLocationPassableForPlayer(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        return isCompletelyPassable(feet) && isCompletelyPassable(head);
    }

    // ---------------------------------------------------------------------------------------------
    // Data structures
    // ---------------------------------------------------------------------------------------------

    private static int compareNodes(Node n1, Node n2) {
        int fCompare = Double.compare(n1.f, n2.f);
        if (fCompare != 0) {
            return fCompare;
        }

        int gCompare = Double.compare(n2.g, n1.g);
        if (gCompare != 0) {
            return gCompare;
        }

        int xCompare = Integer.compare(n1.x, n2.x);
        if (xCompare != 0) {
            return xCompare;
        }

        int zCompare = Integer.compare(n1.z, n2.z);
        if (zCompare != 0) {
            return zCompare;
        }

        return Integer.compare(n1.y, n2.y);
    }

    private static final class SearchContext {
        private final World world;
        private final int minY;
        private final int maxY;
        private final LongObjectMap<Node> nodes = new LongObjectMap<>(2048);
        private final LongObjectMap<Cell> cells = new LongObjectMap<>(8192);
        private final LongObjectMap<Stand> stands = new LongObjectMap<>(4096);
        private final LongByteMap loadedChunks = new LongByteMap(64);
        private final List<Node> allNodes = new ArrayList<>(1024);
        private final MoveResult move = new MoveResult();
        private Cell[] region = new Cell[64];
        private boolean targetInsideWater;

        private SearchContext(World world) {
            this.world = world;
            this.minY = world.getMinHeight();
            this.maxY = world.getMaxHeight();
        }

        private void addNode(long key, Node node) {
            nodes.put(key, node);
            allNodes.add(node);
        }

        private Cell[] region(int size) {
            if (region.length < size) {
                region = new Cell[size];
            }
            return region;
        }

        private boolean isChunkLoaded(int chunkX, int chunkZ) {
            long key = ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
            byte cached = loadedChunks.get(key);
            if (cached != 0) {
                return cached == LongByteMap.TRUE;
            }
            boolean loaded;
            try {
                loaded = world.isChunkLoaded(chunkX, chunkZ);
            } catch (Throwable ignored) {
                loaded = false;
            }
            loadedChunks.put(key, loaded ? LongByteMap.TRUE : LongByteMap.FALSE);
            return loaded;
        }
    }

    private static final class IndexedOpenSet {
        private Node[] heap;
        private int size;

        private IndexedOpenSet(int initialCapacity) {
            heap = new Node[Math.max(2, initialCapacity + 1)];
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private void add(Node node) {
            ensureCapacity(size + 1);
            heap[++size] = node;
            node.heapIndex = size;
            siftUp(size);
        }

        private Node poll() {
            Node result = heap[1];
            Node tail = heap[size];
            heap[size--] = null;
            result.heapIndex = -1;
            if (size > 0) {
                heap[1] = tail;
                tail.heapIndex = 1;
                siftDown(1);
            }
            return result;
        }

        private void decreaseKey(Node node) {
            if (node.heapIndex <= 0) {
                throw new IllegalStateException("Cannot update a node outside the open set");
            }
            siftUp(node.heapIndex);
        }

        private void siftUp(int index) {
            Node value = heap[index];
            while (index > 1) {
                int parentIndex = index >>> 1;
                Node parent = heap[parentIndex];
                if (compareNodes(value, parent) >= 0) {
                    break;
                }
                heap[index] = parent;
                parent.heapIndex = index;
                index = parentIndex;
            }
            heap[index] = value;
            value.heapIndex = index;
        }

        private void siftDown(int index) {
            Node value = heap[index];
            int half = size >>> 1;
            while (index <= half) {
                int child = index << 1;
                int right = child + 1;
                if (right <= size && compareNodes(heap[right], heap[child]) < 0) {
                    child = right;
                }
                Node childValue = heap[child];
                if (compareNodes(value, childValue) <= 0) {
                    break;
                }
                heap[index] = childValue;
                childValue.heapIndex = index;
                index = child;
            }
            heap[index] = value;
            value.heapIndex = index;
        }

        private void ensureCapacity(int requestedSize) {
            if (requestedSize < heap.length) {
                return;
            }
            heap = Arrays.copyOf(heap, heap.length << 1);
        }
    }

    private static final class LongObjectMap<V> {
        private static final float LOAD_FACTOR = 0.65f;

        private long[] keys;
        private Object[] values;
        private int mask;
        private int resizeAt;
        private int size;

        private LongObjectMap(int expectedSize) {
            int capacity = tableSize(expectedSize, LOAD_FACTOR);
            keys = new long[capacity];
            values = new Object[capacity];
            mask = capacity - 1;
            resizeAt = (int) (capacity * LOAD_FACTOR);
        }

        @SuppressWarnings("unchecked")
        private V get(long key) {
            int index = mix(key) & mask;
            while (true) {
                Object value = values[index];
                if (value == null) {
                    return null;
                }
                if (keys[index] == key) {
                    return (V) value;
                }
                index = (index + 1) & mask;
            }
        }

        private void put(long key, V value) {
            if (size >= resizeAt) {
                resize();
            }
            putWithoutResize(key, value);
        }

        private void putWithoutResize(long key, Object value) {
            int index = mix(key) & mask;
            while (values[index] != null) {
                if (keys[index] == key) {
                    values[index] = value;
                    return;
                }
                index = (index + 1) & mask;
            }
            keys[index] = key;
            values[index] = value;
            size++;
        }

        private void resize() {
            long[] oldKeys = keys;
            Object[] oldValues = values;
            int newCapacity = oldValues.length << 1;
            keys = new long[newCapacity];
            values = new Object[newCapacity];
            mask = newCapacity - 1;
            resizeAt = (int) (newCapacity * LOAD_FACTOR);
            size = 0;
            for (int i = 0; i < oldValues.length; i++) {
                if (oldValues[i] != null) {
                    putWithoutResize(oldKeys[i], oldValues[i]);
                }
            }
        }
    }

    private static final class LongByteMap {
        private static final byte FALSE = 1;
        private static final byte TRUE = 2;
        private static final float LOAD_FACTOR = 0.65f;

        private long[] keys;
        private byte[] values;
        private int mask;
        private int resizeAt;
        private int size;

        private LongByteMap(int expectedSize) {
            int capacity = tableSize(expectedSize, LOAD_FACTOR);
            keys = new long[capacity];
            values = new byte[capacity];
            mask = capacity - 1;
            resizeAt = (int) (capacity * LOAD_FACTOR);
        }

        private byte get(long key) {
            int index = mix(key) & mask;
            while (values[index] != 0) {
                if (keys[index] == key) {
                    return values[index];
                }
                index = (index + 1) & mask;
            }
            return 0;
        }

        private void put(long key, byte value) {
            if (size >= resizeAt) {
                resize();
            }
            putWithoutResize(key, value);
        }

        private void putWithoutResize(long key, byte value) {
            int index = mix(key) & mask;
            while (values[index] != 0) {
                if (keys[index] == key) {
                    values[index] = value;
                    return;
                }
                index = (index + 1) & mask;
            }
            keys[index] = key;
            values[index] = value;
            size++;
        }

        private void resize() {
            long[] oldKeys = keys;
            byte[] oldValues = values;
            int newCapacity = oldValues.length << 1;
            keys = new long[newCapacity];
            values = new byte[newCapacity];
            mask = newCapacity - 1;
            resizeAt = (int) (newCapacity * LOAD_FACTOR);
            size = 0;
            for (int i = 0; i < oldValues.length; i++) {
                if (oldValues[i] != 0) {
                    putWithoutResize(oldKeys[i], oldValues[i]);
                }
            }
        }
    }

    private static int tableSize(int expectedSize, float loadFactor) {
        int minimum = Math.max(2, (int) Math.ceil(expectedSize / loadFactor));
        int capacity = 1;
        while (capacity < minimum) {
            capacity <<= 1;
        }
        return capacity;
    }

    private static int mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (int) value;
    }

    private static final long[] NO_BREAKS = new long[0];

    public static class Node {
        public Location location;
        public Node parent;
        public double g;
        public double h;
        public double f;
        public boolean toBreak;
        public List<Location> blocksToBreak;
        /** How the player gets to this node from the previous one (one of the {@code MOVE_*} constants). */
        public int moveType;
        public int dirX;
        public int dirZ;
        public boolean isFenceGate;
        public boolean isDoor;
        public boolean isBanner;
        /** Height of the player's feet at this node (e.g. y + 0.5 on a slab). */
        public double feetY;
        private final int x, y, z;
        private int kind;
        private int floorFlags;
        private long[] brokenKeys = NO_BREAKS;
        private int heapIndex = -1;
        private boolean closed;

        Node(Location location, Node parent, double g, double h, boolean toBreak, int moveType) {
            this.location = location;
            this.parent = parent;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.toBreak = toBreak;
            this.blocksToBreak = Collections.emptyList();
            this.moveType = moveType;
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
            this.feetY = y;
            if (parent != null) {
                this.dirX = Integer.signum(x - parent.x);
                this.dirZ = Integer.signum(z - parent.z);
            }
        }

        private void apply(World world, MoveResult move, int dirX, int dirZ) {
            this.moveType = move.moveType;
            this.feetY = move.feet;
            this.kind = move.kind;
            this.floorFlags = move.floorFlags;
            this.dirX = dirX;
            this.dirZ = dirZ;
            if (move.breakCount == 0) {
                this.brokenKeys = NO_BREAKS;
                this.blocksToBreak = Collections.emptyList();
                this.toBreak = false;
                return;
            }
            this.brokenKeys = Arrays.copyOf(move.breaks, move.breakCount);
            List<Location> locations = new ArrayList<>(move.breakCount);
            for (long key : brokenKeys) {
                locations.add(new Location(world, keyX(key), keyY(key), keyZ(key)));
            }
            this.blocksToBreak = Collections.unmodifiableList(locations);
            this.toBreak = true;
        }

        private void populateDisplayFlags() {
            Block feet = location.getBlock();
            Block below = feet.getRelative(0, -1, 0);
            Block above = feet.getRelative(0, 1, 0);
            this.isFenceGate = isFenceGate(feet) || isFenceGate(below) || isFenceGate(above);
            this.isDoor = isDoor(feet) || isDoor(below) || isDoor(above);
            this.isBanner = isBanner(feet) || isBanner(below) || isBanner(above);
        }
    }

    private static int keyX(long key) {
        return (int) (key >> 38);
    }

    private static int keyZ(long key) {
        return (int) ((key << 26) >> 38);
    }

    private static int keyY(long key) {
        return (int) ((key << 52) >> 52);
    }
}
