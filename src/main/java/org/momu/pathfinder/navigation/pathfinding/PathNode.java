package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One step of a path. During the search it is also an A* node; once the path is finished only the public
 * accessors are meaningful.
 */
public final class PathNode {
    private static final long[] NO_BREAKS = new long[0];

    final Location location;
    final int x, y, z;
    PathNode parent;
    /** Cost from the start to this node. */
    double g;
    /** Heuristic estimate from this node to the goal. */
    final double h;
    /** {@code g + h}, the open set's priority. */
    double f;
    MoveType moveType;
    /** Direction of travel from the parent node (-1, 0 or 1 per axis), used to price turns. */
    int dirX;
    int dirZ;
    /** Height of the player's feet, e.g. {@code y + 0.5} on a slab. */
    double feetY;
    /** {@link TerrainView.Stand} kind flags: standing, swimming, climbing. */
    int kind;
    /** {@link BlockTypes} flags of the block the player stands on. */
    int floorFlags;
    /** Blocks broken to get here, which read as air for the next move. */
    long[] brokenKeys = NO_BREAKS;
    private List<Location> blocksToBreak = Collections.emptyList();
    private boolean nearDoor;
    private boolean nearFenceGate;
    private boolean nearBanner;

    /** Position in {@link IndexedOpenSet}'s heap, or -1 when not queued. */
    int heapIndex = -1;
    boolean closed;

    PathNode(Location location, PathNode parent, double g, double h, MoveType moveType) {
        this.location = location;
        this.parent = parent;
        this.g = g;
        this.h = h;
        this.f = g + h;
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

    /** Takes over how a move arrives here. */
    void apply(MoveResult move, int dirX, int dirZ) {
        this.moveType = move.moveType;
        this.feetY = move.feet;
        this.kind = move.kind;
        this.floorFlags = move.floorFlags;
        this.dirX = dirX;
        this.dirZ = dirZ;
        if (move.breakCount == 0) {
            this.brokenKeys = NO_BREAKS;
            this.blocksToBreak = Collections.emptyList();
            return;
        }
        this.brokenKeys = Arrays.copyOf(move.breaks, move.breakCount);
        List<Location> locations = new ArrayList<>(move.breakCount);
        for (long key : brokenKeys) {
            locations.add(new Location(location.getWorld(), BlockKey.x(key), BlockKey.y(key), BlockKey.z(key)));
        }
        this.blocksToBreak = Collections.unmodifiableList(locations);
    }

    boolean isBroken(long key) {
        for (long broken : brokenKeys) {
            if (broken == key) {
                return true;
            }
        }
        return false;
    }

    /** Block position of the player's feet at this step. */
    public Location getLocation() {
        return location;
    }

    /** How the player gets here from the previous step. */
    public MoveType getMoveType() {
        return moveType;
    }

    /** Height of the player's feet at this step, e.g. {@code y + 0.5} on a slab. */
    public double getFeetY() {
        return feetY;
    }

    public int getDirX() {
        return dirX;
    }

    public int getDirZ() {
        return dirZ;
    }

    /** Blocks the player has to break to make this step. */
    public List<Location> getBlocksToBreak() {
        return blocksToBreak;
    }

    public boolean requiresBreaking() {
        return !blocksToBreak.isEmpty();
    }

    /** Whether a door (or trapdoor) is at, above or below this step. */
    public boolean isNearDoor() {
        return nearDoor;
    }

    public boolean isNearFenceGate() {
        return nearFenceGate;
    }

    public boolean isNearBanner() {
        return nearBanner;
    }

    void updateDisplayFlags() {
        Block feet = location.getBlock();
        Block below = feet.getRelative(0, -1, 0);
        Block above = feet.getRelative(0, 1, 0);
        nearFenceGate = BlockTypes.isFenceGate(feet) || BlockTypes.isFenceGate(below) || BlockTypes.isFenceGate(above);
        nearDoor = BlockTypes.isAnyDoor(feet) || BlockTypes.isAnyDoor(below) || BlockTypes.isAnyDoor(above);
        nearBanner = BlockTypes.isBanner(feet) || BlockTypes.isBanner(below) || BlockTypes.isBanner(above);
    }

    /** Open-set order: lowest f first, then highest g, then position for determinism. */
    static int compare(PathNode a, PathNode b) {
        int result = Double.compare(a.f, b.f);
        if (result != 0) {
            return result;
        }
        result = Double.compare(b.g, a.g);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(a.x, b.x);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(a.z, b.z);
        if (result != 0) {
            return result;
        }
        return Integer.compare(a.y, b.y);
    }
}
