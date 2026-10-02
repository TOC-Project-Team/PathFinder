package org.momu.pathfinder.navigation.locate;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.StructureType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.momu.pathfinder.navigation.runtime.ChunkSnapshots;
import org.momu.pathfinder.navigation.runtime.Scheduling;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Finds strongholds and, once the player is close, the end portal frame inside them.
 */
public final class StrongholdLocator {
    /** The portal frame search gives up after this long; it may have to generate hundreds of chunks. */
    private static final long FRAME_SEARCH_TIMEOUT_MILLIS = 180_000L;

    private StrongholdLocator() {
    }

    /** Asks the server for the nearest stronghold. Must run on the player's thread; can take a while. */
    @SuppressWarnings("deprecation")
    public static Location locateNearest(Player player, int radius) {
        return player.getWorld().locateNearestStructure(player.getLocation(), StructureType.STRONGHOLD, radius, false);
    }

    /**
     * Scans the cube of {@code radius} blocks around {@code center} for the closest end portal frame off the
     * main thread, loading (and if needed generating) the chunks it covers, nearest first, until no chunk left
     * can hold a closer frame. {@code callback} runs on {@code player}'s thread with the frame, or {@code null};
     * it is dropped if the player leaves.
     */
    public static void findNearestPortalFrameAsync(Player player, Location center, int radius,
                                                   Consumer<Location> callback) {
        Scheduling.runAsync(() -> {
            if (!Scheduling.isPluginEnabled()) {
                return;
            }
            long deadline = System.currentTimeMillis() + FRAME_SEARCH_TIMEOUT_MILLIS;
            Location frame = findNearestPortalFrame(center, radius,
                    () -> !player.isOnline() || System.currentTimeMillis() > deadline);
            Scheduling.runFor(player, () -> callback.accept(frame));
        });
    }

    private static Location findNearestPortalFrame(Location center, int radius, BooleanSupplier cancelled) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int minY = Math.max(world.getMinHeight(), center.getBlockY() - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, center.getBlockY() + radius);
        int minChunkX = (centerX - radius) >> 4, maxChunkX = (centerX + radius) >> 4;
        int minChunkZ = (centerZ - radius) >> 4, maxChunkZ = (centerZ + radius) >> 4;
        int centerChunkX = centerX >> 4, centerChunkZ = centerZ >> 4;

        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        // Every block of a chunk on ring r (in chunks around the center's chunk) is more than (r - 1) * 16
        // blocks from the center, so once a frame is that close, the remaining rings cannot hold a closer one.
        for (int ring = 0; !cancelled.getAsBoolean(); ring++) {
            if (nearestDistance <= (ring - 1) * 16.0) {
                break;
            }
            List<Long> ringChunks = new ArrayList<>();
            for (int chunkX = centerChunkX - ring; chunkX <= centerChunkX + ring; chunkX++) {
                for (int chunkZ = centerChunkZ - ring; chunkZ <= centerChunkZ + ring; chunkZ++) {
                    boolean onRing = Math.max(Math.abs(chunkX - centerChunkX), Math.abs(chunkZ - centerChunkZ)) == ring;
                    if (onRing && chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ
                            && chunkZ <= maxChunkZ) {
                        ringChunks.add(ChunkSnapshots.key(chunkX, chunkZ));
                    }
                }
            }
            if (ringChunks.isEmpty()) {
                break;
            }
            Map<Long, ChunkSnapshot> chunks = ChunkSnapshots.take(world, ringChunks, true, cancelled);
            for (Map.Entry<Long, ChunkSnapshot> entry : chunks.entrySet()) {
                ChunkSnapshot chunk = entry.getValue();
                int baseX = chunk.getX() << 4;
                int baseZ = chunk.getZ() << 4;
                for (int x = Math.max(baseX, centerX - radius); x <= Math.min(baseX + 15, centerX + radius); x++) {
                    for (int z = Math.max(baseZ, centerZ - radius); z <= Math.min(baseZ + 15, centerZ + radius); z++) {
                        for (int y = minY; y <= maxY; y++) {
                            if (chunk.getBlockType(x & 15, y, z & 15) != Material.END_PORTAL_FRAME) {
                                continue;
                            }
                            Location frame = new Location(world, x, y, z);
                            double distance = frame.distance(center);
                            if (distance < nearestDistance
                                    || (distance == nearestDistance && comesFirst(frame, nearest))) {
                                nearestDistance = distance;
                                nearest = frame;
                            }
                        }
                    }
                }
            }
        }
        return nearest;
    }

    /** Among equally near frames, the one with the lowest x, then y, then z wins. */
    private static boolean comesFirst(Location a, Location b) {
        if (a.getBlockX() != b.getBlockX()) {
            return a.getBlockX() < b.getBlockX();
        }
        if (a.getBlockY() != b.getBlockY()) {
            return a.getBlockY() < b.getBlockY();
        }
        return a.getBlockZ() < b.getBlockZ();
    }
}
