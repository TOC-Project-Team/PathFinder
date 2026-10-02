package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;

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

    final int minY;
    final int maxY;
    private final BlockSource source;
    private final BlockMap<TerrainCell> cells = new BlockMap<>(8192);
    private final BlockMap<Stand> stands = new BlockMap<>(4096);
    /** Buttons, levers and pressure plates powering each iron door block that was looked at. */
    private final BlockMap<int[]> activatorsByDoor = new BlockMap<>(16);

    TerrainView(World world, BlockSource source) {
        this.minY = world.getMinHeight();
        this.maxY = world.getMaxHeight();
        this.source = source;
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

    /**
     * Same as {@link #cell(int, int, int)}, but blocks broken anywhere on the way to {@code from}, or by the move,
     * read as air.
     */
    TerrainCell cell(int x, int y, int z, PathNode from, MoveResult move) {
        if ((from != null && from.breaks != null) || (move != null && move.breakCount > 0)) {
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
        return source.cell(x, y, z);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Iron doors
    // ---------------------------------------------------------------------------------------------------------

    private static final int[][] FACES = {
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 }, { 0, 0, -1 }
    };

    /** The door opens for the player: a switch that powers it is usable from the side they come from. */
    static final int DOOR_OPENS = 0;
    /** Pass only where the door's current collision allows it (it is open, or held open by a lever). */
    static final int DOOR_AS_IS = 1;
    /** The door may be open right now, but only switches on the far side power it: do not rely on it. */
    static final int DOOR_SHUT = 2;

    /**
     * How the iron door block at (x, y, z) behaves for a player moving in direction (dx, dz). Only switches that
     * really power the door count (see {@link #switchesPowering}), and only those on the side the player comes
     * from, no more than one block to either side of the doorway.
     */
    int ironDoorPassage(int x, int y, int z, int dx, int dz) {
        int[] switches = switchesPowering(x, y, z);
        boolean momentaryElsewhere = false;
        for (int i = 0; i < switches.length; i += 4) {
            int ox = switches[i], oz = switches[i + 2];
            boolean momentary = switches[i + 3] != 0;
            boolean usable;
            if (dx != 0 && dz != 0) {
                usable = Integer.signum(-ox) == Integer.signum(dx) || Integer.signum(-oz) == Integer.signum(dz);
            } else if (dx != 0) {
                usable = Integer.signum(-ox) == Integer.signum(dx) && Math.abs(oz) <= 1;
            } else {
                usable = Integer.signum(-oz) == Integer.signum(dz) && Math.abs(ox) <= 1;
            }
            if (usable) {
                return DOOR_OPENS;
            }
            momentaryElsewhere |= momentary;
        }
        return momentaryElsewhere ? DOOR_SHUT : DOOR_AS_IS;
    }

    /**
     * Offsets (dx, dy, dz, momentary) from the given door block of every button, lever and pressure plate that
     * powers the door, following vanilla redstone rules. A switch powers the door when it is
     * <ul>
     *     <li>right next to either half of the door, or</li>
     *     <li>attached to a solid, redstone-conducting block that touches either half (a button or lever powers
     *     the block it is attached to, a pressure plate the block under it).</li>
     * </ul>
     * Longer redstone circuits (dust, repeaters...) are not followed.
     */
    private int[] switchesPowering(int x, int y, int z) {
        long key = BlockKey.of(x, y, z);
        int[] cached = activatorsByDoor.get(key);
        if (cached != null) {
            return cached;
        }
        java.util.List<int[]> found = new java.util.ArrayList<>(2);
        // Both halves of a door: the block itself and an iron door block directly above or below it.
        for (int halfY = y - 1; halfY <= y + 1; halfY++) {
            if (halfY != y && !cell(x, halfY, z).has(BlockTypes.IRON_DOOR)) {
                continue;
            }
            for (int[] face : FACES) {
                int nx = x + face[0], ny = halfY + face[1], nz = z + face[2];
                TerrainCell neighbor = cell(nx, ny, nz);
                if (neighbor.has(BlockTypes.ACTIVATOR)) {
                    addSwitch(found, nx, ny, nz, neighbor, x, y, z);
                    continue;
                }
                if (!conductsRedstone(neighbor)) {
                    continue;
                }
                for (int[] face2 : FACES) {
                    int mx = nx + face2[0], my = ny + face2[1], mz = nz + face2[2];
                    TerrainCell candidate = cell(mx, my, mz);
                    if (candidate.has(BlockTypes.ACTIVATOR) && isAttachedTo(mx, my, mz, candidate, nx, ny, nz)) {
                        addSwitch(found, mx, my, mz, candidate, x, y, z);
                    }
                }
            }
        }
        int[] result = new int[found.size() * 4];
        for (int i = 0; i < found.size(); i++) {
            System.arraycopy(found.get(i), 0, result, i * 4, 4);
        }
        activatorsByDoor.put(key, result);
        return result;
    }

    private static void addSwitch(java.util.List<int[]> found, int sx, int sy, int sz, TerrainCell cell,
                                  int doorX, int doorY, int doorZ) {
        int[] entry = { sx - doorX, sy - doorY, sz - doorZ, cell.has(BlockTypes.MOMENTARY) ? 1 : 0 };
        for (int[] existing : found) {
            if (java.util.Arrays.equals(existing, entry)) {
                return;
            }
        }
        found.add(entry);
    }

    /** Full, opaque-style blocks pass strong power from an attached switch on to their neighbors. */
    private static boolean conductsRedstone(TerrainCell cell) {
        double[] b = cell.boxes;
        return b.length == 6 && b[0] <= 0 && b[1] <= 0 && b[2] <= 0 && b[3] >= 1 && b[4] >= 1 && b[5] >= 1
                && !cell.has(BlockTypes.NOT_CONDUCTOR | BlockTypes.PLATFORM | BlockTypes.ACTIVATOR);
    }

    /** Whether the switch at (sx, sy, sz) strongly powers the block at (bx, by, bz). */
    private boolean isAttachedTo(int sx, int sy, int sz, TerrainCell cell, int bx, int by, int bz) {
        if (cell.has(BlockTypes.PRESSURE_PLATE)) {
            return bx == sx && bz == sz && by == sy - 1;
        }
        try {
            BlockData data = source.blockData(sx, sy, sz);
            if (!(data instanceof FaceAttachable attachable)) {
                return false;
            }
            int ax = sx, ay = sy, az = sz;
            switch (attachable.getAttachedFace()) {
                case FLOOR -> ay--;
                case CEILING -> ay++;
                default -> {
                    if (!(data instanceof Directional directional)) {
                        return false;
                    }
                    BlockFace facing = directional.getFacing();
                    ax -= facing.getModX();
                    ay -= facing.getModY();
                    az -= facing.getModZ();
                }
            }
            return ax == bx && ay == by && az == bz;
        } catch (Throwable ignored) {
            return false;
        }
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

    /**
     * {@link #stand(int, int, int)} as seen after the blocks broken on the way to {@code from} and by the move.
     * Only positions next to a broken block are recomputed; everything else comes from the cache.
     */
    Stand stand(int x, int y, int z, PathNode from, MoveResult move) {
        boolean changed = move != null && move.breakCount > 0;
        if (!changed && from != null && from.breaks != null) {
            changed = from.hasBrokenNear(x, z, y - 1, y + 3);
        }
        return changed ? computeStand(x, y, z, from, move) : stand(x, y, z);
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

        if (support == Double.NEGATIVE_INFINITY && !feet.has(BlockTypes.CLIMBABLE | BlockTypes.DOOR)
                && below.has(BlockTypes.LADDER | BlockTypes.DOOR)) {
            // A ladder's or door's panel sits at the edge of its block, but the player can stand on its top edge
            // and jump from there. Vines have no collision, so this does not apply to them.
            double[] boxes = below.boxes;
            for (int k = 0; k < boxes.length; k += 6) {
                double top = y - 1 + boxes[k + 4];
                if (top >= y - EPS && top < y + 1 - EPS && top > support) {
                    support = top;
                    floorFlags = below.flags;
                }
            }
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
