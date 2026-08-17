package org.momu.pathfinder.navigation.algorithm;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.momu.pathfinder.bootstrap.PathFinderPlugin;
import org.momu.pathfinder.config.PathfinderConfig;

import java.util.*;

public class Pathfinder {

    public static void loadConfig(PathFinderPlugin plugin) {
        PathfinderConfig.loadConfig(plugin);
    }

    public static void clearBannerCache() {
        // Material classifications are immutable for the lifetime of the server.
    }

    public static int getMaxSearchRadius() {
        return PathfinderConfig.MAX_SEARCH_RADIUS;
    }

    private static final Set<Material> OBSTACLES = new HashSet<>();
    private static final int[] MOVE_DX = { 0, 1, -1, 0, 1, -1, 1, -1, 0 };
    private static final int[] MOVE_DZ = { 1, 0, 0, -1, 1, 1, -1, -1, 0 };
    private static final int[] JUMP_DX = { 0, 1, -1, 0 };
    private static final int[] JUMP_DZ = { 1, 0, 0, -1 };
    private static final int[][] HAZARD_DIRECTIONS = {
            { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, -1, 0 }
    };
    private static final boolean[] DOOR_TYPES = new boolean[Material.values().length];
    private static final boolean[] TRAPDOOR_TYPES = new boolean[Material.values().length];
    private static final boolean[] IRON_TRAPDOOR_TYPES = new boolean[Material.values().length];
    private static final boolean[] BANNER_TYPES = new boolean[Material.values().length];
    private static final boolean[] LADDER_TYPES = new boolean[Material.values().length];
    private static final boolean[] SCAFFOLDING_TYPES = new boolean[Material.values().length];

    static {
        OBSTACLES.add(Material.CACTUS);
        OBSTACLES.add(Material.COBWEB);
        OBSTACLES.add(Material.SWEET_BERRY_BUSH);
        OBSTACLES.add(Material.VINE);
        OBSTACLES.add(Material.POWDER_SNOW);
        OBSTACLES.add(Material.POINTED_DRIPSTONE);

        for (Material material : Material.values()) {
            String name = material.name();
            int ordinal = material.ordinal();
            DOOR_TYPES[ordinal] = name.contains("DOOR");
            TRAPDOOR_TYPES[ordinal] = name.contains("TRAPDOOR");
            IRON_TRAPDOOR_TYPES[ordinal] = name.contains("IRON_TRAPDOOR");
            BANNER_TYPES[ordinal] = name.contains("BANNER");
            LADDER_TYPES[ordinal] = name.contains("LADDER");
            SCAFFOLDING_TYPES[ordinal] = name.contains("SCAFFOLDING");
        }
    }

    public static final int MOVE_HORIZONTAL = 0;
    public static final int MOVE_UP = 1;
    public static final int MOVE_DOWN = 2;
    public static final int MOVE_JUMP = 3;
    public static final int MOVE_FALL = 4;
    public static final int MOVE_BLOCK_JUMP = 5;
    public static final int MOVE_WATER_SURFACE = 6;

    public static boolean isPlayerInAir(Player player) {
        if (player == null) return false;
        Location playerLoc = player.getLocation();
        double verticalVelocity = player.getVelocity().getY();
        if (verticalVelocity > 0.05) return false;
        if (isBlockSupportive(playerLoc.getBlock().getRelative(0, -1, 0))) return false;
        for (int i = 2; i <= 10; i++) {
            Block blockBelow = playerLoc.getBlock().getRelative(0, -i, 0);
            if (isBlockSupportive(blockBelow)) {
                if (verticalVelocity >= -5.5) return false;
            }
        }
        return true;
    }
    private static boolean isBlockSupportive(Block block) {
        return block.getType().isSolid() ||
               block.getType().name().contains("WATER") ||
               isLadder(block) ||
               isScaffolding(block) ||
               isDoorPassable(block);
    }

    private static boolean shouldBreakBlock(Block block, Player player) {
        if (block == null)
            return false;
        Material type = block.getType();

        if (type.isAir()) {
            return false;
        }

        if (block.isPassable()) {
            return false;
        }

        if (!type.isSolid()) {
            return false;
        }

        if (isDoor(block) || isBanner(block) || isTrapdoor(block) || isScaffolding(block) || isFenceGate(block)) {
            return false;
        }

        if (isLowBlockButNotStair(block)) {
            return false;
        }

        return isBreakable(block.getLocation(), player);
    }

    private static List<Location> addBlockToBreak(List<Location> blocksToBreak, Block block) {
        Location blockLocation = block.getLocation();
        if (blocksToBreak == null) {
            blocksToBreak = new ArrayList<>(3);
        } else if (blocksToBreak.contains(blockLocation)) {
            return blocksToBreak;
        }
        blocksToBreak.add(blockLocation);
        return blocksToBreak;
    }

    /**
     * Checks the complete player volume while entering a lower block column.
     * The departure cell only needs the player's normal two-block clearance,
     * but the landing column must also be clear one block above the player's
     * head so the player can finish the descent.
     */
    private static DescentClearance collectDescentClearance(
            Location currentLocation, Location nextLocation, Player player, List<Location> blocksToBreak) {
        int lowestRequiredY = nextLocation.getBlockY();
        int highestRequiredY = Math.max(
                currentLocation.getBlockY() + 1,
                nextLocation.getBlockY() + 2
        );

        for (int blockY = lowestRequiredY; blockY <= highestRequiredY; blockY++) {
            Block block = nextLocation.getWorld().getBlockAt(
                    nextLocation.getBlockX(), blockY, nextLocation.getBlockZ());
            if (isPassableForDescent(block)) {
                continue;
            }
            if (!shouldBreakBlock(block, player)) {
                return DescentClearance.blocked();
            }
            blocksToBreak = addBlockToBreak(blocksToBreak, block);
        }

        return DescentClearance.clear(blocksToBreak);
    }

    private static boolean isPassableForDescent(Block block) {
        Material type = block.getType();
        String typeName = type.name();

        if (OBSTACLES.contains(type) || containsWithCache(typeName, "LAVA") || containsWithCache(typeName, "FIRE")) {
            return false;
        }

        return block.isPassable()
                || isDoorPassable(block)
                || isBannerPassable(block)
                || isTrapdoorPassable(block)
                || isLadder(block)
                || isScaffolding(block)
                || isFenceGate(block)
                || containsWithCache(typeName, "WATER")
                || isLowBlockButNotStair(block);
    }

    private static final class DescentClearance {
        private final boolean clear;
        private final List<Location> blocksToBreak;

        private DescentClearance(boolean clear, List<Location> blocksToBreak) {
            this.clear = clear;
            this.blocksToBreak = blocksToBreak;
        }

        private static DescentClearance clear(List<Location> blocksToBreak) {
            return new DescentClearance(true, blocksToBreak);
        }

        private static DescentClearance blocked() {
            return new DescentClearance(false, null);
        }
    }

