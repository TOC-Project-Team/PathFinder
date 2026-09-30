package org.momu.pathfinder.testsupport;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/**
 * Deterministic approximation of vanilla block properties, good enough for pathfinding tests.
 */
public final class TestMaterials {
    private static final Set<Material> AIR = EnumSet.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);
    private static final Set<Material> NON_SOLID = EnumSet.of(
            Material.WATER, Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.LADDER, Material.VINE,
            Material.COBWEB, Material.SWEET_BERRY_BUSH, Material.KELP, Material.KELP_PLANT, Material.SEAGRASS,
            Material.SHORT_GRASS, Material.TALL_GRASS, Material.POWDER_SNOW, Material.TORCH, Material.RAIL);

    private TestMaterials() {
    }

    public static boolean isAir(Material material) {
        return AIR.contains(material);
    }

    public static boolean isSolid(Material material) {
        if (isAir(material) || NON_SOLID.contains(material)) {
            return false;
        }
        String name = material.name();
        return !name.contains("BANNER") && !name.contains("SIGN");
    }

    public static boolean isPassable(Material material) {
        return !isSolid(material);
    }

    public static float hardness(Material material) {
        return switch (material) {
            case BEDROCK, BARRIER, END_PORTAL_FRAME -> -1.0f;
            case OBSIDIAN, CRYING_OBSIDIAN -> 50.0f;
            default -> isAir(material) ? 0.0f : 1.5f;
        };
    }
}
