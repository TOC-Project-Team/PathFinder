package org.momu.pathfinder.golden;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.momu.pathfinder.config.PathfinderConfig;
import org.momu.pathfinder.navigation.algorithm.Pathfinder;
import org.momu.pathfinder.navigation.effect.ParticleGen;
import org.momu.pathfinder.navigation.runtime.PathFinding;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter between the golden-master tests and the production navigation code.
 * Baseline version: drives the pre-refactor code (render and target adjustment copied verbatim from
 * PathFinding's scheduled task, which was not callable on its own).
 */
final class NavigationProbe {
    private NavigationProbe() {
    }

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

    static String describePath(Location start, Location end, Player player) {
        List<Pathfinder.Node> path = Pathfinder.findPath(start, end, player);
        if (path == null) {
            return "  <no path>\n";
        }
        StringBuilder out = new StringBuilder();
        for (Pathfinder.Node node : path) {
            out.append("  ").append(node.location.getBlockX()).append(',').append(node.location.getBlockY())
                    .append(',').append(node.location.getBlockZ())
                    .append(" move=").append(node.moveType)
                    .append(" dir=").append(node.dirX).append(',').append(node.dirZ)
                    .append(" break=").append(node.toBreak);
            for (Location block : node.blocksToBreak) {
                out.append(' ').append(block.getBlockX()).append('/').append(block.getBlockY()).append('/')
                        .append(block.getBlockZ());
            }
            out.append(" flags=").append(node.isDoor ? 'D' : '-').append(node.isFenceGate ? 'G' : '-')
                    .append(node.isBanner ? 'B' : '-').append('\n');
        }
        return out.toString();
    }

    static boolean isSafeLanding(Location location) {
        return PathFinding.isSafeLanding(location);
    }

    static Location findSafeLandingNearWater(Location location) {
        return PathFinding.findSafeLandingNearWater(location);
    }

    static Location findWaterLanding(Location from, Location target) {
        return PathFinding.findWaterLanding(from, target);
    }

    /** Verbatim copy of the water handling applied to each refresh's target in PathFinding. */
    static Location adjustTargetForWater(Location targetLoc) {
        if (targetLoc.getBlock().getType().name().contains((CharSequence) "WATER")) {
            Location safeLanding = PathFinding.findSafeLandingNearWater(targetLoc);
            if (safeLanding != null) {
                targetLoc = safeLanding;
            } else {
                Location waterSurface = targetLoc.clone();
                int maxIterations = 50;
                int iterations = 0;
                while (waterSurface.getBlock().getType().name().contains((CharSequence) "WATER")
                        && waterSurface.getBlockY() < waterSurface.getWorld().getMaxHeight() - 1
                        && iterations < maxIterations) {
                    waterSurface.add(0.0, 1.0, 0.0);
                    iterations++;
                }
                if (!waterSurface.getBlock().getType().name()
                        .contains((CharSequence) "WATER")) {
                    targetLoc = waterSurface;
                    if (waterSurface.clone().add(0.0, -1.0, 0.0).getBlock().getType().name()
                            .contains((CharSequence) "WATER")) {
                        targetLoc.setY((double) waterSurface.getBlockY());
                    }
                } else {
                    int radius = 3;
                    boolean found = false;
                    int checkedLocations = 0;
                    int maxLocationsToCheck = 50;

                    block1: for (int r = 1; r <= radius && !found
                            && checkedLocations < maxLocationsToCheck; ++r) {
                        int dx = -r;
                        while (true) {
                            if (dx > r || found || checkedLocations >= maxLocationsToCheck)
                                continue block1;
                            for (int dz = -r; dz <= r && !found
                                    && checkedLocations < maxLocationsToCheck; ++dz) {
                                checkedLocations++;
                                Location checkLoc;
                                if (Math.abs((int) dx) != r && Math.abs((int) dz) != r
                                        || (checkLoc = targetLoc.clone().add((double) dx, 0.0,
                                                (double) dz)).getBlock().getType().name()
                                                .contains((CharSequence) "WATER")
                                        || !PathFinding.isSafeLanding(checkLoc))
                                    continue;
                                targetLoc = checkLoc;
                                found = true;
                                break;
                            }
                            ++dx;
                        }
                    }
                }
            }
        }
        return targetLoc;
    }

    /** Verbatim copy of the particle drawing done in PathFinding's scheduled task. */
    static void renderPath(Player player, Location start, Location end) {
        List<?> finalPath = Pathfinder.findPath(start, end, player);
        if (finalPath != null && !finalPath.isEmpty()) {
            int maxDrawNodes = Math.min((int) finalPath.size(),
                    (int) PathfinderConfig.MAX_PARTICLE_DISTANCE);
            Set<Location> outlinedBreakBlocks = new HashSet<>();
            for (int nodeIndex = 0; nodeIndex < maxDrawNodes; nodeIndex++) {
                Pathfinder.Node node = (Pathfinder.Node) finalPath.get(nodeIndex);
                for (Location blockLoc : node.blocksToBreak) {
                    if (outlinedBreakBlocks.add(blockLoc)) {
                        ParticleGen.drawBlockOutline(player, blockLoc, Color.RED, 0.2);
                    }
                }
            }

            for (int i = 0; i < maxDrawNodes - 1; ++i) {
                Pathfinder.Node currentNode = (Pathfinder.Node) finalPath.get(i);
                Pathfinder.Node nextNode = (Pathfinder.Node) finalPath.get(i + 1);

                if (currentNode.moveType == 5) {
                    int jumpEndIndex = i + 1;
                    while (jumpEndIndex < maxDrawNodes - 1 &&
                            ((Pathfinder.Node) finalPath.get(jumpEndIndex)).moveType == 5) {
                        jumpEndIndex++;
                    }
                    Pathfinder.Node jumpStartNode = currentNode;
                    Pathfinder.Node jumpEndNode = (Pathfinder.Node) finalPath.get(jumpEndIndex);
                    Location jumpStart = new Location(jumpStartNode.location.getWorld(),
                            jumpStartNode.location.getBlockX() + 0.5, jumpStartNode.location.getBlockY() + 0.5,
                            jumpStartNode.location.getBlockZ() + 0.5);
                    Location jumpEnd = new Location(jumpEndNode.location.getWorld(),
                            jumpEndNode.location.getBlockX() + 0.5, jumpEndNode.location.getBlockY() + 0.5,
                            jumpEndNode.location.getBlockZ() + 0.5);
                    PathFinding.generateJumpParabola(player, jumpStart, jumpEnd);
                    i = jumpEndIndex - 1;
                    continue;
                }
                Location segStart = new Location(currentNode.location.getWorld(),
                        currentNode.location.getBlockX() + 0.5, currentNode.location.getBlockY() + 0.5,
                        currentNode.location.getBlockZ() + 0.5);
                Location segEnd = new Location(nextNode.location.getWorld(),
                        nextNode.location.getBlockX() + 0.5, nextNode.location.getBlockY() + 0.5,
                        nextNode.location.getBlockZ() + 0.5);
                double distance = segStart.distance(segEnd);
                Particle particleType = Particle.DUST;
                Color particleColor = Color.WHITE;
                float particleSize = PathfinderConfig.PARTICLE_SIZE;
                switch (nextNode.moveType) {
                    case 3: particleColor = Color.YELLOW; break;
                    case 4: particleColor = Color.PURPLE; break;
                    case 1:
                    case 2: particleColor = Color.AQUA; break;
                    case 5: particleColor = Color.AQUA; break;
                }
                boolean breakTransition = currentNode.toBreak || nextNode.toBreak;
                if (!breakTransition) {
                    Block block = currentNode.location.getBlock();
                    if (Pathfinder.isLadder(block)) {
                        Location blockLoc = currentNode.location.clone();
                        ParticleGen.drawBlockOutline(player, blockLoc, Color.GREEN, 0.2);
                        Block blockAbove = block.getRelative(0, 1, 0);
                        if (Pathfinder.isLadder(blockAbove)) {
                            ParticleGen.drawBlockOutline(player, blockLoc.clone().add(0.0, 1.0, 0.0), Color.GREEN, 0.2);
                        }
                    } else if (Pathfinder.isScaffolding(block)) {
                        Location blockLoc = currentNode.location.clone();
                        ParticleGen.drawBlockOutline(player, blockLoc, Color.ORANGE, 0.2);
                        Block blockAbove = block.getRelative(0, 1, 0);
                        if (Pathfinder.isScaffolding(blockAbove)) {
                            ParticleGen.drawBlockOutline(player, blockLoc.clone().add(0.0, 1.0, 0.0), Color.ORANGE, 0.2);
                        }
                    } else if (Pathfinder.isTrapdoor(block)) {
                        ParticleGen.drawBlockOutline(player, currentNode.location.clone(), Color.GREEN, 0.2);
                    } else if (currentNode.isFenceGate) {
                        Location blockLoc = currentNode.location.clone();
                        ParticleGen.drawBlockOutline(player, blockLoc, Color.GREEN, 0.2);
                        Block blockAbove = block.getRelative(0, 1, 0);
                        if (Pathfinder.isFenceGate(blockAbove)) {
                            ParticleGen.drawBlockOutline(player, blockLoc.clone().add(0.0, 1.0, 0.0), Color.GREEN, 0.2);
                        }
                        Block blockBelow = block.getRelative(0, -1, 0);
                        if (Pathfinder.isFenceGate(blockBelow)) {
                            ParticleGen.drawBlockOutline(player, blockLoc.clone().add(0.0, -1.0, 0.0), Color.GREEN, 0.2);
                        }
                    } else if (currentNode.isDoor) {
                        Location blockLoc = currentNode.location.clone();
                        ParticleGen.drawBlockOutline(player, blockLoc, Color.GREEN, 0.2);
                        Block blockAbove = block.getRelative(0, 1, 0);
                        if (Pathfinder.isDoor(blockAbove)) {
                            ParticleGen.drawBlockOutline(player, blockLoc.clone().add(0.0, 1.0, 0.0), Color.GREEN, 0.2);
                        }
                        Block blockBelow = block.getRelative(0, -1, 0);
                        if (Pathfinder.isDoor(blockBelow)) {
                            ParticleGen.drawBlockOutline(player, blockLoc.clone().add(0.0, -1.0, 0.0), Color.GREEN, 0.2);
                        }
                    }
                }
                if (breakTransition) {
                    continue;
                }
                if (!(distance > PathfinderConfig.PARTICLE_SPACING)) {
                    player.spawnParticle(particleType, segEnd, 1, 0.0, 0.0, 0.0, 0.0,
                            (Object) new Particle.DustOptions(particleColor, particleSize * 1.5f));
                    continue;
                }
                double totalDistance = segStart.distance(segEnd);
                int steps = Math.max((int) Math.ceil(totalDistance / PathfinderConfig.PARTICLE_SPACING), 1);
                player.spawnParticle(particleType, segStart, 1, 0.0, 0.0, 0.0, 0.0,
                        (Object) new Particle.DustOptions(particleColor, particleSize * 1.5f));
                for (int step = 1; step < steps; ++step) {
                    double ratio = (double) step / (double) steps;
                    if (ratio >= 1.0)
                        break;
                    Location intermediateLoc = segStart.clone().add(
                            (segEnd.getX() - segStart.getX()) * ratio,
                            (segEnd.getY() - segStart.getY()) * ratio,
                            (segEnd.getZ() - segStart.getZ()) * ratio);
                    Location particleLoc = ParticleGen.adjustParticleLocationForWater(intermediateLoc);
                    float size = particleSize * 1.2f;
                    player.spawnParticle(particleType, particleLoc, 1, 0.0, 0.0, 0.0, 0.0,
                            (Object) new Particle.DustOptions(particleColor, size));
                }
                player.spawnParticle(particleType, segEnd, 1, 0.0, 0.0, 0.0, 0.0,
                        (Object) new Particle.DustOptions(particleColor, particleSize * 1.5f));
            }

            Pathfinder.Node lastNode = (Pathfinder.Node) finalPath.get(finalPath.size() - 1);
            Location finalLocation = new Location(lastNode.location.getWorld(),
                    lastNode.location.getBlockX() + 0.5, lastNode.location.getBlockY() + 0.5,
                    lastNode.location.getBlockZ() + 0.5);
            player.spawnParticle(Particle.DUST, finalLocation, 1, 0.0, 0.0, 0.0, 0.0,
                    (Object) new Particle.DustOptions(Color.WHITE, 2.0f));
        }
    }

}
