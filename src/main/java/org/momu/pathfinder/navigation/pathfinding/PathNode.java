package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One step of a path. During the search it is also an A* node; once the path is finished only the public
 * accessors are meaningful.
 */
public final class PathNode {
    final Location location;
    PathNode parent;
    /** Cost from the start to this node. */
    double g;
    /** Heuristic estimate from this node to the goal. */
    final double h;
    /** {@code g + h}, the open set's priority. */
    double f;
    MoveType moveType;
    /** Horizontal offset from the parent node (can be larger than 1 for block jumps). */
    int dirX;
    int dirZ;
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
        if (parent != null) {
            this.dirX = location.getBlockX() - parent.location.getBlockX();
            this.dirZ = location.getBlockZ() - parent.location.getBlockZ();
        }
    }

    /** Block position of the player's feet at this step. */
    public Location getLocation() {
        return location;
    }

    public MoveType getMoveType() {
        return moveType;
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

    void setBlocksToBreak(List<Location> blocks) {
        this.blocksToBreak = blocks == null || blocks.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(blocks));
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
        result = Integer.compare(a.location.getBlockX(), b.location.getBlockX());
        if (result != 0) {
            return result;
        }
        result = Integer.compare(a.location.getBlockZ(), b.location.getBlockZ());
        if (result != 0) {
            return result;
        }
        return Integer.compare(a.location.getBlockY(), b.location.getBlockY());
    }
}
