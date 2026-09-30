package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;

import java.util.function.Supplier;

/**
 * Classifies materials for movement. Shape questions (how tall a block is, whether it can be walked through) are
 * answered from the block's collision shape by {@link TerrainView}; this class only covers what a shape cannot
 * tell: climbing, water, doors the player can open, hazards and fall damage.
 *
 * <p>Classes are looked up from Minecraft's block tags where one exists, with a name-based fallback, so every
 * wood type, color and future variant is covered automatically.</p>
 */
public final class BlockTypes {
    static final int AIR = 1 << 1;
    static final int WATER = 1 << 2;
    static final int LAVA = 1 << 3;
    static final int CLIMBABLE = 1 << 4;
    /** Scaffolding: solid when standing on top of it, walk-through and climbable from inside. */
    static final int PLATFORM = 1 << 5;
    /** Doors, trapdoors and fence gates the player can open by hand. */
    static final int OPENABLE = 1 << 6;
    static final int DOOR = 1 << 7;
    static final int TRAPDOOR = 1 << 8;
    static final int FENCE_GATE = 1 << 9;
    /** Hurts, traps or teleports the player when the body is inside it. */
    static final int BODY_HAZARD = 1 << 10;
    /** Hurts or drops the player when stood on. */
    static final int FLOOR_HAZARD = 1 << 11;
    static final int NO_JUMP = 1 << 12;
    static final int SLOW = 1 << 13;
    static final int UNBREAKABLE = 1 << 14;
    static final int NO_FALL_DAMAGE = 1 << 15;
    static final int FALL_DAMAGE_20 = 1 << 16;
    static final int FALL_DAMAGE_50 = 1 << 17;
    static final int LADDER = 1 << 18;
    static final int BANNER = 1 << 19;
    private static final int KNOWN = 1;

    private static final int[] FLAGS = new int[Material.values().length];
    /** 0 = unknown, 1 = cannot be waterlogged, 2 = can be waterlogged. */
    private static final byte[] WATERLOGGABLE = new byte[Material.values().length];

    private BlockTypes() {
    }

    static int flags(Material material) {
        int ordinal = material.ordinal();
        int flags = FLAGS[ordinal];
        if (flags == 0) {
            flags = classify(material);
            FLAGS[ordinal] = flags;
        }
        return flags;
    }

    static boolean has(Block block, int flag) {
        return (flags(block.getType()) & flag) != 0;
    }

    private static int classify(Material material) {
        String name = material.name();
        int flags = KNOWN;
        if (name.equals("AIR") || name.equals("CAVE_AIR") || name.equals("VOID_AIR")) {
            return flags | AIR;
        }

        if (name.equals("WATER") || name.equals("BUBBLE_COLUMN") || name.equals("KELP")
                || name.equals("KELP_PLANT") || name.equals("SEAGRASS") || name.equals("TALL_SEAGRASS")) {
            flags |= WATER;
        }
        if (name.equals("LAVA") || name.equals("LAVA_CAULDRON")) {
            flags |= LAVA | BODY_HAZARD | FLOOR_HAZARD;
        }

        if (isTagged(() -> Tag.CLIMBABLE, material) || name.equals("LADDER") || name.equals("VINE")
                || name.endsWith("_VINES") || name.endsWith("_VINES_PLANT") || name.equals("SCAFFOLDING")) {
            flags |= CLIMBABLE;
        }
        if (name.equals("LADDER")) {
            flags |= LADDER;
        }
        if (name.equals("SCAFFOLDING")) {
            flags |= PLATFORM;
        }

        boolean trapdoor = isTagged(() -> Tag.TRAPDOORS, material) || name.endsWith("TRAPDOOR");
        boolean door = !trapdoor && (isTagged(() -> Tag.DOORS, material) || name.endsWith("_DOOR"));
        boolean gate = isTagged(() -> Tag.FENCE_GATES, material) || name.endsWith("FENCE_GATE");
        if (trapdoor) {
            flags |= TRAPDOOR;
        }
        if (door) {
            flags |= DOOR;
        }
        if (gate) {
            flags |= FENCE_GATE;
        }
        if ((trapdoor || door || gate) && !name.startsWith("IRON_")) {
            flags |= OPENABLE;
        }
        if (name.contains("BANNER")) {
            flags |= BANNER;
        }

        if (isTagged(() -> Tag.FIRE, material) || name.equals("FIRE") || name.equals("SOUL_FIRE")) {
            flags |= BODY_HAZARD;
        }
        if (isTagged(() -> Tag.CAMPFIRES, material) || name.endsWith("CAMPFIRE")) {
            flags |= BODY_HAZARD | FLOOR_HAZARD;
        }
        if (isTagged(() -> Tag.PORTALS, material) || name.equals("NETHER_PORTAL") || name.equals("END_PORTAL")
                || name.equals("END_GATEWAY")) {
            flags |= BODY_HAZARD;
        }
        switch (name) {
            case "CACTUS" -> flags |= BODY_HAZARD | FLOOR_HAZARD;
            case "SWEET_BERRY_BUSH", "WITHER_ROSE", "COBWEB", "POWDER_SNOW" -> flags |= BODY_HAZARD;
            case "MAGMA_BLOCK", "POINTED_DRIPSTONE", "BIG_DRIPLEAF" -> flags |= FLOOR_HAZARD;
            case "HONEY_BLOCK" -> flags |= NO_JUMP | SLOW | FALL_DAMAGE_20;
            case "SOUL_SAND" -> flags |= SLOW;
            case "SLIME_BLOCK" -> flags |= NO_FALL_DAMAGE;
            case "HAY_BLOCK" -> flags |= FALL_DAMAGE_20;
            default -> {
            }
        }
        if (isTagged(() -> Tag.BEDS, material) || name.endsWith("_BED")) {
            flags |= FALL_DAMAGE_50;
        }

        float hardness;
        try {
            hardness = material.getHardness();
        } catch (Throwable ignored) {
            hardness = 1.0f;
        }
        if (hardness < 0 || hardness >= 50 || trapdoor || door || gate
                || name.equals("BEDROCK") || name.equals("BARRIER") || name.equals("LIGHT")
                || name.endsWith("SPAWNER") || name.equals("VAULT") || name.equals("END_PORTAL_FRAME")
                || name.contains("COMMAND_BLOCK") || name.equals("STRUCTURE_BLOCK") || name.equals("JIGSAW")
                || name.equals("REINFORCED_DEEPSLATE") || (flags & BODY_HAZARD) != 0) {
            flags |= UNBREAKABLE;
        }
        return flags;
    }

