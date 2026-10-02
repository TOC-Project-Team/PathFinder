package org.momu.pathfinder.navigation.pathfinding;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.BoundingBox;

import java.util.Collection;

import static org.momu.pathfinder.navigation.pathfinding.PlayerBody.overlapsFootprint;

/**
 * What the search knows about one block position: its {@link BlockTypes} flags, its collision boxes and whether
 * it holds water.
 */
final class TerrainCell {
    static final double[] NO_BOXES = new double[0];
    static final double[] FULL_BOX = { 0, 0, 0, 1, 1, 1 };

    static final TerrainCell AIR = new TerrainCell(BlockTypes.AIR, NO_BOXES, false);
    /** Below the world: nothing to stand on and deadly to fall into. */
    static final TerrainCell VOID = new TerrainCell(BlockTypes.AIR | BlockTypes.BODY_HAZARD, NO_BOXES, false);
    /** Chunk not loaded: treated as a wall instead of loading it from the search thread. */
    static final TerrainCell UNLOADED = new TerrainCell(BlockTypes.UNBREAKABLE, FULL_BOX, false);

    final int flags;
    /** Collision boxes relative to the block origin: minX, minY, minZ, maxX, maxY, maxZ per box. */
    final double[] boxes;
    final boolean water;

    private TerrainCell(int flags, double[] boxes, boolean water) {
        this.flags = flags;
        this.boxes = boxes;
        this.water = water;
    }

    static TerrainCell of(Block block) {
        int flags = BlockTypes.flags(block.getType());
        if ((flags & BlockTypes.AIR) != 0) {
            return AIR;
        }
        boolean water = (flags & BlockTypes.WATER) != 0 || BlockTypes.isWaterlogged(block, block.getType());
        // Scaffolding reports no collision without a player to test against; it is solid from above.
        double[] boxes = (flags & BlockTypes.PLATFORM) != 0 ? FULL_BOX : collisionBoxes(block);
        return new TerrainCell(flags, boxes, water);
    }

    /**
     * The same as {@link #of(Block)} for a block copied out of the world (see {@link TerrainSnapshot}); {@code at}
     * is where it is, which offsets the shapes of blocks such as bamboo.
     */
    static TerrainCell of(Material type, BlockData data, Location at) {
        int flags = BlockTypes.flags(type);
        if ((flags & BlockTypes.AIR) != 0) {
            return AIR;
        }
        boolean water = (flags & BlockTypes.WATER) != 0 || (data instanceof Waterlogged w && w.isWaterlogged());
        double[] boxes;
        if ((flags & BlockTypes.PLATFORM) != 0) {
            boxes = FULL_BOX;
        } else {
            try {
                boxes = toArray(data.getCollisionShape(at).getBoundingBoxes());
            } catch (Throwable ignored) {
                boxes = type.isCollidable() ? FULL_BOX : NO_BOXES;
            }
        }
        return new TerrainCell(flags, boxes, water);
    }

    private static double[] collisionBoxes(Block block) {
        try {
            return toArray(block.getCollisionShape().getBoundingBoxes());
        } catch (Throwable ignored) {
            return block.isPassable() ? NO_BOXES : FULL_BOX;
        }
    }

    private static double[] toArray(Collection<BoundingBox> shape) {
        if (shape.isEmpty()) {
            return NO_BOXES;
        }
        double[] boxes = new double[shape.size() * 6];
        int i = 0;
        for (BoundingBox box : shape) {
            boxes[i++] = box.getMinX();
            boxes[i++] = box.getMinY();
            boxes[i++] = box.getMinZ();
            boxes[i++] = box.getMaxX();
            boxes[i++] = box.getMaxY();
            boxes[i++] = box.getMaxZ();
        }
        return boxes;
    }

    boolean has(int flag) {
        return (flags & flag) != 0;
    }

    /** Like {@link Block#isPassable()}: nothing to bump into. */
    boolean isPassable() {
        return boxes.length == 0;
    }

    /**
     * Highest top of a box under the footprint centred on {@code (px, pz)} with a top inside [low, high], or
     * {@code best} if none is higher.
     */
    double highestTop(int cellX, int cellY, int cellZ, double px, double pz, double low, double high, double best) {
        for (int k = 0; k < boxes.length; k += 6) {
            double top = cellY + boxes[k + 4];
            if (top < low || top > high || top <= best || !overlapsFootprint(boxes, k, cellX, cellZ, px, pz)) {
                continue;
            }
            best = top;
        }
        return best;
    }
}
