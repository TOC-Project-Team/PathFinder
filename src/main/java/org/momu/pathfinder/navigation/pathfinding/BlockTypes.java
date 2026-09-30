package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Classifies blocks for movement. Most groups are matched by material name so that every wood type, color
 * and future variant is covered automatically.
 */
public final class BlockTypes {
    /** Blocks the player must never walk through. */
    static final Set<Material> OBSTACLES = EnumSet.of(Material.CACTUS, Material.COBWEB, Material.SWEET_BERRY_BUSH,
            Material.VINE, Material.POWDER_SNOW, Material.POINTED_DRIPSTONE);

    /** Every material whose name contains "DOOR", which includes trapdoors. */
    private static final Set<Material> ANY_DOORS = byName(name -> name.contains("DOOR"));
    private static final Set<Material> TRAPDOORS = byName(name -> name.contains("TRAPDOOR"));
    private static final Set<Material> IRON_TRAPDOORS = byName(name -> name.contains("IRON_TRAPDOOR"));
    private static final Set<Material> BANNERS = byName(name -> name.contains("BANNER"));
    private static final Set<Material> LADDERS = byName(name -> name.contains("LADDER"));
    private static final Set<Material> SCAFFOLDING = byName(name -> name.contains("SCAFFOLDING"));
    private static final Set<Material> FENCES = byName(name -> name.contains("FENCE") && !name.contains("GATE"));
    private static final Set<Material> FENCE_GATES = byName(name -> name.contains("FENCE_GATE"));
    private static final Set<Material> CARPETS = byName(name -> name.contains("CARPET"));
    private static final Set<Material> SLABS = byName(name -> name.contains("SLAB"));
    private static final Set<Material> KELP = byName(name -> name.contains("KELP"));
    /** Any material containing "WATER": water itself and waterlogged-style blocks such as water cauldrons. */
    private static final Set<Material> WATER = byName(name -> name.contains("WATER"));
    private static final Set<Material> LAVA = byName(name -> name.contains("LAVA"));
    private static final Set<Material> LAVA_OR_FIRE = byName(name -> name.contains("LAVA") || name.contains("FIRE"));
    /** Short blocks the player can step onto without jumping: slabs, lanterns, cakes and candles. */
    private static final Set<Material> LOW_BLOCKS = byName(name -> name.contains("SLAB") || name.contains("LANTERN")
            || name.contains("CAKE") || name.contains("CANDLE"));
    private static final Set<Material> UNBREAKABLE = byName(name -> name.contains("BEDROCK") || name.contains("PORTAL")
            || name.contains("SPAWNER") || name.contains("BARRIER") || name.contains("END_GATEWAY"));

    private static final int[][] SLAB_STAIR_DIRECTIONS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    private BlockTypes() {
    }

    private static Set<Material> byName(Predicate<String> predicate) {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (Material material : Material.values()) {
            if (predicate.test(material.name())) {
                materials.add(material);
            }
        }
        return materials;
    }

    /** Doors of any kind, <em>including trapdoors</em>. */
    public static boolean isAnyDoor(Block block) {
        return ANY_DOORS.contains(block.getType());
    }

    public static boolean isTrapdoor(Block block) {
        return TRAPDOORS.contains(block.getType());
    }

    public static boolean isIronTrapdoor(Block block) {
        return IRON_TRAPDOORS.contains(block.getType());
    }

    /** Trapdoors the player can open by hand (everything except iron trapdoors). */
    public static boolean isPassableTrapdoor(Block block) {
        return isTrapdoor(block) && !isIronTrapdoor(block);
    }

    public static boolean isBanner(Block block) {
        return BANNERS.contains(block.getType());
    }

    public static boolean isLadder(Block block) {
        return LADDERS.contains(block.getType());
    }

    public static boolean isScaffolding(Block block) {
        return SCAFFOLDING.contains(block.getType());
    }

    public static boolean isFence(Block block) {
        return FENCES.contains(block.getType());
    }

    public static boolean isFenceGate(Block block) {
        return FENCE_GATES.contains(block.getType());
    }

    public static boolean isCarpet(Block block) {
        return CARPETS.contains(block.getType());
    }

    public static boolean isKelp(Block block) {
        return KELP.contains(block.getType());
    }

    public static boolean isWater(Block block) {
        return WATER.contains(block.getType());
    }

    public static boolean isLava(Block block) {
        return LAVA.contains(block.getType());
    }

    /** Lava or anything burning (fire, soul fire, campfires). */
    public static boolean isLavaOrFire(Block block) {
        return LAVA_OR_FIRE.contains(block.getType());
    }

    public static boolean isObstacle(Block block) {
        return OBSTACLES.contains(block.getType());
    }

    public static boolean isUnbreakable(Block block) {
        return UNBREAKABLE.contains(block.getType());
    }

    /** A fence with a carpet on top and room above it, which the player can walk over. */
    public static boolean isJumpableFence(Block fence) {
        if (!isFence(fence)) {
            return false;
        }
        Block carpet = fence.getRelative(0, 1, 0);
        return isCarpet(carpet) && carpet.getRelative(0, 1, 0).isPassable();
    }

    /** A single (non-double) slab. */
    public static boolean isSlab(Block block) {
        if (!SLABS.contains(block.getType())) {
            return false;
        }
        try {
            BlockData data = block.getBlockData();
            if (data instanceof Slab slab) {
                return slab.getType() != Slab.Type.DOUBLE;
            }
        } catch (Exception ignored) {
            // Fall through: treat unreadable slab data as a single slab.
        }
        return true;
    }

    public static boolean isLowBlock(Block block) {
        return LOW_BLOCKS.contains(block.getType()) && (!SLABS.contains(block.getType()) || isSlab(block));
    }

    /**
     * A low block the player has to step onto. Slabs that form a staircase are excluded because they can be
     * walked up like stairs.
     */
    public static boolean isLowBlockButNotStair(Block block) {
        if (!isLowBlock(block)) {
            return false;
        }
        return !(isSlab(block) && isSlabStair(block));
    }

    /** Whether this slab is part of a run of slabs that changes height, i.e. a slab staircase. */
    private static boolean isSlabStair(Block slab) {
        for (int[] direction : SLAB_STAIR_DIRECTIONS) {
            if (isSlabStairInDirection(slab, direction[0], direction[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSlabStairInDirection(Block slab, int dirX, int dirZ) {
        int slabCount = 0;
        boolean heightChanges = false;

        for (int step = 1; step <= 3; step++) {
            Block ahead = slab.getRelative(dirX * step, 0, dirZ * step);
            if (isSlab(ahead)) {
                slabCount++;
                continue;
            }
            if (isSlab(ahead.getRelative(0, 1, 0))) {
                slabCount++;
                heightChanges = true;
                continue;
            }
            break;
        }

        for (int step = 1; step <= 3; step++) {
            Block behind = slab.getRelative(-dirX * step, 0, -dirZ * step);
            if (isSlab(behind)) {
                slabCount++;
                continue;
            }
            if (isSlab(behind.getRelative(0, -1, 0))) {
                slabCount++;
                heightChanges = true;
                continue;
            }
            break;
        }

        return slabCount >= 2 && heightChanges;
    }
}
