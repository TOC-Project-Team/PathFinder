package org.momu.pathfinder.testsupport;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic terrains used by the golden-master tests. Changing anything here invalidates the golden files.
 */
public final class Scenarios {
    public static final int GROUND_Y = 64;

    public record Scenario(String name, FakeWorld world, Location start, Location end) {
    }

    private Scenarios() {
    }

    public static List<Scenario> all() {
        List<Scenario> scenarios = new ArrayList<>();
        scenarios.add(flat("flat-straight", 0, 0, 20, 0));
        scenarios.add(flat("flat-diagonal", 0, 0, 15, 12));
        scenarios.add(wallWithDoor());
        scenarios.add(solidWall());
        scenarios.add(unbreakableWall());
        scenarios.add(stairsUp());
        scenarios.add(ladderTower());
        scenarios.add(scaffoldingTower());
        scenarios.add(cliffDrop());
        scenarios.add(waterPool());
        scenarios.add(deepWater());
        scenarios.add(lavaMoat());
        scenarios.add(gapJump());
        scenarios.add(fenceWithCarpet());
        scenarios.add(slabsAndLanterns());
        scenarios.add(trapdoorsAndGates());
        scenarios.add(hazards());
        for (int seed = 1; seed <= 40; seed++) {
            scenarios.add(random(seed));
        }
        return scenarios;
    }

    private static FakeWorld ground(String name, int minX, int minZ, int maxX, int maxZ) {
        FakeWorld world = new FakeWorld(name);
        world.fill(minX, GROUND_Y - 3, minZ, maxX, GROUND_Y - 1, maxZ, Material.STONE);
        return world;
    }

    private static Scenario flat(String name, int sx, int sz, int ex, int ez) {
        FakeWorld world = ground(name, -10, -10, 30, 30);
        return new Scenario(name, world, world.loc(sx, GROUND_Y, sz), world.loc(ex, GROUND_Y, ez));
    }

    private static Scenario wallWithDoor() {
        FakeWorld world = ground("door", -10, -10, 30, 30);
        world.fill(10, GROUND_Y, -10, 10, GROUND_Y + 3, 30, Material.STONE_BRICKS);
        world.set(10, GROUND_Y, 5, Material.OAK_DOOR).set(10, GROUND_Y + 1, 5, Material.OAK_DOOR);
        world.set(10, GROUND_Y + 2, 5, Material.WHITE_BANNER);
        return new Scenario("door", world, world.loc(0, GROUND_Y, 0), world.loc(20, GROUND_Y, 0));
    }

    private static Scenario solidWall() {
        FakeWorld world = ground("solid-wall", -10, -10, 30, 30);
        world.fill(10, GROUND_Y, -10, 10, GROUND_Y + 3, 30, Material.DIRT);
        return new Scenario("solid-wall", world, world.loc(0, GROUND_Y, 0), world.loc(20, GROUND_Y, 0));
    }

    private static Scenario unbreakableWall() {
        FakeWorld world = ground("unbreakable-wall", -10, -10, 30, 30);
        world.fill(10, GROUND_Y, -10, 10, GROUND_Y + 3, 8, Material.OBSIDIAN);
        world.fill(10, GROUND_Y, 9, 10, GROUND_Y + 3, 30, Material.BEDROCK);
        world.set(10, GROUND_Y, 12, Material.AIR).set(10, GROUND_Y + 1, 12, Material.AIR);
        return new Scenario("unbreakable-wall", world, world.loc(0, GROUND_Y, 0), world.loc(20, GROUND_Y, 0));
    }

    private static Scenario stairsUp() {
        FakeWorld world = ground("stairs", -10, -10, 30, 30);
        for (int step = 0; step < 6; step++) {
            world.fill(5 + step, GROUND_Y, -2, 5 + step, GROUND_Y + step, 2, Material.COBBLESTONE);
        }
        world.fill(11, GROUND_Y, -2, 20, GROUND_Y + 5, 2, Material.COBBLESTONE);
        return new Scenario("stairs", world, world.loc(0, GROUND_Y, 0), world.loc(18, GROUND_Y + 6, 0));
    }

    private static Scenario ladderTower() {
        FakeWorld world = ground("ladder", -10, -10, 30, 30);
        world.fill(8, GROUND_Y, -5, 16, GROUND_Y + 7, 5, Material.STONE);
        world.fill(7, GROUND_Y, 0, 7, GROUND_Y + 8, 0, Material.LADDER);
        return new Scenario("ladder", world, world.loc(0, GROUND_Y, 0), world.loc(12, GROUND_Y + 8, 0));
    }

    private static Scenario scaffoldingTower() {
        FakeWorld world = ground("scaffolding", -10, -10, 30, 30);
        world.fill(8, GROUND_Y, -5, 16, GROUND_Y + 6, 5, Material.STONE);
        world.fill(7, GROUND_Y, 0, 7, GROUND_Y + 6, 0, Material.SCAFFOLDING);
        return new Scenario("scaffolding", world, world.loc(0, GROUND_Y, 0), world.loc(12, GROUND_Y + 7, 0));
    }

