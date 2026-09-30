package org.momu.pathfinder.navigation.display;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.momu.pathfinder.config.PathfinderConfig;
import org.momu.pathfinder.navigation.pathfinding.BlockTypes;
import org.momu.pathfinder.navigation.pathfinding.MoveType;
import org.momu.pathfinder.navigation.pathfinding.PathNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Draws a path as dust particles for the navigating player:
 * <ul>
 *     <li>blocks that must be broken are outlined in red,</li>
 *     <li>ladders, vines, doors, fence gates and trapdoors are outlined in green, scaffolding in orange,</li>
 *     <li>segments are lines colored by how the player moves (white walk, yellow step up, purple drop,
 *     aqua climb or jump arc),</li>
 *     <li>the end of the drawn part gets a large white dot.</li>
 * </ul>
 * Only the first {@code max_particle_distance} nodes are drawn.
 */
public final class PathRenderer {
    private static final double OUTLINE_SPACING = 0.2;
    private static final float END_DOT_SIZE = 2.0f;

    private PathRenderer() {
    }

    public static void render(Player player, List<PathNode> path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        int drawCount = Math.min(path.size(), PathfinderConfig.MAX_PARTICLE_DISTANCE);
        outlineBlocksToBreak(player, path, drawCount);

        for (int i = 0; i < drawCount - 1; i++) {
            PathNode current = path.get(i);
            PathNode next = path.get(i + 1);
            if (next.getMoveType() == MoveType.BLOCK_JUMP) {
                // Each gap jump is its own arc from take-off to landing.
                ParticleShapes.jumpArc(player, center(current), center(next), Color.AQUA);
                continue;
            }
            drawStep(player, current, next);
        }

        ParticleShapes.dot(player, center(path.get(path.size() - 1)), Color.WHITE, END_DOT_SIZE);
    }

    private static void outlineBlocksToBreak(Player player, List<PathNode> path, int drawCount) {
        Set<Location> outlined = new HashSet<>();
        for (int i = 0; i < drawCount; i++) {
            for (Location block : path.get(i).getBlocksToBreak()) {
                if (outlined.add(block)) {
                    ParticleShapes.blockOutline(player, block, Color.RED, OUTLINE_SPACING);
                }
            }
        }
    }

    private static void drawStep(Player player, PathNode current, PathNode next) {
        // Steps that need breaking are already marked by the red outlines.
        if (current.requiresBreaking() || next.requiresBreaking()) {
            return;
        }
        outlineInteractiveBlocks(player, current);

        Color color = segmentColor(next.getMoveType());
        float size = PathfinderConfig.PARTICLE_SIZE;
        Location start = center(current);
        Location end = center(next);
        double distance = start.distance(end);
        if (!(distance > PathfinderConfig.PARTICLE_SPACING)) {
            ParticleShapes.dot(player, end, color, size * 1.5f);
            return;
        }

        int steps = Math.max((int) Math.ceil(distance / PathfinderConfig.PARTICLE_SPACING), 1);
        ParticleShapes.dot(player, start, color, size * 1.5f);
        for (int step = 1; step < steps; step++) {
            double ratio = (double) step / steps;
            Location point = start.clone().add((end.getX() - start.getX()) * ratio,
                    (end.getY() - start.getY()) * ratio, (end.getZ() - start.getZ()) * ratio);
            ParticleShapes.dot(player, ParticleShapes.liftOutOfWater(point), color, size * 1.2f);
        }
        ParticleShapes.dot(player, end, color, size * 1.5f);
    }

    private static Color segmentColor(MoveType move) {
        return switch (move) {
            case JUMP -> Color.YELLOW;
            case FALL -> Color.PURPLE;
            case UP, DOWN, BLOCK_JUMP -> Color.AQUA;
            default -> Color.WHITE;
        };
    }

    /** Highlights the block the player has to climb or open at this step. */
    private static void outlineInteractiveBlocks(Player player, PathNode node) {
        Block block = node.getLocation().getBlock();
        if (BlockTypes.isScaffolding(block)) {
            outlineColumn(player, node, Color.ORANGE, BlockTypes::isScaffolding, false);
        } else if (BlockTypes.isClimbable(block)) {
            outlineColumn(player, node, Color.GREEN,
                    other -> BlockTypes.isClimbable(other) && !BlockTypes.isScaffolding(other), false);
        } else if (BlockTypes.isTrapdoor(block)) {
            ParticleShapes.blockOutline(player, node.getLocation().clone(), Color.GREEN, OUTLINE_SPACING);
        } else if (node.isNearFenceGate()) {
            outlineColumn(player, node, Color.GREEN, BlockTypes::isFenceGate, true);
        } else if (node.isNearDoor()) {
            outlineColumn(player, node, Color.GREEN, BlockTypes::isAnyDoor, true);
        }
    }

    /**
     * Outlines the node's block, plus the block above (and optionally below) when it has the same type,
     * e.g. both halves of a door.
     */
    private static void outlineColumn(Player player, PathNode node, Color color, Predicate<Block> sameType,
                                      boolean includeBelow) {
        Location location = node.getLocation().clone();
        Block block = location.getBlock();
        ParticleShapes.blockOutline(player, location, color, OUTLINE_SPACING);
        if (sameType.test(block.getRelative(0, 1, 0))) {
            ParticleShapes.blockOutline(player, location.clone().add(0.0, 1.0, 0.0), color, OUTLINE_SPACING);
        }
        if (includeBelow && sameType.test(block.getRelative(0, -1, 0))) {
            ParticleShapes.blockOutline(player, location.clone().add(0.0, -1.0, 0.0), color, OUTLINE_SPACING);
        }
    }

    private static Location center(PathNode node) {
        Location location = node.getLocation();
        return new Location(location.getWorld(), location.getBlockX() + 0.5, location.getBlockY() + 0.5,
                location.getBlockZ() + 0.5);
    }
}
