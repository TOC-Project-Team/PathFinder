package org.momu.pathfinder.golden;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.momu.pathfinder.config.PathfinderConfig;
import org.momu.pathfinder.navigation.display.PathRenderer;
import org.momu.pathfinder.navigation.pathfinding.AStarPathfinder;
import org.momu.pathfinder.navigation.pathfinding.PathNode;
import org.momu.pathfinder.navigation.runtime.WaterLanding;

import java.util.List;

/**
 * Adapter between the golden-master tests and the production navigation code.
 */
final class NavigationProbe {
    private NavigationProbe() {
    }

    /** The defaults shipped in pathfinder.yml. */
    static void applyDefaultSettings() {
        PathfinderConfig.MAX_SEARCH_RADIUS = 3000;
        PathfinderConfig.MAX_ITERATIONS = 4000;
        PathfinderConfig.PARTICLE_SPACING = 0.5;
        PathfinderConfig.MAX_PARTICLE_DISTANCE = 30;
        PathfinderConfig.PARTICLE_SIZE = 1.0f;
        PathfinderConfig.PATH_REFRESH_TICKS = 15;
        PathfinderConfig.DIAGONAL_COST = 1.5;
        PathfinderConfig.STRAIGHT_COST = 1.0;
        PathfinderConfig.RIGHT_ANGLE_TURN_COST = 0.5;
        PathfinderConfig.DIAGONAL_TURN_COST = 1.0;
        PathfinderConfig.BREAK_BLOCK_COST = 100.0;
        PathfinderConfig.WATER_COST = 10.0;
        PathfinderConfig.DOOR_COST = 0.0;
        PathfinderConfig.TRAPDOOR_COST = 6.0;
        PathfinderConfig.SCAFFOLDING_COST = 0.0;
        PathfinderConfig.JUMP_COST = 0.0;
        PathfinderConfig.VERTICAL_COST = 1.0;
        PathfinderConfig.FALL_COST = 2.0;
        PathfinderConfig.BLOCK_JUMP_COST = 1.0;
        PathfinderConfig.MAX_BLOCK_JUMP_DISTANCE = 4;
        PathfinderConfig.MAX_SAFE_FALL_HEIGHT = 4;
    }

    static String describePath(Location start, Location end) {
        List<PathNode> path = AStarPathfinder.findPath(start, end);
        if (path == null) {
            return "  <no path>\n";
        }
        StringBuilder out = new StringBuilder();
        for (PathNode node : path) {
            Location at = node.getLocation();
            out.append("  ").append(at.getBlockX()).append(',').append(at.getBlockY()).append(',').append(at.getBlockZ())
                    .append(" move=").append(node.getMoveType().ordinal())
                    .append(" dir=").append(node.getDirX()).append(',').append(node.getDirZ())
                    .append(" break=").append(node.requiresBreaking());
            for (Location block : node.getBlocksToBreak()) {
                out.append(' ').append(block.getBlockX()).append('/').append(block.getBlockY()).append('/')
                        .append(block.getBlockZ());
            }
            out.append(" flags=").append(node.isNearDoor() ? 'D' : '-').append(node.isNearFenceGate() ? 'G' : '-')
                    .append(node.isNearBanner() ? 'B' : '-').append('\n');
        }
        return out.toString();
    }

    static boolean isSafeLanding(Location location) {
        return WaterLanding.isSafeLanding(location);
    }

    static Location findSafeLandingNearWater(Location location) {
        return WaterLanding.findSafeLandingNearWater(location);
    }

    static Location adjustTargetForWater(Location target) {
        return WaterLanding.adjustTarget(target);
    }

    static void renderPath(Player player, Location start, Location end) {
        PathRenderer.render(player, AStarPathfinder.findPath(start, end));
    }
}