    private static Scenario cliffDrop() {
        FakeWorld world = ground("cliff", -10, -10, 30, 30);
        world.fill(-10, GROUND_Y, -10, 8, GROUND_Y + 7, 30, Material.STONE);
        return new Scenario("cliff", world, world.loc(0, GROUND_Y + 8, 0), world.loc(20, GROUND_Y, 3));
    }

    private static Scenario waterPool() {
        FakeWorld world = ground("water-pool", -10, -10, 30, 30);
        world.fill(4, GROUND_Y - 2, -8, 16, GROUND_Y - 1, 8, Material.WATER);
        world.set(9, GROUND_Y - 1, 1, Material.KELP_PLANT).set(9, GROUND_Y, 1, Material.KELP);
        return new Scenario("water-pool", world, world.loc(0, GROUND_Y, 0), world.loc(20, GROUND_Y, 0));
    }

    private static Scenario deepWater() {
        FakeWorld world = ground("deep-water", -10, -10, 30, 30);
        world.fill(-10, GROUND_Y - 8, -10, 30, GROUND_Y - 1, 30, Material.WATER);
        world.fill(-2, GROUND_Y - 8, -2, 2, GROUND_Y - 1, 2, Material.STONE);
        world.fill(18, GROUND_Y - 8, -2, 22, GROUND_Y - 1, 2, Material.STONE);
        return new Scenario("deep-water", world, world.loc(0, GROUND_Y, 0), world.loc(20, GROUND_Y, 0));
    }

    private static Scenario lavaMoat() {
        FakeWorld world = ground("lava", -10, -10, 30, 30);
        world.fill(8, GROUND_Y - 1, -10, 9, GROUND_Y - 1, 6, Material.LAVA);
        world.fill(8, GROUND_Y - 1, 10, 9, GROUND_Y - 1, 30, Material.LAVA);
        world.set(12, GROUND_Y, 8, Material.FIRE);
        return new Scenario("lava", world, world.loc(0, GROUND_Y, 0), world.loc(20, GROUND_Y, 0));
    }

    private static Scenario gapJump() {
        FakeWorld world = new FakeWorld("gap");
        world.fill(-5, GROUND_Y - 3, -5, 6, GROUND_Y - 1, 5, Material.STONE);
        world.fill(9, GROUND_Y - 3, -5, 20, GROUND_Y - 1, 5, Material.STONE);
        world.fill(7, GROUND_Y - 1, 2, 8, GROUND_Y - 1, 2, Material.OAK_FENCE);
        world.fill(7, GROUND_Y, 2, 8, GROUND_Y, 2, Material.WHITE_CARPET);
        return new Scenario("gap", world, world.loc(0, GROUND_Y, 0), world.loc(15, GROUND_Y, 0));
    }

    private static Scenario fenceWithCarpet() {
        FakeWorld world = ground("fence", -10, -10, 30, 30);
        world.fill(10, GROUND_Y, -10, 10, GROUND_Y, 30, Material.OAK_FENCE);
        world.set(10, GROUND_Y + 1, 3, Material.RED_CARPET);
        world.set(10, GROUND_Y, 8, Material.OAK_FENCE_GATE);
        return new Scenario("fence", world, world.loc(0, GROUND_Y, 0), world.loc(20, GROUND_Y, 0));
    }

    private static Scenario slabsAndLanterns() {
        FakeWorld world = ground("slabs", -10, -10, 30, 30);
        world.fill(5, GROUND_Y, -3, 5, GROUND_Y, 3, Material.OAK_SLAB);
        world.set(8, GROUND_Y, 0, Material.LANTERN).set(8, GROUND_Y, 1, Material.CAKE);
        world.set(8, GROUND_Y, -1, Material.CANDLE).set(8, GROUND_Y, 2, Material.CANDLE_CAKE);
        for (int i = 0; i < 4; i++) {
            world.set(12 + i, GROUND_Y + i / 2, 0, Material.STONE_SLAB);
            if (i % 2 == 1) {
                world.set(12 + i, GROUND_Y + i / 2 - 1, 0, Material.STONE);
            }
        }
        world.fill(11, GROUND_Y + 2, -4, 11, GROUND_Y + 2, 4, Material.SMOOTH_STONE_SLAB);
        return new Scenario("slabs", world, world.loc(0, GROUND_Y, 0), world.loc(20, GROUND_Y, 0));
    }