    public static List<Node> findPath(Location start, Location end, Player player) {
        SearchContext context = new SearchContext();
        Location endLoc = end.getBlock().getLocation();
        IndexedOpenSet openSet = new IndexedOpenSet(1024);

        Location startLoc = start.getBlock().getLocation();

        double startHeuristic = heuristic(startLoc, endLoc, context);

        Node startNode = new Node(startLoc, null, 0, startHeuristic, false);
        openSet.add(startNode);
        context.addNode(coordinateKey(startLoc), startNode);

        int iterations = 0;
        double maxSearchRadiusSq = (double) PathfinderConfig.MAX_SEARCH_RADIUS
                * PathfinderConfig.MAX_SEARCH_RADIUS;

        while (!openSet.isEmpty() && iterations < PathfinderConfig.MAX_ITERATIONS) {
            iterations++;
            Node current = openSet.poll();
            current.closed = true;

            double distanceToEnd = current.location.distanceSquared(endLoc);
            if (distanceToEnd < 1.0) {
                return reconstructPath(current);
            }

            if (current.location.distanceSquared(start) > maxSearchRadiusSq) {
                continue;
            }

            for (int i = 0; i < MOVE_DX.length; i++) {
                if (MOVE_DX[i] != 0 && MOVE_DZ[i] != 0) {
                    Location side1 = current.location.clone().add(MOVE_DX[i], 0, 0);
                    Location side2 = current.location.clone().add(0, 0, MOVE_DZ[i]);
                    Location diagonal = current.location.clone().add(MOVE_DX[i], 0, MOVE_DZ[i]);
                    Location head1 = side1.clone().add(0, 1, 0);
                    Location head2 = side2.clone().add(0, 1, 0);
                    Location diagHead = diagonal.clone().add(0, 1, 0);

                    if (!isSafe(side1, context) || !isSafe(side2, context) || !isSafe(diagonal, context) ||
                            (!head1.getBlock().isPassable() && !isDoor(head1.getBlock()) && !isBanner(head1.getBlock()))
                            ||
                            (!head2.getBlock().isPassable() && !isDoor(head2.getBlock()) && !isBanner(head2.getBlock()))
                            ||
                            (!diagHead.getBlock().isPassable() && !isDoor(diagHead.getBlock())
                                    && !isBanner(diagHead.getBlock()))) {
                        continue;
                    }
                }
                int minYOffset = -1;
                if (MOVE_DX[i] == 0 && MOVE_DZ[i] == 0) {
                    minYOffset = -PathfinderConfig.MAX_SAFE_FALL_HEIGHT;
                } else if (Math.abs(MOVE_DX[i]) <= 1 && Math.abs(MOVE_DZ[i]) <= 1) {
                    minYOffset = -2;
                }

                if (Math.abs(current.location.getY() - endLoc.getY()) > 10) {
                    minYOffset = Math.max(minYOffset, -5);
                }

                Location tempLoc = current.location.clone();

                for (int yOffset = minYOffset; yOffset <= 1; yOffset++) {
                    tempLoc.setX(current.location.getX() + MOVE_DX[i]);
                    tempLoc.setY(current.location.getY() + yOffset);
                    tempLoc.setZ(current.location.getZ() + MOVE_DZ[i]);
                    Location nextLocation = tempLoc.clone();
                    long nextKey = coordinateKey(nextLocation);
                    Node existingNode = context.nodes.get(nextKey);

                    if (existingNode != null && existingNode.closed) {
                        continue;
                    }

                    boolean toBreak = false;
                    List<Location> blocksToBreak = null;
                    int moveType;

                    if (yOffset > 0) {
                        if (MOVE_DX[i] != 0 || MOVE_DZ[i] != 0) {
                            moveType = MOVE_JUMP;
                        } else {
                            moveType = MOVE_UP;
                        }
                    } else if (yOffset < 0) {
                        moveType = MOVE_DOWN;
                    } else {
                        moveType = MOVE_HORIZONTAL;
                    }

                    // Height space checks based on move type
                    Block nextHead = nextLocation.getBlock().getRelative(0, 1, 0);
                    Block nextCeiling = nextLocation.getBlock().getRelative(0, 2, 0);

                    int currentHeight = current.location.getBlockY();
                    int nextHeight = nextLocation.getBlockY();

                    if (moveType == MOVE_HORIZONTAL && currentHeight == nextHeight) {
                        Block targetFeet = nextLocation.getBlock();
                        boolean canPassThroughLowBlock = isLowBlockButNotStair(targetFeet) && (nextHead.isPassable()
                                || isDoor(nextHead) || isBanner(nextHead) || isTrapdoorPassable(nextHead))
                                && !OBSTACLES.contains(nextHead.getType());

                        if (canPassThroughLowBlock) {
                        } else {
                            if (!nextHead.isPassable() && !isDoor(nextHead) && !isBanner(nextHead)
                                    && !isTrapdoorPassable(nextHead)) {
                                if (shouldBreakBlock(nextHead, player)) {
                                    toBreak = true;
                                    blocksToBreak = addBlockToBreak(blocksToBreak, nextHead);
                                } else {
                                    continue;
                                }
                            }
                        }
                    }

                    if (moveType == MOVE_DOWN) {
                        int fallHeight = current.location.getBlockY() - nextLocation.getBlockY();

                        DescentClearance descentClearance = collectDescentClearance(
                                current.location, nextLocation, player, blocksToBreak);
                        if (!descentClearance.clear) {
                            continue;
                        }
                        blocksToBreak = descentClearance.blocksToBreak;
                        toBreak = blocksToBreak != null && !blocksToBreak.isEmpty();

                        boolean targetInWater = isInWater(nextLocation);
                        if (fallHeight > PathfinderConfig.MAX_SAFE_FALL_HEIGHT && targetInWater) {
                            continue;
                        }

                        if (fallHeight > PathfinderConfig.MAX_SAFE_FALL_HEIGHT) {
                            if (Math.abs(MOVE_DX[i]) <= 1 && Math.abs(MOVE_DZ[i]) <= 1) {
                                int breakDepth = fallHeight - PathfinderConfig.MAX_SAFE_FALL_HEIGHT;
                                boolean canBreak = true;
                                for (int j = 1; j <= breakDepth; j++) {
                                    Block blockBelow = current.location.getBlock().getRelative(0, -j, 0);
                                    if (!isBreakable(blockBelow.getLocation(), player)) {
                                        canBreak = false;
                                        break;
                                    }
                                    blocksToBreak = addBlockToBreak(blocksToBreak, blockBelow);
                                }
                                if (canBreak) {
                                    toBreak = true;
                                    moveType = MOVE_FALL;
                                } else {
                                    continue;
                                }
                            } else {
                                continue;
                            }
                        } else if (fallHeight > 0) {
                            moveType = MOVE_FALL;
                        }
                    }

                    if (moveType == MOVE_UP) {
                        boolean currentIsLadder = isLadder(current.location.getBlock());
                        boolean nextIsLadder = isLadder(nextLocation.getBlock());
                        boolean currentIsScaffolding = isScaffolding(current.location.getBlock());
                        boolean nextIsScaffolding = isScaffolding(nextLocation.getBlock());
                        boolean currentInWater = containsWithCache(current.location.getBlock().getType().name(), "WATER");
                        boolean nextInWater = containsWithCache(nextLocation.getBlock().getType().name(), "WATER");
                        if (!( (currentIsLadder && nextIsLadder) || (currentIsScaffolding && nextIsScaffolding) || (currentInWater && nextInWater) )) {
                            continue;
                        }

                        if (!(currentIsScaffolding && nextIsScaffolding)) {
                            if (!nextHead.isPassable() && nextHead.getType().isSolid() &&
                                    !isDoor(nextHead) && !isBanner(nextHead) && !isIronTrapdoor(nextHead)
                                    && !isScaffolding(nextHead)) {
                                if (shouldBreakBlock(nextHead, player)) {
                                    toBreak = true;
                                    blocksToBreak = addBlockToBreak(blocksToBreak, nextHead);
                                } else {
                                    continue;
                                }
                            }
                            if (!nextCeiling.isPassable() && nextCeiling.getType().isSolid() &&
                                    !isDoor(nextCeiling) && !isBanner(nextCeiling) && !isIronTrapdoor(nextCeiling)
                                    && !isScaffolding(nextCeiling)) {
                                if (shouldBreakBlock(nextCeiling, player)) {
                                    toBreak = true;
                                    blocksToBreak = addBlockToBreak(blocksToBreak, nextCeiling);
                                } else {
                                    continue;
                                }
                            }
                        }

                        Block currentFeet = current.location.getBlock();
                        if (!currentFeet.isPassable() && currentFeet.getType().isSolid() &&
                                !isDoor(currentFeet) && !isBanner(currentFeet) && !isIronTrapdoor(currentFeet)) {
                            if (shouldBreakBlock(currentFeet, player)) {
                                toBreak = true;
                                blocksToBreak = addBlockToBreak(blocksToBreak, currentFeet);
                            } else {
                                continue;
                            }
                        }
                    }

                    if (moveType == MOVE_JUMP) {
                        Block targetBlock = nextLocation.getBlock();

                        Block targetHead = targetBlock.getRelative(0, 1, 0);
                        Block targetBelow = targetBlock.getRelative(0, -1, 0);
                        if (isBanner(targetBlock) || isBanner(targetHead) || isBanner(targetBelow)) {
                            continue;
                        }

                        Block currentFeetBlock = current.location.getBlock();
                        Block currentGroundBlock = current.location.clone().add(0, -1, 0).getBlock();

                        boolean onScaffolding = isScaffolding(currentGroundBlock) || isScaffolding(currentFeetBlock);
                        boolean standingOnLowBlock = (isLowBlockButNotStair(currentFeetBlock)
                                || isLowBlockButNotStair(currentGroundBlock)) && !onScaffolding;

                        if (standingOnLowBlock) {
                            int heightDiff = nextLocation.getBlockY() - current.location.getBlockY();
                            if (heightDiff > 0) {
                                continue;
                            }

                            Block targetAbove = nextLocation.clone().add(0, 1, 0).getBlock();
                            if (!targetAbove.isPassable() && targetAbove.getType().isSolid() && !isDoor(targetAbove)
                                    && !isBanner(targetAbove)) {
                                continue;
                            }
                        }

                        if (isFenceGate(targetBlock)) {
                            continue;
                        }

                        if (isBanner(targetBlock)) {
                            continue;
                        }

                        Block belowTarget = nextLocation.clone().add(0, -1, 0).getBlock();
                        if (isFenceGate(belowTarget)) {
                            continue;
                        }

                        if (isBanner(belowTarget)) {
                            continue;
                        }

                        if (isFence(targetBlock)) {
                            Block aboveFence = targetBlock.getRelative(0, 1, 0);
                            if (!isCarpet(aboveFence)) {
                                continue;
                            }
                        }

                        if (isFence(belowTarget)) {
                            Block aboveFence = belowTarget.getRelative(0, 1, 0);
                            if (!isCarpet(aboveFence)) {
                                continue;
                            }
                        }

                        if (!nextHead.isPassable() && nextHead.getType().isSolid() && !isDoor(nextHead)
                                && !isBanner(nextHead) && !isIronTrapdoor(nextHead) && !isScaffolding(nextHead)
                                && !isFenceGate(nextHead) && !isLowBlock(nextHead)) {
                            if (shouldBreakBlock(nextHead, player)) {
                                toBreak = true;
                                blocksToBreak = addBlockToBreak(blocksToBreak, nextHead);
                            } else {
                                continue;
                            }
                        }

                        Block currentHead = current.location.getBlock().getRelative(0, 1, 0);
                        Block currentCeiling = current.location.getBlock().getRelative(0, 2, 0);
                        if (!currentHead.isPassable() && currentHead.getType().isSolid() &&
                                !isDoor(currentHead) && !isBanner(currentHead) && !isIronTrapdoor(currentHead)) {
                            if (shouldBreakBlock(currentHead, player)) {
                                toBreak = true;
                                blocksToBreak = addBlockToBreak(blocksToBreak, currentHead);
                            } else {
                                continue;
                            }
                        }

                        if (!currentCeiling.isPassable() && currentCeiling.getType().isSolid() &&
                                !isDoor(currentCeiling) && !isBanner(currentCeiling)
                                && !isIronTrapdoor(currentCeiling)) {
                            if (shouldBreakBlock(currentCeiling, player)) {
                                toBreak = true;
                                blocksToBreak = addBlockToBreak(blocksToBreak, currentCeiling);
                            } else {
                                continue;
                            }
                        }

                    }

                    if (moveType == MOVE_FALL) {
                        Block targetBlock = nextLocation.getBlock();
                        if (!isLowBlockButNotStair(targetBlock) && shouldBreakBlock(targetBlock, player)) {
                            toBreak = true;
                            blocksToBreak = addBlockToBreak(blocksToBreak, targetBlock);
                        }
                    }

                    boolean diagonal = (MOVE_DX[i] != 0 && MOVE_DZ[i] != 0);
                    double moveCost = diagonal ? PathfinderConfig.DIAGONAL_COST : PathfinderConfig.STRAIGHT_COST;

                    if (current.parent != null) {
                        int currentDirX = MOVE_DX[i];
                        int currentDirZ = MOVE_DZ[i];

                        int prevDirX = current.dirX;
                        int prevDirZ = current.dirZ;

                        if (prevDirX != 0 || prevDirZ != 0) {
                            if (currentDirX != prevDirX || currentDirZ != prevDirZ) {
                                int dotProduct = currentDirX * prevDirX + currentDirZ * prevDirZ;
                                if (dotProduct == 0) {
                                    moveCost += PathfinderConfig.RIGHT_ANGLE_TURN_COST;
                                } else if ((currentDirX != 0 && currentDirZ != 0)
                                        != (prevDirX != 0 && prevDirZ != 0)) {
                                    moveCost += PathfinderConfig.DIAGONAL_TURN_COST;
                                }
                            }
                        }
                    }

                    if (moveType == MOVE_HORIZONTAL) {
                        Location headPath = current.location.clone().add(MOVE_DX[i], 1, MOVE_DZ[i]);
                        Block headPathBlock = headPath.getBlock();
                        Block belowHeadPath = headPath.clone().add(0, -1, 0).getBlock();

                        boolean isKelpOnWater = containsWithCache(headPathBlock.getType().name(), "KELP") &&
                                containsWithCache(belowHeadPath.getType().name(), "WATER");

                        if (isKelpOnWater) {
                        } else if (!headPathBlock.isPassable() && headPathBlock.getType().isSolid()
                                && !isDoor(headPathBlock) && !isBanner(headPathBlock)) {
                            if (shouldBreakBlock(headPathBlock, player)) {
                                toBreak = true;
                                blocksToBreak = addBlockToBreak(blocksToBreak, headPathBlock);
                            } else {
                                continue;
                            }
                        }
                    }

                    if (!isSafe(nextLocation, context)) {
                        Block nextBlock = nextLocation.getBlock();
                        Block groundBlock = nextLocation.clone().add(0, -1, 0).getBlock();

                        boolean isKelpOnWater = containsWithCache(nextBlock.getType().name(), "KELP") &&
                                containsWithCache(groundBlock.getType().name(), "WATER");

                        if (isKelpOnWater) {
                        } else if (!isDoor(nextBlock) && !isBanner(nextBlock) && !isLowBlockButNotStair(nextBlock)
                                && shouldBreakBlock(nextBlock, player)) {
                            toBreak = true;
                            blocksToBreak = addBlockToBreak(blocksToBreak, nextBlock);
                        } else {
                            continue;
                        }
                    }

                    if (toBreak) {
                        // Charge once for every distinct block this transition requires breaking.
                        // Keep a one-cost fallback for legacy paths that only carry the boolean flag.
                        int breakCount = blocksToBreak == null ? 0 : blocksToBreak.size();
                        moveCost += PathfinderConfig.BREAK_BLOCK_COST * Math.max(1, breakCount);
                    }

                    boolean nextInsideWater = isInsideWater(nextLocation, context);
                    boolean nextOnWaterSurface = isOnWaterSurface(nextLocation, context);
                    
                    if (nextInsideWater) {
                        moveCost += PathfinderConfig.WATER_COST * 5.0;
                    }

                    if (nextOnWaterSurface) {
                        moveCost += PathfinderConfig.WATER_COST;
                    }

                    if (isDoorPassable(nextLocation.getBlock()) || isBannerPassable(nextLocation.getBlock())
                            || isFenceGate(nextLocation.getBlock())) {
                        moveCost += PathfinderConfig.DOOR_COST;
                    }

                    Block currentFeet = nextLocation.getBlock();
                    Block belowFeet = nextLocation.clone().add(0, -1, 0).getBlock();

                    if (isFence(currentFeet) || isFence(belowFeet)) {
                        Block fenceBlock = isFence(currentFeet) ? currentFeet : belowFeet;
                        Block carpetBlock = fenceBlock.getRelative(0, 1, 0);
                        Block aboveCarpet = carpetBlock.getRelative(0, 1, 0);

                        if (!isCarpet(carpetBlock)) {
                            continue;
                        }

                        if (!aboveCarpet.isPassable()) {
                            continue;
                        }
                    }

                    if (isTrapdoor(nextLocation.getBlock())) {
                        moveCost += PathfinderConfig.TRAPDOOR_COST;
                    }
                    if (isTrapdoor(nextLocation.getBlock().getRelative(0, 1, 0))) {
                        moveCost += PathfinderConfig.TRAPDOOR_COST;
                    }

                    if (isScaffolding(nextLocation.getBlock())) {
                        moveCost += PathfinderConfig.SCAFFOLDING_COST;
                    }

                    if (moveType == MOVE_JUMP) {
                        moveCost += PathfinderConfig.JUMP_COST;
                    }

                    if (moveType == MOVE_BLOCK_JUMP) {
                        moveCost += PathfinderConfig.BLOCK_JUMP_COST;
                    }

                    if (moveType == MOVE_UP) {
                        moveCost += PathfinderConfig.VERTICAL_COST;
                    } else if (moveType == MOVE_DOWN || moveType == MOVE_FALL) {
                        moveCost += PathfinderConfig.FALL_COST;

                        int fallHeight = current.location.getBlockY() - nextLocation.getBlockY();
                        if (fallHeight > 1) {
                            moveCost += (fallHeight - 1) * 0.1;
                        }
                    }

                    double nextG = current.g + moveCost;

                    if (existingNode != null) {
                        if (nextG < existingNode.g) {
                            existingNode.parent = current;
                            existingNode.g = nextG;
                            existingNode.f = nextG + existingNode.h;
                            existingNode.setBlocksToBreak(blocksToBreak);
                            existingNode.moveType = moveType;
                            existingNode.dirX = nextLocation.getBlockX() - current.location.getBlockX();
                            existingNode.dirZ = nextLocation.getBlockZ() - current.location.getBlockZ();
                            openSet.decreaseKey(existingNode);
                        }
                    } else {
                        double nextH = heuristic(nextLocation, endLoc, context);
                        Node neighbor = new Node(nextLocation, current, nextG, nextH, toBreak, moveType);
                        neighbor.setBlocksToBreak(blocksToBreak);
                        openSet.add(neighbor);
                        context.addNode(nextKey, neighbor);
                    }
                }
            }

            Block blockJumpFeet = current.location.getBlock();
            Block blockJumpGround = blockJumpFeet.getRelative(0, -1, 0);
            boolean onScaffoldingForBlockJump = isScaffolding(blockJumpGround) || isScaffolding(blockJumpFeet);
            boolean canStartBlockJump = !isTraversableWaterSurface(current.location)
                    && !((isLowBlockButNotStair(blockJumpFeet)
                    || isLowBlockButNotStair(blockJumpGround)) && !onScaffoldingForBlockJump);
            canStartBlockJump = canStartBlockJump && hasJumpTrajectoryClearance(blockJumpFeet);

            for (int dir = 0; dir < JUMP_DX.length && canStartBlockJump; dir++) {
                int jumpDirX = JUMP_DX[dir];
                int jumpDirZ = JUMP_DZ[dir];

                for (int distance = 2; distance <= PathfinderConfig.MAX_BLOCK_JUMP_DISTANCE; distance++) {
                    Location jumpTarget = current.location.clone().add(jumpDirX * distance, 0, jumpDirZ * distance);
                    long jumpKey = coordinateKey(jumpTarget);
                    Node existingNode = context.nodes.get(jumpKey);

                    if (existingNode != null && existingNode.closed) {
                        continue;
                    }

                    if (isTraversableWaterSurface(jumpTarget)) {
                        continue;
                    }

                    // Landing only needs room for the player's feet and head.
                    if (!isSafe(jumpTarget, context)) {
                        continue;
                    }

                    boolean canJump = true;

                    boolean hasObstacle = false;
                    for (int step = 1; step < distance; step++) {
                        Location pathPoint = current.location.clone().add(jumpDirX * step, 0, jumpDirZ * step);
                        Block pathBlock = pathPoint.getBlock();
                        Block belowPathBlock = pathPoint.clone().add(0, -1, 0).getBlock();

                        boolean jumpableFence = isJumpableFence(pathBlock);
                        if (isBanner(pathBlock) || isBanner(belowPathBlock)) {
                            hasObstacle = true;
                            canJump = false;
                            break;
                        }
                        if (!pathBlock.isPassable() && pathBlock.getType().isSolid() && !jumpableFence
                                && !isDoor(pathBlock)) {
                            hasObstacle = true;
                            canJump = false;
                            break;
                        }

                        if ((isFence(pathBlock) && !isJumpableFence(pathBlock)) || isFenceGate(pathBlock)) {
                            hasObstacle = true;
                            canJump = false;
                            break;
                        }

                        if (isFenceGate(belowPathBlock)) {
                            hasObstacle = true;
                            canJump = false;
                            break;
                        }

                        String blockName = pathBlock.getType().name();
                        if (containsWithCache(blockName, "WATER")) {
                            hasObstacle = true;
                            canJump = false;
                            break;
                        }

                        if (OBSTACLES.contains(pathBlock.getType()) ||
                                containsWithCache(blockName, "LAVA") ||
                                containsWithCache(blockName, "FIRE")) {
                            hasObstacle = true;
                            canJump = false;
                            break;
                        }

                        for (int y = 1; y <= 2; y++) {
                            Block checkBlock = pathPoint.getBlock().getRelative(0, y, 0);
                            if (isBanner(checkBlock)) {
                                hasObstacle = true;
                                canJump = false;
                                break;
                            }
                            if (!checkBlock.isPassable() && checkBlock.getType().isSolid() && !isDoor(checkBlock)) {
                                hasObstacle = true;
                                canJump = false;
                                break;
                            }

                            String checkBlockName = checkBlock.getType().name();
                            if (OBSTACLES.contains(checkBlock.getType()) ||
                                    containsWithCache(checkBlockName, "LAVA") ||
                                    containsWithCache(checkBlockName, "FIRE")) {
                                hasObstacle = true;
                                canJump = false;
                                break;
                            }
                        }
                        if (!canJump)
                            break;
                    }

                    int heightDiff = jumpTarget.getBlockY() - current.location.getBlockY();

                    boolean canReachByNormalMove = true;
                    if (distance == 2) {
                        Location midPoint = current.location.clone().add(jumpDirX, heightDiff, jumpDirZ);
                        if (!isSafe(midPoint, context) || heightDiff > 1) {
                            canReachByNormalMove = false;
                        }
                    }

                    if (!hasObstacle && distance == 2 && heightDiff <= 0 && canReachByNormalMove) {
                        canJump = false;
                    }

                    if (!canJump)
                        continue;

                    Block jumpTargetBlock = jumpTarget.getBlock();
                    Block belowJumpTarget = jumpTarget.clone().add(0, -1, 0).getBlock();
                    if (isFenceGate(jumpTargetBlock) || isFenceGate(belowJumpTarget)) {
                        continue;
                    }

                    if (isBanner(jumpTargetBlock) || isBanner(belowJumpTarget)) {
                        continue;
                    }

                    double jumpCost = PathfinderConfig.BLOCK_JUMP_COST * distance * distance;
                    double nextG = current.g + jumpCost;

                    if (existingNode != null) {
                        if (nextG < existingNode.g) {
                            existingNode.parent = current;
                            existingNode.g = nextG;
                            existingNode.f = nextG + existingNode.h;
                            existingNode.setBlocksToBreak(null);
                            existingNode.moveType = MOVE_BLOCK_JUMP;
                            existingNode.dirX = jumpDirX * distance;
                            existingNode.dirZ = jumpDirZ * distance;
                            openSet.decreaseKey(existingNode);
                        }
                    } else {
                        double nextH = heuristic(jumpTarget, endLoc, context);
                        Node jumpNode = new Node(jumpTarget, current, nextG, nextH, false, MOVE_BLOCK_JUMP);
                        jumpNode.dirX = jumpDirX * distance;
                        jumpNode.dirZ = jumpDirZ * distance;
                        openSet.add(jumpNode);
                        context.addNode(jumpKey, jumpNode);
                    }
                }
            }
        }

        Node closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Node node : context.allNodes) {
            double distance = node.location.distanceSquared(endLoc);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = node;
            }
        }

        return closest != null ? reconstructPath(closest) : null;
    }

    private static long coordinateKey(Location loc) {
        return coordinateKey(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private static long coordinateKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | (long) y & 0xFFFL;
    }

    private static List<Node> reconstructPath(Node node) {
        int pathLength = 0;
        Node temp = node;
        while (temp != null) {
            pathLength++;
            temp = temp.parent;
        }

        List<Node> path = new ArrayList<>(pathLength * 2);
        while (node != null) {
            path.add(node);
            node = node.parent;
        }
        Collections.reverse(path);
        path = smoothWaterSurfacePath(path);

        List<Node> expandedPath = new ArrayList<>();

        if (!path.isEmpty()) {
            Node firstNode = path.get(0);
            if (path.size() > 1) {
                Node nextNode = path.get(1);
                int dx = nextNode.location.getBlockX() - firstNode.location.getBlockX();
                int dz = nextNode.location.getBlockZ() - firstNode.location.getBlockZ();
                int distance = Math.max(Math.abs(dx), Math.abs(dz));

                if (nextNode.moveType == MOVE_WATER_SURFACE) {
                    firstNode.moveType = MOVE_WATER_SURFACE;
                    firstNode.dirX = Integer.compare(dx, 0);
                    firstNode.dirZ = Integer.compare(dz, 0);
                } else if (nextNode.moveType == MOVE_BLOCK_JUMP || distance > 1) {
                    firstNode.moveType = MOVE_BLOCK_JUMP;
                    firstNode.dirX = Integer.compare(dx, 0);
                    firstNode.dirZ = Integer.compare(dz, 0);
                }
            }
            expandedPath.add(firstNode);
        }

        for (int i = 0; i < path.size() - 1; i++) {
            Node currentNode = path.get(i);
            Node nextNode = path.get(i + 1);

            int dx = nextNode.location.getBlockX() - currentNode.location.getBlockX();
            int dz = nextNode.location.getBlockZ() - currentNode.location.getBlockZ();
            int dy = nextNode.location.getBlockY() - currentNode.location.getBlockY();
            int distance = Math.max(Math.abs(dx), Math.abs(dz));

            if (nextNode.moveType == MOVE_WATER_SURFACE) {
                currentNode.moveType = MOVE_WATER_SURFACE;
                currentNode.dirX = Integer.compare(dx, 0);
                currentNode.dirZ = Integer.compare(dz, 0);
                nextNode.dirX = currentNode.dirX;
                nextNode.dirZ = currentNode.dirZ;
            } else if (distance > 1 || nextNode.moveType == MOVE_BLOCK_JUMP) {
                currentNode.moveType = MOVE_BLOCK_JUMP;
                nextNode.moveType = MOVE_BLOCK_JUMP;

                int stepX = dx == 0 ? 0 : (dx > 0 ? 1 : -1);
                int stepZ = dz == 0 ? 0 : (dz > 0 ? 1 : -1);

                currentNode.dirX = stepX;
                currentNode.dirZ = stepZ;

                nextNode.dirX = stepX;
                nextNode.dirZ = stepZ;

                if (distance > 1) {
                    for (int step = 1; step < distance; step++) {
                        int midX = currentNode.location.getBlockX() + stepX * step;
                        int midY = currentNode.location.getBlockY();
                        int midZ = currentNode.location.getBlockZ() + stepZ * step;

                        Location midLoc = new Location(
                                currentNode.location.getWorld(),
                                midX,
                                midY,
                                midZ);

                        Node midNode = new Node(midLoc, currentNode, 0, 0, false, MOVE_BLOCK_JUMP);
                        midNode.dirX = stepX;
                        midNode.dirZ = stepZ;
                        expandedPath.add(midNode);
                    }
                }
            } else if (nextNode.moveType == MOVE_JUMP || dy > 0) {
                currentNode.moveType = MOVE_JUMP;
                nextNode.moveType = MOVE_JUMP;

                currentNode.dirX = dx == 0 ? 0 : (dx > 0 ? 1 : -1);
                currentNode.dirZ = dz == 0 ? 0 : (dz > 0 ? 1 : -1);
                nextNode.dirX = currentNode.dirX;
                nextNode.dirZ = currentNode.dirZ;
            }

            expandedPath.add(nextNode);
        }

        for (Node pathNode : expandedPath) {
            pathNode.populateDisplayFlags();
        }

        return expandedPath;
    }

    private static List<Node> smoothWaterSurfacePath(List<Node> path) {
        if (path.size() < 3) {
            return path;
        }

        List<Node> smoothed = new ArrayList<>(path.size());
        smoothed.add(path.get(0));
        int currentIndex = 0;

        while (currentIndex < path.size() - 1) {
            Node current = path.get(currentIndex);
            int nextIndex = currentIndex + 1;

            if (isTraversableWaterSurface(current.location)) {
                int farthestVisible = currentIndex;
                int surfaceY = current.location.getBlockY();

                for (int candidate = currentIndex + 1; candidate < path.size(); candidate++) {
                    Node candidateNode = path.get(candidate);
                    if (candidateNode.location.getBlockY() != surfaceY
                            || !isTraversableWaterSurface(candidateNode.location)) {
                        break;
                    }
                    if (hasDirectWaterSurfacePath(current.location, candidateNode.location)) {
                        farthestVisible = candidate;
                    }
                }

                if (farthestVisible > currentIndex + 1) {
                    Node destination = path.get(farthestVisible);
                    current.moveType = MOVE_WATER_SURFACE;
                    destination.moveType = MOVE_WATER_SURFACE;
                    nextIndex = farthestVisible;
                }
            }

            smoothed.add(path.get(nextIndex));
            currentIndex = nextIndex;
        }

        return smoothed;
    }

    private static boolean hasDirectWaterSurfacePath(Location start, Location end) {
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
            Location point = new Location(start.getWorld(),
                    startX + deltaX * ratio,
                    start.getBlockY(),
                    startZ + deltaZ * ratio);
            if (!isTraversableWaterSurface(point)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isTraversableWaterSurface(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block below = feet.getRelative(0, -1, 0);

        boolean feetWater = containsWithCache(feet.getType().name(), "WATER");
        boolean headWater = containsWithCache(head.getType().name(), "WATER");
        boolean belowWater = containsWithCache(below.getType().name(), "WATER");

        boolean feetOpen = (feet.isPassable() && !OBSTACLES.contains(feet.getType()))
                || feetWater
                || (belowWater && containsWithCache(feet.getType().name(), "KELP"));
        boolean headOpen = head.isPassable()
                && !headWater
                && !OBSTACLES.contains(head.getType());

        return feetOpen && headOpen
                && ((!feetWater && belowWater) || (feetWater && !headWater));
    }

    private static boolean hasJumpTrajectoryClearance(Block feet) {
        for (int y = 1; y <= 2; y++) {
            Block block = feet.getRelative(0, y, 0);
            if (!block.isPassable() && block.getType().isSolid()
                    && !isDoor(block) && !isBanner(block)) {
                return false;
            }
        }
        return true;
    }

    private static double heuristic(Location a, Location b, SearchContext context) {
        int dx = Math.abs(a.getBlockX() - b.getBlockX());
        int dy = Math.abs(a.getBlockY() - b.getBlockY());
        int dz = Math.abs(a.getBlockZ() - b.getBlockZ());

        double baseHeuristic = dx + dz + dy * 1.5;

        boolean insideWater = isInsideWater(a, context);
        boolean targetInsideWater = isInsideWater(b, context);
        boolean onWaterSurface = isOnWaterSurface(a, context);

        if (insideWater) {
            baseHeuristic += PathfinderConfig.WATER_COST * 2.0;
        }

        if (targetInsideWater) {
            baseHeuristic += PathfinderConfig.WATER_COST * 2.0;
        }

        if (onWaterSurface) {
            baseHeuristic += PathfinderConfig.WATER_COST;
        }

        double perturbation = (a.getBlockX() * 0.001 + a.getBlockZ() * 0.0001) % 0.01;

        return baseHeuristic + perturbation;
    }

    private static boolean isInWater(Location location) {
        Block block = location.getBlock();
        String blockName = block.getType().name();

        return containsWithCache(blockName, "WATER");
    }

    private static boolean isOnWaterSurface(Location location) {
        Block currentBlock = location.getBlock();
        Block belowBlock = location.clone().add(0, -1, 0).getBlock();
        Block aboveBlock = location.clone().add(0, 1, 0).getBlock();

        String currentName = currentBlock.getType().name();
        String belowName = belowBlock.getType().name();
        String aboveName = aboveBlock.getType().name();

        boolean currentIsPassable = currentBlock.isPassable() || !containsWithCache(currentName, "WATER");
        boolean belowIsWater = containsWithCache(belowName, "WATER");
        boolean onWaterSurface = currentIsPassable && belowIsWater;

        boolean aboveIsWater = containsWithCache(aboveName, "WATER");
        boolean underWaterSurface = currentIsPassable && aboveIsWater && belowIsWater;

        boolean currentIsWater = containsWithCache(currentName, "WATER");
        boolean aboveIsPassable = aboveBlock.isPassable() && !containsWithCache(aboveName, "WATER");
        boolean shallowWaterSurface = currentIsWater && aboveIsPassable;

        return onWaterSurface || underWaterSurface || shallowWaterSurface;
    }

    private static boolean isInsideWater(Location location) {
        if (containsWithCache(location.getBlock().getType().name(), "WATER")) {
            Location aboveLocation = location.clone().add(0, 1, 0);
            if (containsWithCache(aboveLocation.getBlock().getType().name(), "WATER")) {
                return true;
            }

            Location belowLocation = location.clone().add(0, -1, 0);
            if (containsWithCache(belowLocation.getBlock().getType().name(), "WATER")) {
                Location deeperLocation = location.clone().add(0, -2, 0);
                if (containsWithCache(deeperLocation.getBlock().getType().name(), "WATER")) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean isInsideWater(Location location, SearchContext context) {
        return (waterState(location, context) & SearchContext.WATER_INSIDE) != 0;
    }

    private static boolean isOnWaterSurface(Location location, SearchContext context) {
        return (waterState(location, context) & SearchContext.WATER_SURFACE) != 0;
    }

    private static byte waterState(Location location, SearchContext context) {
        long key = coordinateKey(location);
        byte cached = context.waterStates.get(key);
        if (cached != 0) {
            return cached;
        }

        byte state = SearchContext.WATER_KNOWN;
        if (isInsideWater(location)) {
            state |= SearchContext.WATER_INSIDE;
        }
        if (isOnWaterSurface(location)) {
            state |= SearchContext.WATER_SURFACE;
        }
        context.waterStates.put(key, state);
        return state;
    }

    private static final Set<String> UNBREAKABLE_BLOCKS = new HashSet<>(Arrays.asList(
            "BEDROCK", "PORTAL", "SPAWNER", "BARRIER", "END_PORTAL", "END_GATEWAY"));

    private static boolean isBreakable(Location loc, Player player) {
        Block block = loc.getBlock();
        Material type = block.getType();

        if (type.isAir() || block.isPassable()) {
            return false;
        }

        if (!type.isSolid() || type.getHardness() >= 50) {
            return false;
        }

        if (isDoor(block) || isBanner(block) || isTrapdoor(block)) {
            return false;
        }

        if (isLowBlockButNotStair(block)) {
            Block above = block.getRelative(0, 1, 0);
            if (above.isPassable() && !OBSTACLES.contains(above.getType())) {
                return false;
            }
        }

        String typeName = type.name();
        if (containsWithCache(typeName, "KELP")) {
            Block below = block.getRelative(0, -1, 0);
            if (containsWithCache(below.getType().name(), "WATER")) {
                return false;
            }
        }

        if (isCarpet(block)) {
            Block below = block.getRelative(0, -1, 0);
            Block above = block.getRelative(0, 1, 0);

            if (isFence(below) && above.isPassable()) {
                return false;
            }
        }

        if (isFence(block)) {
            Block above = block.getRelative(0, 1, 0);
            Block aboveAbove = block.getRelative(0, 2, 0);

            if (isCarpet(above) && aboveAbove.isPassable()) {
                return false;
            }
        }

        String blockType = type.name();
        for (String unbreakable : UNBREAKABLE_BLOCKS) {
            if (blockType.contains(unbreakable)) {
                return false;
            }
        }

        boolean nearLava = false;
        for (int[] dir : HAZARD_DIRECTIONS) {
            Block neighbor = block.getRelative(dir[0], dir[1], dir[2]);
            String neighborType = neighbor.getType().name();

            if (neighborType.contains("LAVA")) {
                nearLava = true;
                break;
            }
        }

        if (nearLava) {
            return false;
        }

        return true;
    }

    public static boolean isDoor(Block block) {
        return DOOR_TYPES[block.getType().ordinal()];
    }

    public static boolean isDoorPassable(Block block) {
        if (!isDoor(block)) {
            return false;
        }

        return true;
    }

    public static boolean isBanner(Block block) {
        return BANNER_TYPES[block.getType().ordinal()];
    }

    public static boolean isBannerPassable(Block block) {
        if (!isBanner(block)) {
            return false;
        }

        return true;
    }

    public static boolean isTrapdoor(Block block) {
        return TRAPDOOR_TYPES[block.getType().ordinal()];
    }

    public static boolean isIronTrapdoor(Block block) {
        return IRON_TRAPDOOR_TYPES[block.getType().ordinal()];
    }

    public static boolean isTrapdoorPassable(Block block) {
        return isTrapdoor(block) && !isIronTrapdoor(block);
    }

    public static boolean isLadder(Block block) {
        return LADDER_TYPES[block.getType().ordinal()];
    }
    
    private static boolean isJumpableFence(Block fenceBlock) {
        if (!isFence(fenceBlock)) {
            return false;
        }
        Block carpetBlock = fenceBlock.getRelative(0, 1, 0);
        if (!isCarpet(carpetBlock)) {
            return false;
        }
        Block aboveCarpet = carpetBlock.getRelative(0, 1, 0);
        return aboveCarpet.isPassable();
    }

    public static boolean isScaffolding(Block block) {
        return SCAFFOLDING_TYPES[block.getType().ordinal()];
    }

    public static boolean isNearLava(Block block) {
        if (block.hasMetadata("nearLava")) {
            for (MetadataValue value : block.getMetadata("nearLava")) {
                if (value.asBoolean()) {
                    return true;
                }
            }
        }

        for (int[] dir : HAZARD_DIRECTIONS) {
            Block neighbor = block.getRelative(dir[0], dir[1], dir[2]);
            if (neighbor.getType().name().contains("LAVA") || neighbor.getType().name().contains("FIRE")) {
                block.setMetadata("nearLava", new FixedMetadataValue(PathFinderPlugin.getInstance(), true));
                return true;
            }
        }

        return false;
    }

    private static boolean containsWithCache(String str, String substr) {
        return str.contains(substr);
    }

    private static boolean isSafe(Location loc, SearchContext context) {
        long key = coordinateKey(loc);
        byte cachedResult = context.safeLocations.get(key);
        if (cachedResult != 0) {
            return cachedResult == LongByteMap.TRUE;
        }

        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        Material feetType = feet.getType();
        Material headType = head.getType();
        Material groundType = ground.getType();

        String feetName = feetType.name();
        String headName = headType.name();
        String groundName = groundType.name();

        if (OBSTACLES.contains(feetType) || OBSTACLES.contains(headType) ||
                containsWithCache(feetName, "LAVA") || containsWithCache(feetName, "FIRE") ||
                containsWithCache(headName, "LAVA") || containsWithCache(headName, "FIRE") ||
                containsWithCache(groundName, "LAVA") || containsWithCache(groundName, "FIRE")) {
            context.safeLocations.put(key, LongByteMap.FALSE);
            return false;
        }

        if (containsWithCache(groundName, "WATER") && containsWithCache(feetName, "KELP")) {
            feetType = Material.AIR;
            feetName = "AIR";
        }

        if (isInsideWater(loc, context)) {
            if (!containsWithCache(headName, "WATER") && !head.isPassable()) {
                context.safeLocations.put(key, LongByteMap.FALSE);
                return false;
            }
        }

        boolean feetPassable = (feet.isPassable() && !OBSTACLES.contains(feetType)) || isDoorPassable(feet)
                || isBannerPassable(feet) || (isTrapdoor(feet) && !isIronTrapdoor(feet)) || isLadder(feet)
                || isScaffolding(feet) || isFenceGate(feet) ||
                containsWithCache(feetName, "WATER") ||
                (containsWithCache(groundName, "WATER") && containsWithCache(feetName, "KELP")) ||
                (isLowBlockButNotStair(feet) && head.isPassable() && !OBSTACLES.contains(headType));
        boolean headPassable = (head.isPassable() && !OBSTACLES.contains(headType)) || isDoorPassable(head)
                || isBannerPassable(head) || (isTrapdoor(head) && !isIronTrapdoor(head)) || isLadder(head)
                || isScaffolding(head) || isFenceGate(head);

        boolean groundSolid = groundType.isSolid() || containsWithCache(groundName, "WATER") ||
                isLadder(ground) || isScaffolding(ground);

        if (!headPassable && (isDoorPassable(head) || isBannerPassable(head)
                || (isTrapdoor(head) && !isIronTrapdoor(head)) || isFenceGate(head))) {
            headPassable = true;
        }

        boolean nearLava = false;
        for (int[] dir : HAZARD_DIRECTIONS) {
            Block neighbor = feet.getRelative(dir[0], dir[1], dir[2]);
            String neighborType = neighbor.getType().name();

            if (containsWithCache(neighborType, "LAVA") || containsWithCache(neighborType, "FIRE")) {
                nearLava = true;
                break;
            }
        }

        if (nearLava) {
            context.safeLocations.put(key, LongByteMap.FALSE);
            return false;
        }

        boolean result = feetPassable && headPassable && groundSolid;
        context.safeLocations.put(key, result ? LongByteMap.TRUE : LongByteMap.FALSE);
        return result;
    }

    private static int compareNodes(Node n1, Node n2) {
        int fCompare = Double.compare(n1.f, n2.f);
        if (fCompare != 0) {
            return fCompare;
        }

        int gCompare = Double.compare(n2.g, n1.g);
        if (gCompare != 0) {
            return gCompare;
        }

        int xCompare = Integer.compare(n1.location.getBlockX(), n2.location.getBlockX());
        if (xCompare != 0) {
            return xCompare;
        }

        int zCompare = Integer.compare(n1.location.getBlockZ(), n2.location.getBlockZ());
        if (zCompare != 0) {
            return zCompare;
        }

        return Integer.compare(n1.location.getBlockY(), n2.location.getBlockY());
    }

    private static final class SearchContext {
        private static final byte WATER_KNOWN = 1;
        private static final byte WATER_INSIDE = 2;
        private static final byte WATER_SURFACE = 4;

        private final LongNodeMap nodes = new LongNodeMap(2048);
        private final LongByteMap safeLocations = new LongByteMap(2048);
        private final LongByteMap waterStates = new LongByteMap(2048);
        private final List<Node> allNodes = new ArrayList<>(1024);

        private void addNode(long key, Node node) {
            nodes.put(key, node);
            allNodes.add(node);
        }
    }

    private static final class IndexedOpenSet {
        private Node[] heap;
        private int size;

        private IndexedOpenSet(int initialCapacity) {
            heap = new Node[Math.max(2, initialCapacity + 1)];
        }

        private boolean isEmpty() {
            return size == 0;
        }

        private void add(Node node) {
            ensureCapacity(size + 1);
            heap[++size] = node;
            node.heapIndex = size;
            siftUp(size);
        }

        private Node poll() {
            Node result = heap[1];
            Node tail = heap[size];
            heap[size--] = null;
            result.heapIndex = -1;
            if (size > 0) {
                heap[1] = tail;
                tail.heapIndex = 1;
                siftDown(1);
            }
            return result;
        }

        private void decreaseKey(Node node) {
            if (node.heapIndex <= 0) {
                throw new IllegalStateException("Cannot update a node outside the open set");
            }
            siftUp(node.heapIndex);
        }

        private void siftUp(int index) {
            Node value = heap[index];
            while (index > 1) {
                int parentIndex = index >>> 1;
                Node parent = heap[parentIndex];
                if (compareNodes(value, parent) >= 0) {
                    break;
                }
                heap[index] = parent;
                parent.heapIndex = index;
                index = parentIndex;
            }
            heap[index] = value;
            value.heapIndex = index;
        }

        private void siftDown(int index) {
            Node value = heap[index];
            int half = size >>> 1;
            while (index <= half) {
                int child = index << 1;
                int right = child + 1;
                if (right <= size && compareNodes(heap[right], heap[child]) < 0) {
                    child = right;
                }
                Node childValue = heap[child];
                if (compareNodes(value, childValue) <= 0) {
                    break;
                }
                heap[index] = childValue;
                childValue.heapIndex = index;
                index = child;
            }
            heap[index] = value;
            value.heapIndex = index;
        }

        private void ensureCapacity(int requestedSize) {
            if (requestedSize < heap.length) {
                return;
            }
            heap = Arrays.copyOf(heap, heap.length << 1);
        }
    }

    private static final class LongNodeMap {
        private static final float LOAD_FACTOR = 0.65f;

        private long[] keys;
        private Node[] values;
        private int mask;
        private int resizeAt;
        private int size;

        private LongNodeMap(int expectedSize) {
            int capacity = tableSize(expectedSize, LOAD_FACTOR);
            keys = new long[capacity];
            values = new Node[capacity];
            mask = capacity - 1;
            resizeAt = (int) (capacity * LOAD_FACTOR);
        }

        private Node get(long key) {
            int index = mix(key) & mask;
            while (true) {
                Node value = values[index];
                if (value == null) {
                    return null;
                }
                if (keys[index] == key) {
                    return value;
                }
                index = (index + 1) & mask;
            }
        }

        private void put(long key, Node value) {
            if (size >= resizeAt) {
                resize();
            }
            putWithoutResize(key, value);
        }

        private void putWithoutResize(long key, Node value) {
            int index = mix(key) & mask;
            while (values[index] != null) {
                if (keys[index] == key) {
                    values[index] = value;
                    return;
                }
                index = (index + 1) & mask;
            }
            keys[index] = key;
            values[index] = value;
            size++;
        }

        private void resize() {
            long[] oldKeys = keys;
            Node[] oldValues = values;
            int newCapacity = oldValues.length << 1;
            keys = new long[newCapacity];
            values = new Node[newCapacity];
            mask = newCapacity - 1;
            resizeAt = (int) (newCapacity * LOAD_FACTOR);
            size = 0;
            for (int i = 0; i < oldValues.length; i++) {
                if (oldValues[i] != null) {
                    putWithoutResize(oldKeys[i], oldValues[i]);
                }
            }
        }
    }

    private static final class LongByteMap {
        private static final byte FALSE = 1;
        private static final byte TRUE = 2;
        private static final float LOAD_FACTOR = 0.65f;

        private long[] keys;
        private byte[] values;
        private int mask;
        private int resizeAt;
        private int size;

        private LongByteMap(int expectedSize) {
            int capacity = tableSize(expectedSize, LOAD_FACTOR);
            keys = new long[capacity];
            values = new byte[capacity];
            mask = capacity - 1;
            resizeAt = (int) (capacity * LOAD_FACTOR);
        }

        private byte get(long key) {
            int index = mix(key) & mask;
            while (values[index] != 0) {
                if (keys[index] == key) {
                    return values[index];
                }
                index = (index + 1) & mask;
            }
            return 0;
        }

        private void put(long key, byte value) {
            if (size >= resizeAt) {
                resize();
            }
            putWithoutResize(key, value);
        }

        private void putWithoutResize(long key, byte value) {
            int index = mix(key) & mask;
            while (values[index] != 0) {
                if (keys[index] == key) {
                    values[index] = value;
                    return;
                }
                index = (index + 1) & mask;
            }
            keys[index] = key;
            values[index] = value;
            size++;
        }

        private void resize() {
            long[] oldKeys = keys;
            byte[] oldValues = values;
            int newCapacity = oldValues.length << 1;
            keys = new long[newCapacity];
            values = new byte[newCapacity];
            mask = newCapacity - 1;
            resizeAt = (int) (newCapacity * LOAD_FACTOR);
            size = 0;
            for (int i = 0; i < oldValues.length; i++) {
                if (oldValues[i] != 0) {
                    putWithoutResize(oldKeys[i], oldValues[i]);
                }
            }
        }
    }

    private static int tableSize(int expectedSize, float loadFactor) {
        int minimum = Math.max(2, (int) Math.ceil(expectedSize / loadFactor));
        int capacity = 1;
        while (capacity < minimum) {
            capacity <<= 1;
        }
        return capacity;
    }

    private static int mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        value ^= value >>> 33;
        return (int) value;
    }

    public static class Node {
        public Location location;
        public Node parent;
        public double g;
        public double h;
        public double f;
        public boolean toBreak;
        public List<Location> blocksToBreak;
        public int moveType;
        public int dirX;
        public int dirZ;
        public boolean isFenceGate;
        public boolean isDoor;
        public boolean isBanner;
        private int heapIndex = -1;
        private boolean closed;

        Node(Location location, Node parent, double g, double h, boolean toBreak) {
            this.location = location;
            this.parent = parent;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.toBreak = toBreak;
            this.blocksToBreak = Collections.emptyList();
            this.moveType = MOVE_HORIZONTAL;
            this.dirX = 0;
            this.dirZ = 0;
            if (parent != null) {
                this.dirX = location.getBlockX() - parent.location.getBlockX();
                this.dirZ = location.getBlockZ() - parent.location.getBlockZ();

                int yDiff = location.getBlockY() - parent.location.getBlockY();
                if (yDiff > 0) {
                    this.moveType = MOVE_UP;
                } else if (yDiff < 0) {
                    this.moveType = MOVE_DOWN;
                }
            }
        }

        Node(Location location, Node parent, double g, double h, boolean toBreak, int moveType) {
            this.location = location;
            this.parent = parent;
            this.g = g;
            this.h = h;
            this.f = g + h;
            this.toBreak = toBreak;
            this.blocksToBreak = Collections.emptyList();
            this.moveType = moveType;
            this.dirX = 0;
            this.dirZ = 0;
            if (parent != null) {
                this.dirX = location.getBlockX() - parent.location.getBlockX();
                this.dirZ = location.getBlockZ() - parent.location.getBlockZ();
            }
        }

        private void setBlocksToBreak(List<Location> blocksToBreak) {
            if (blocksToBreak == null || blocksToBreak.isEmpty()) {
                this.blocksToBreak = Collections.emptyList();
                this.toBreak = false;
                return;
            }
            this.blocksToBreak = Collections.unmodifiableList(new ArrayList<>(blocksToBreak));
            this.toBreak = true;
        }

        private void populateDisplayFlags() {
            Block feet = location.getBlock();
            Block below = feet.getRelative(0, -1, 0);
            Block above = feet.getRelative(0, 1, 0);
            this.isFenceGate = isFenceGate(feet) || isFenceGate(below) || isFenceGate(above);
            this.isDoor = isDoor(feet) || isDoor(below) || isDoor(above);
            this.isBanner = isBanner(feet) || isBanner(below) || isBanner(above);
        }
    }

    public static boolean isFence(Block block) {
        String name = block.getType().name();
        return containsWithCache(name, "FENCE") && !containsWithCache(name, "GATE");
    }

    public static boolean isFenceGate(Block block) {
        return containsWithCache(block.getType().name(), "FENCE_GATE");
    }

    public static boolean isCarpet(Block block) {
        return containsWithCache(block.getType().name(), "CARPET");
    }

    public static boolean isSlab(Block block) {
        if (!containsWithCache(block.getType().name(), "SLAB")) {
            return false;
        }

        try {
            if (block.getBlockData() instanceof org.bukkit.block.data.type.Slab) {
                org.bukkit.block.data.type.Slab slabData = (org.bukkit.block.data.type.Slab) block.getBlockData();
                return slabData.getType() != org.bukkit.block.data.type.Slab.Type.DOUBLE;
            }
        } catch (Exception e) {
        }
        return true;
    }

    public static boolean isLantern(Block block) {
        String name = block.getType().name();
        return containsWithCache(name, "LANTERN");
    }

    public static boolean isCake(Block block) {
        return containsWithCache(block.getType().name(), "CAKE");
    }

    public static boolean isCandle(Block block) {
        return containsWithCache(block.getType().name(), "CANDLE");
    }

    public static boolean isCakeWithCandle(Block block) {
        String name = block.getType().name();
        return containsWithCache(name, "CANDLE_CAKE");
    }

    public static boolean isSlabStair(Block slabBlock) {
        if (!isSlab(slabBlock)) {
            return false;
        }

        Location loc = slabBlock.getLocation();

        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

        for (int[] dir : directions) {
            if (isStairPatternInDirection(loc, dir[0], dir[1])) {
                return true;
            }
        }

        return false;
    }

    private static boolean isStairPatternInDirection(Location startLoc, int dirX, int dirZ) {
        int stairCount = 0;
        boolean hasHeightChange = false;

        for (int step = 1; step <= 3; step++) {
            Location checkLoc = startLoc.clone().add(dirX * step, 0, dirZ * step);
            Block checkBlock = checkLoc.getBlock();

            if (isSlab(checkBlock)) {
                stairCount++;
                continue;
            }

            Block upperBlock = checkLoc.clone().add(0, 1, 0).getBlock();
            if (isSlab(upperBlock)) {
                stairCount++;
                hasHeightChange = true;
                continue;
            }

            break;
        }

        for (int step = 1; step <= 3; step++) {
            Location checkLoc = startLoc.clone().add(-dirX * step, 0, -dirZ * step);
            Block checkBlock = checkLoc.getBlock();

            if (isSlab(checkBlock)) {
                stairCount++;
                continue;
            }

            Block lowerBlock = checkLoc.clone().add(0, -1, 0).getBlock();
            if (isSlab(lowerBlock)) {
                stairCount++;
                hasHeightChange = true;
                continue;
            }

            break;
        }

        return stairCount >= 2 && hasHeightChange;
    }

    public static boolean isLowBlockButNotStair(Block block) {
        if (!isLowBlock(block)) {
            return false;
        }

        if (isSlab(block) && isSlabStair(block)) {
            return false;
        }

        return true;
    }

    public static boolean isLowBlock(Block block) {
        return isSlab(block) || isLantern(block) || (isCake(block) && !isCakeWithCandle(block)) || isCandle(block);
    }

    public static boolean isCompletelyPassable(Block block) {
        return block.isPassable() || isDoor(block) || isBanner(block) || isFenceGate(block);
    }

    public static boolean isLocationPassableForPlayer(Location location) {
        Block feet = location.getBlock();
        Block head = location.clone().add(0, 1, 0).getBlock();
        boolean feetPassable = isCompletelyPassable(feet);
        boolean headPassable = isCompletelyPassable(head);

        return feetPassable && headPassable;
    }
}