    /** Tag lookups are deferred so a tag missing from a newer or older server cannot break classification. */
    private static boolean isTagged(Supplier<Tag<Material>> tagSupplier, Material material) {
        try {
            Tag<Material> tag = tagSupplier.get();
            return tag != null && tag.isTagged(material);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean isWaterlogged(Block block, Material type) {
        int ordinal = type.ordinal();
        if (WATERLOGGABLE[ordinal] == 1) {
            return false;
        }
        BlockData data = block.getBlockData();
        boolean waterloggable = data instanceof Waterlogged;
        WATERLOGGABLE[ordinal] = waterloggable ? (byte) 2 : (byte) 1;
        return waterloggable && ((Waterlogged) data).isWaterlogged();
    }

    /** Water, bubble columns, underwater plants and waterlogged blocks. */
    public static boolean isWater(Block block) {
        Material type = block.getType();
        int flags = flags(type);
        if ((flags & WATER) != 0) {
            return true;
        }
        return (flags & AIR) == 0 && isWaterlogged(block, type);
    }

    /** Doors of any kind, <em>including trapdoors</em>. */
    public static boolean isAnyDoor(Block block) {
        return has(block, DOOR | TRAPDOOR);
    }

    public static boolean isTrapdoor(Block block) {
        return has(block, TRAPDOOR);
    }

    public static boolean isIronTrapdoor(Block block) {
        return has(block, TRAPDOOR) && !has(block, OPENABLE);
    }

    /** Trapdoors the player can open by hand (everything except iron trapdoors). */
    public static boolean isPassableTrapdoor(Block block) {
        return has(block, TRAPDOOR) && has(block, OPENABLE);
    }

    public static boolean isBanner(Block block) {
        return has(block, BANNER);
    }

    public static boolean isLadder(Block block) {
        return has(block, LADDER);
    }

    /** Ladders, every kind of vine and scaffolding. */
    public static boolean isClimbable(Block block) {
        return has(block, CLIMBABLE);
    }

    public static boolean isScaffolding(Block block) {
        return has(block, PLATFORM);
    }

    public static boolean isFenceGate(Block block) {
        return has(block, FENCE_GATE);
    }

    public static boolean isLava(Block block) {
        return has(block, LAVA);
    }

    /** Lava or anything burning (fire, soul fire, campfires). */
    public static boolean isLavaOrFire(Block block) {
        Material type = block.getType();
        return (flags(type) & LAVA) != 0 || ((flags(type) & BODY_HAZARD) != 0 && type.name().contains("FIRE"));
    }

    /** Whether the block has a collision box, i.e. something to stand on or bump into. */
    public static boolean hasCollision(Block block) {
        return !has(block, AIR) && !block.isPassable();
    }
}
