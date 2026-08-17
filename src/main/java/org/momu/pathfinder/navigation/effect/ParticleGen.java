package org.momu.pathfinder.navigation.effect;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class ParticleGen {
    private static final int[][] BLOCK_EDGES = {
            { 0, 0, 0, 1, 0, 0 },
            { 0, 0, 1, 1, 0, 1 },
            { 0, 0, 0, 0, 0, 1 },
            { 1, 0, 0, 1, 0, 1 },
            { 0, 1, 0, 1, 1, 0 },
            { 0, 1, 1, 1, 1, 1 },
            { 0, 1, 0, 0, 1, 1 },
            { 1, 1, 0, 1, 1, 1 },
            { 0, 0, 0, 0, 1, 0 },
            { 1, 0, 0, 1, 1, 0 },
            { 0, 0, 1, 0, 1, 1 },
            { 1, 0, 1, 1, 1, 1 }
    };

    public static Location adjustParticleLocationForWater(Location location) {
        if (location == null) return location;
        Location adjustedLoc = location.clone();
        Block currentBlock = adjustedLoc.getBlock();
        if (currentBlock.getType().name().contains("WATER")) {
            while (adjustedLoc.getBlockY() < adjustedLoc.getWorld().getMaxHeight() - 1) {
                adjustedLoc.add(0.0, 1.0, 0.0);
                Block checkBlock = adjustedLoc.getBlock();
                if (checkBlock.getType().name().contains("WATER") || !checkBlock.isPassable()) continue;
                adjustedLoc.add(0.0, -0.3, 0.0);
                break;
            }
        }
        return adjustedLoc;
    }

    public static void drawBlockOutline(Player player, Location blockLoc, Color color, double particleDensity) {
        int blockX = blockLoc.getBlockX();
        int blockY = blockLoc.getBlockY();
        int blockZ = blockLoc.getBlockZ();
        double actualDensity = particleDensity > 0.0
                ? particleDensity
                : org.momu.pathfinder.config.PathfinderConfig.PARTICLE_SPACING;
        int steps = Math.max(1, (int) Math.ceil(1.0 / actualDensity));

        for (int[] edge : BLOCK_EDGES) {
            Location edgeStart = new Location(blockLoc.getWorld(),
                    blockX + edge[0], blockY + edge[1], blockZ + edge[2]);
            Location edgeEnd = new Location(blockLoc.getWorld(),
                    blockX + edge[3], blockY + edge[4], blockZ + edge[5]);
            Vector direction = edgeEnd.toVector().subtract(edgeStart.toVector());
            for (int step = 0; step <= steps; step++) {
                double ratio = (double) step / steps;
                Location particleLoc = edgeStart.clone().add(direction.clone().multiply(ratio));
                player.spawnParticle(Particle.DUST, particleLoc, 1, 0.0, 0.0, 0.0, 0.0,
                        (Object) new Particle.DustOptions(color, 0.9f));
            }
        }
    }
}
