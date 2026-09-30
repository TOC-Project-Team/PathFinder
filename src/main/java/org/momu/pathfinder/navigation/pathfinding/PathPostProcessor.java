package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns the goal node of a search into the path that is shown to the player:
 * <ol>
 *     <li>follows parent links back to the start,</li>
 *     <li>merges straight swims along the water surface into single {@link MoveType#WATER_SURFACE} segments.</li>
 * </ol>
 * Every node keeps the move type of the step that reaches it, so a {@link MoveType#BLOCK_JUMP} node is the
 * landing of one jump from the node before it.
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
        for (PathNode node : path) {
            node.updateDisplayFlags();
        }
        return path;
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

            if (isTraversableWaterSurface(current.location)) {
                int farthest = index;
                int surfaceY = current.location.getBlockY();
                for (int candidate = index + 1; candidate < path.size(); candidate++) {
                    PathNode candidateNode = path.get(candidate);
                    if (candidateNode.location.getBlockY() != surfaceY
                            || !isTraversableWaterSurface(candidateNode.location)) {
                        break;
                    }
                    if (hasStraightSwim(current.location, candidateNode.location)) {
                        farthest = candidate;
                    }
                }
                if (farthest > index + 1) {
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
            if (!isTraversableWaterSurface(point)) {
                return false;
            }
        }
        return true;
    }

    /** A spot where the player can swim along the surface with their head above water. */
    private static boolean isTraversableWaterSurface(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block below = feet.getRelative(0, -1, 0);

        boolean feetWater = BlockTypes.isWater(feet);
        boolean headWater = BlockTypes.isWater(head);
        boolean belowWater = BlockTypes.isWater(below);

        boolean feetOpen = feetWater || (feet.isPassable() && !BlockTypes.has(feet, BlockTypes.BODY_HAZARD));
        boolean headOpen = !headWater && head.isPassable() && !BlockTypes.has(head, BlockTypes.BODY_HAZARD);
        return feetOpen && headOpen && ((!feetWater && belowWater) || (feetWater && !headWater));
    }
}