    private static Scenario trapdoorsAndGates() {
        FakeWorld world = ground("trapdoors", -10, -10, 30, 30);
        world.fill(6, GROUND_Y, -10, 6, GROUND_Y + 3, 30, Material.BRICKS);
        world.set(6, GROUND_Y, 0, Material.OAK_TRAPDOOR).set(6, GROUND_Y + 1, 0, Material.IRON_TRAPDOOR);
        world.set(6, GROUND_Y, 4, Material.SPRUCE_FENCE_GATE).set(6, GROUND_Y + 1, 4, Material.AIR);
        world.fill(12, GROUND_Y, -10, 12, GROUND_Y + 3, 30, Material.BRICKS);
        world.set(12, GROUND_Y, -3, Material.IRON_DOOR).set(12, GROUND_Y + 1, -3, Material.IRON_DOOR);
        return new Scenario("trapdoors", world, world.loc(0, GROUND_Y, 0), world.loc(20, GROUND_Y, 0));
    }

    private static Scenario hazards() {
        FakeWorld world = ground("hazards", -10, -10, 30, 30);
        world.fill(6, GROUND_Y, -3, 6, GROUND_Y, 3, Material.COBWEB);
        world.fill(9, GROUND_Y, -6, 9, GROUND_Y, 6, Material.SWEET_BERRY_BUSH);
        world.set(12, GROUND_Y, 0, Material.CACTUS).set(13, GROUND_Y + 1, 0, Material.POINTED_DRIPSTONE);
        world.set(15, GROUND_Y, 1, Material.POWDER_SNOW).set(15, GROUND_Y + 1, -1, Material.VINE);
        world.fill(17, GROUND_Y, -2, 17, GROUND_Y + 1, 2, Material.SPAWNER);
        return new Scenario("hazards", world, world.loc(0, GROUND_Y, 0), world.loc(20, GROUND_Y, 0));
    }

    /** Random hilly terrain with scattered features. */
    public static Scenario random(int seed) {
        Random random = new Random(seed);
        String name = "random-" + seed;
        FakeWorld world = new FakeWorld(name);
        int size = 36;
        int[][] height = new int[size][size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int base = x > 0 ? height[x - 1][z] : (z > 0 ? height[x][z - 1] : 0);
                if (x > 0 && z > 0) {
                    base = (height[x - 1][z] + height[x][z - 1]) / 2;
                }
                int delta = random.nextInt(9) - 4;
                height[x][z] = Math.max(-2, Math.min(5, base + (Math.abs(delta) == 4 ? Integer.signum(delta) : 0)));
                int top = GROUND_Y + height[x][z];
                world.fill(x, GROUND_Y - 6, z, x, top - 1, z, random.nextInt(6) == 0 ? Material.DIRT : Material.STONE);
            }
        }
        Material[] features = {
                Material.WATER, Material.LAVA, Material.OAK_DOOR, Material.LADDER, Material.SCAFFOLDING,
                Material.OAK_FENCE, Material.OAK_SLAB, Material.COBWEB, Material.WHITE_BANNER, Material.OAK_TRAPDOOR,
                Material.OAK_FENCE_GATE, Material.GLASS, Material.OBSIDIAN, Material.BEDROCK, Material.LANTERN,
                Material.KELP, Material.RED_CARPET, Material.IRON_TRAPDOOR, Material.CACTUS, Material.STONE_BRICKS
        };
        int featureCount = 18 + random.nextInt(18);
        for (int i = 0; i < featureCount; i++) {
            int x = 2 + random.nextInt(size - 4);
            int z = 2 + random.nextInt(size - 4);
            int top = GROUND_Y + height[x][z];
            Material feature = features[random.nextInt(features.length)];
            switch (feature) {
                case WATER -> world.fill(x, top - 2, z, x + random.nextInt(4), top - 1, z + random.nextInt(4), Material.WATER);
                case LAVA -> world.set(x, top - 1, z, Material.LAVA);
                case OAK_DOOR -> world.set(x, top, z, Material.OAK_DOOR).set(x, top + 1, z, Material.OAK_DOOR);
                case LADDER, SCAFFOLDING -> world.fill(x, top, z, x, top + 2 + random.nextInt(3), z, feature);
                case STONE_BRICKS, GLASS -> world.fill(x, top, z, x + (random.nextBoolean() ? 5 : 0), top + 2, z
                        + (random.nextBoolean() ? 0 : 5), feature);
                case OAK_FENCE -> {
                    world.set(x, top, z, Material.OAK_FENCE);
                    if (random.nextBoolean()) {
                        world.set(x, top + 1, z, Material.RED_CARPET);
                    }
                }
                case KELP -> world.set(x, top - 1, z, Material.WATER).set(x, top, z, Material.KELP);
                default -> world.set(x, top + (random.nextInt(4) == 0 ? 1 : 0), z, feature);
            }
        }
        int sx = 1 + random.nextInt(6);
        int sz = 1 + random.nextInt(size - 2);
        int ex = size - 2 - random.nextInt(6);
        int ez = 1 + random.nextInt(size - 2);
        return new Scenario(name, world, world.loc(sx, surface(world, sx, sz), sz), world.loc(ex, surface(world, ex, ez), ez));
    }

    private static int surface(FakeWorld world, int x, int z) {
        int y = GROUND_Y + 12;
        while (y > GROUND_Y - 6 && !world.get(x, y - 1, z).isSolid()) {
            y--;
        }
        return y;
    }
}
