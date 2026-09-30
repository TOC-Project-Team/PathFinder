package org.momu.pathfinder.testsupport;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;

import java.util.HashMap;
import java.util.Map;

/**
 * A sparse block grid backed by proxy fakes of {@link World} and {@link Block}. Unset positions are air.
 */
public final class FakeWorld {
    public static final int MIN_HEIGHT = -64;
    public static final int MAX_HEIGHT = 320;

    private final Map<Long, Material> materials = new HashMap<>();
    private final World world;
    private final BlockData plainData = Proxies.fake(BlockData.class, (self, method, args) -> null);
    private final Slab slabData = Proxies.fake(Slab.class,
            (self, method, args) -> method.equals("getType") ? Slab.Type.BOTTOM : null);

    public FakeWorld(String name) {
        world = Proxies.fake(World.class, (self, method, args) -> switch (method) {
            case "getBlockAt" -> args.length == 1
                    ? blockAt((Location) args[0])
                    : blockAt((Integer) args[0], (Integer) args[1], (Integer) args[2]);
            case "getMinHeight" -> MIN_HEIGHT;
            case "getMaxHeight" -> MAX_HEIGHT;
            case "getName" -> name;
            case "toString" -> "FakeWorld[" + name + "]";
            default -> null;
        });
    }

    public World world() {
        return world;
    }

    public Location loc(double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    public FakeWorld set(int x, int y, int z, Material material) {
        materials.put(key(x, y, z), material);
        return this;
    }

    public FakeWorld fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, material);
                }
            }
        }
        return this;
    }

    public Material get(int x, int y, int z) {
        return materials.getOrDefault(key(x, y, z), Material.AIR);
    }

    private Block blockAt(Location location) {
        return blockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private Block blockAt(int x, int y, int z) {
        return createBlock(x, y, z);
    }

    private Block createBlock(int x, int y, int z) {
        return Proxies.fake(Block.class, (self, method, args) -> switch (method) {
            case "getType" -> get(x, y, z);
            case "isPassable" -> TestMaterials.isPassable(get(x, y, z));
            case "isEmpty" -> get(x, y, z).isAir();
            case "isLiquid" -> get(x, y, z) == Material.WATER || get(x, y, z) == Material.LAVA;
            case "getRelative" -> args.length == 3
                    ? blockAt(x + (Integer) args[0], y + (Integer) args[1], z + (Integer) args[2])
                    : null;
            case "getLocation" -> args.length == 0 ? new Location(world, x, y, z) : null;
            case "getBlockData" -> get(x, y, z).name().endsWith("_SLAB") ? slabData : plainData;
            case "getWorld" -> world;
            case "getX" -> x;
            case "getY" -> y;
            case "getZ" -> z;
            case "hasMetadata" -> false;
            case "toString" -> "Block[" + x + "," + y + "," + z + "=" + get(x, y, z) + "]";
            default -> null;
        });
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }
}
