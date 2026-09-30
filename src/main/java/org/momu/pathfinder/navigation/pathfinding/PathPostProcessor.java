package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns the goal node of a search into the path that is shown to the player:
 * <ol>
 *     <li>follows parent links back to the start,</li>
 *     <li>merges straight swims along the water surface into single {@link MoveType#WATER_SURFACE} segments,</li>
 *     <li>labels jump segments and fills in the blocks a block jump passes over, so every step is at most one
 *     block apart and the renderer can draw arcs.</li>
 * </ol>
 */
final class PathPostProcessor {
    private PathPostProcessor() {
    }

    static List<PathNode> buildPath(PathNode goal) {
        List<PathNode> path = new ArrayList<>();
        for (PathNode node = goal; node != null; node = node.parent) {
            path.add(node);
        }
        Collections.reverse(path);
        path = mergeWaterSurfaceRuns(path);
        List<PathNode> expanded = expandJumps(path);
        for (PathNode node : expanded) {
            node.updateDisplayFlags();
        }
        return expanded;
    }

    private static List<PathNode> expandJumps(List<PathNode> path) {
        List<PathNode> expanded = new ArrayList<>();
        if (path.isEmpty()) {
            return expanded;
        }

        PathNode first = path.get(0);
        if (path.size() > 1) {
            PathNode second = path.get(1);
            int dx = second.location.getBlockX() - first.location.getBlockX();
            int dz = second.location.getBlockZ() - first.location.getBlockZ();
            int distance = Math.max(Math.abs(dx), Math.abs(dz));
            if (second.moveType == MoveType.WATER_SURFACE) {
                label(first, MoveType.WATER_SURFACE, dx, dz);
            } else if (second.moveType == MoveType.BLOCK_JUMP || distance > 1) {
                label(first, MoveType.BLOCK_JUMP, dx, dz);
            }
        }
        expanded.add(first);

        for (int i = 0; i < path.size() - 1; i++) {
            PathNode current = path.get(i);
            PathNode next = path.get(i + 1);
            int dx = next.location.getBlockX() - current.location.getBlockX();
            int dz = next.location.getBlockZ() - current.location.getBlockZ();
            int dy = next.location.getBlockY() - current.location.getBlockY();
            int distance = Math.max(Math.abs(dx), Math.abs(dz));

            if (next.moveType == MoveType.WATER_SURFACE) {
                label(current, MoveType.WATER_SURFACE, dx, dz);
                next.dirX = current.dirX;
                next.dirZ = current.dirZ;
            } else if (distance > 1 || next.moveType == MoveType.BLOCK_JUMP) {
                label(current, MoveType.BLOCK_JUMP, dx, dz);
                label(next, MoveType.BLOCK_JUMP, dx, dz);
                addJumpedOverBlocks(expanded, current, distance);
            } else if (next.moveType == MoveType.JUMP || dy > 0) {
                label(current, MoveType.JUMP, dx, dz);
                label(next, MoveType.JUMP, dx, dz);
            }
            expanded.add(next);
        }
        return expanded;
    }

    /** Sets the move type and a unit direction (-1, 0 or 1 per axis). */
    private static void label(PathNode node, MoveType moveType, int dx, int dz) {
        node.moveType = moveType;
        node.dirX = Integer.compare(dx, 0);
        node.dirZ = Integer.compare(dz, 0);
    }

    /** Inserts a node for every block between the two ends of a block jump. */
    private static void addJumpedOverBlocks(List<PathNode> expanded, PathNode from, int distance) {
        for (int step = 1; step < distance; step++) {
            Location location = new Location(from.location.getWorld(),
                    from.location.getBlockX() + from.dirX * step,
                    from.location.getBlockY(),
                    from.location.getBlockZ() + from.dirZ * step);
            PathNode node = new PathNode(location, from, 0, 0, MoveType.BLOCK_JUMP);
            node.dirX = from.dirX;
            node.dirZ = from.dirZ;
            expanded.add(node);
        }
    }

    /**
     * Where the path swims along the water surface, skips intermediate nodes that have a clear straight line
     * between them.
     */
    private static List<PathNode> mergeWaterSurfaceRuns(List<PathNode> path) {
        if (path.size() < 3) {
            return path;
        }

        List<PathNode> merged = new ArrayList<>(path.size());
        merged.add(path.get(0));
        int index = 0;
        while (index < path.size() - 1) {
            PathNode current = path.get(index);
            int nextIndex = index + 1;

            if (TerrainRules.isTraversableWaterSurface(current.location)) {
                int farthest = index;
                int surfaceY = current.location.getBlockY();
                for (int candidate = index + 1; candidate < path.size(); candidate++) {
                    PathNode candidateNode = path.get(candidate);
                    if (candidateNode.location.getBlockY() != surfaceY
                            || !TerrainRules.isTraversableWaterSurface(candidateNode.location)) {
                        break;
                    }
                    if (hasStraightSwim(current.location, candidateNode.location)) {
                        farthest = candidate;
                    }
                }
                if (farthest > index + 1) {
                    current.moveType = MoveType.WATER_SURFACE;
                    path.get(farthest).moveType = MoveType.WATER_SURFACE;
                    nextIndex = farthest;
                }
            }

            merged.add(path.get(nextIndex));
            index = nextIndex;
        }
        return merged;
    }

    /** Samples the straight line between two surface spots every quarter block. */
    private static boolean hasStraightSwim(Location start, Location end) {
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
            Location point = new Location(start.getWorld(), startX + deltaX * ratio, start.getBlockY(),
                    startZ + deltaZ * ratio);
            if (!TerrainRules.isTraversableWaterSurface(point)) {
                return false;
            }
        }
        return true;
    }
}
