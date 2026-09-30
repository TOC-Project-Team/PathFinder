package org.momu.pathfinder.navigation.locate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.StructureType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.momu.pathfinder.navigation.runtime.Scheduling;

import java.util.function.Consumer;

/**
 * Finds strongholds and, once the player is close, the end portal frame inside them.
 */
public final class StrongholdLocator {
    private StrongholdLocator() {
    }

    /** Asks the server for the nearest stronghold. Must run on the main thread; can take a while. */
    @SuppressWarnings("deprecation")
    public static Location locateNearest(Player player, int radius) {
        return player.getWorld().locateNearestStructure(player.getLocation(), StructureType.STRONGHOLD, radius, false);
    }

    /**
     * Scans the cube of {@code radius} blocks around {@code center} for the closest end portal frame off the
     * main thread. {@code callback} runs on the main thread with the frame, or {@code null}.
     */
    public static void findNearestPortalFrameAsync(Location center, int radius, Consumer<Location> callback) {
        Scheduling.runAsync(() -> {
            if (!Scheduling.isPluginEnabled()) {
                return;
            }
            Location frame = findNearestPortalFrame(center, radius);
            Scheduling.runSync(() -> callback.accept(frame));
        });
    }

    private static Location findNearestPortalFrame(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) {
            return null;
        }
        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y,
                            center.getBlockZ() + z);
                    if (block.getType() != Material.END_PORTAL_FRAME) {
                        continue;
                    }
                    Location frame = block.getLocation();
                    double distance = frame.distance(center);
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearest = frame;
                    }
                }
            }
        }
        return nearest;
    }
}
