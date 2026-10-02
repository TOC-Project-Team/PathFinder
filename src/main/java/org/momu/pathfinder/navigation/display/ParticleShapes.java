package org.momu.pathfinder.navigation.display;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.momu.pathfinder.config.PathfinderConfig;
import org.momu.pathfinder.navigation.pathfinding.BlockTypes;
import org.momu.pathfinder.navigation.runtime.Scheduling;

/**
 * Dust particle shapes drawn for a single player: dots, lines, block outlines and jump arcs.
 */
public final class ParticleShapes {
    /** The 12 edges of a unit cube as {x1, y1, z1, x2, y2, z2}. */
    private static final int[][] CUBE_EDGES = {
            { 0, 0, 0, 1, 0, 0 }, { 0, 0, 1, 1, 0, 1 }, { 0, 0, 0, 0, 0, 1 }, { 1, 0, 0, 1, 0, 1 },
            { 0, 1, 0, 1, 1, 0 }, { 0, 1, 1, 1, 1, 1 }, { 0, 1, 0, 0, 1, 1 }, { 1, 1, 0, 1, 1, 1 },
            { 0, 0, 0, 0, 1, 0 }, { 1, 0, 0, 1, 1, 0 }, { 0, 0, 1, 0, 1, 1 }, { 1, 0, 1, 1, 1, 1 }
    };
    private static final float OUTLINE_SIZE = 0.9f;
    /** Height of a jump arc above the straight line between take-off and landing, in blocks. */
    private static final double JUMP_ARC_HEIGHT = 0.5;
    private static final int MIN_JUMP_ARC_STEPS = 15;

    private ParticleShapes() {
    }

    public static void dot(Player player, Location location, Color color, float size) {
        player.spawnParticle(Particle.DUST, location, 1, 0.0, 0.0, 0.0, 0.0, new Particle.DustOptions(color, size));
    }

    /**
     * Outlines the block at {@code block}.
     *
     * @param spacing distance between particles along each edge; {@code <= 0} uses {@code particle_spacing}
     */
    public static void blockOutline(Player player, Location block, Color color, double spacing) {
        int x = block.getBlockX();
        int y = block.getBlockY();
        int z = block.getBlockZ();
        double actualSpacing = spacing > 0.0 ? spacing : PathfinderConfig.PARTICLE_SPACING;
        int steps = Math.max(1, (int) Math.ceil(1.0 / actualSpacing));

        for (int[] edge : CUBE_EDGES) {
            Location start = new Location(block.getWorld(), x + edge[0], y + edge[1], z + edge[2]);
            Location end = new Location(block.getWorld(), x + edge[3], y + edge[4], z + edge[5]);
            Vector direction = end.toVector().subtract(start.toVector());
            for (int step = 0; step <= steps; step++) {
                double ratio = (double) step / steps;
                dot(player, start.clone().add(direction.clone().multiply(ratio)), color, OUTLINE_SIZE);
            }
        }
    }

    /** Draws a parabola from {@code start} to {@code end}, used for block jumps. */
    public static void jumpArc(Player player, Location start, Location end, Color color) {
        double horizontalDistance = Math.sqrt(Math.pow(end.getX() - start.getX(), 2)
                + Math.pow(end.getZ() - start.getZ(), 2));
        double verticalDistance = end.getY() - start.getY();
        int steps = Math.max((int) Math.ceil(horizontalDistance / (PathfinderConfig.PARTICLE_SPACING * 0.2d)),
                MIN_JUMP_ARC_STEPS);
        float baseSize = PathfinderConfig.PARTICLE_SIZE;

        for (int step = 0; step <= steps; step++) {
            double t = (double) step / steps;
            double x = start.getX() + (end.getX() - start.getX()) * t;
            double z = start.getZ() + (end.getZ() - start.getZ()) * t;
            double y = start.getY() + verticalDistance * t + JUMP_ARC_HEIGHT * 4.0d * t * (1.0d - t);
            Location point = liftOutOfWater(new Location(start.getWorld(), x, y, z));
            float size = (step == 0 || step == steps) ? baseSize * 1.5f : baseSize * 1.2f;
            dot(player, point, color, size);
        }
    }

    /** Particles are invisible under water, so points inside water are moved just below the surface. */
    public static Location liftOutOfWater(Location location) {
        if (location == null) {
            return null;
        }
        Location adjusted = location.clone();
        if (!Scheduling.isLoaded(adjusted) || !BlockTypes.isWater(adjusted.getBlock())) {
            return adjusted;
        }
        while (adjusted.getBlockY() < adjusted.getWorld().getMaxHeight() - 1) {
            adjusted.add(0.0, 1.0, 0.0);
            Block block = adjusted.getBlock();
            if (!BlockTypes.isWater(block) && block.isPassable()) {
                adjusted.add(0.0, -0.3, 0.0);
                break;
            }
        }
        return adjusted;
    }
}
