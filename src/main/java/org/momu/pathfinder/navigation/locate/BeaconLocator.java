package org.momu.pathfinder.navigation.locate;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.momu.pathfinder.config.Messages;
import org.momu.pathfinder.navigation.runtime.NavigationTasks;
import org.momu.pathfinder.navigation.runtime.Scheduling;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Finds the beacon closest to a player by scanning outward in square rings, off the main thread.
 */
public final class BeaconLocator {
    /** Only blocks within this many blocks above or below the player are scanned. */
    private static final int VERTICAL_RANGE = 50;
    /** Once a beacon has been found, scanning continues until at least this ring. */
    private static final int MIN_RINGS_AFTER_FIND = 10;
    private static final long TIMEOUT_TICKS = 20 * 20;

    private BeaconLocator() {
    }

    /**
     * Searches for the nearest beacon within {@code maxRadius} blocks horizontally. The player is told about the
     * search and its result. {@code callback} runs on the main thread with the beacon's center, or {@code null}
     * if none was found or the search timed out after 20 seconds.
     */
    @SuppressWarnings("deprecation")
    public static void findNearestAsync(Player player, int maxRadius, Consumer<Location> callback) {
        if (!Scheduling.isPluginEnabled()) {
            return;
        }
        Location origin = player.getLocation().clone();
        World world = origin.getWorld();
        if (world == null) {
            callback.accept(null);
            return;
        }
        UUID playerId = player.getUniqueId();
        NavigationTasks.getInstance().cancelBeaconSearch(playerId);

        int searchId = (int) (Math.random() * 9000) + 1000;
        player.sendMessage(ChatColor.YELLOW + Messages.get(player, "messages.searching-for-beacon", searchId));
        player.sendMessage(ChatColor.GRAY + Messages.get(player, "messages.search-radius", maxRadius) + ", "
                + Messages.get(player, "messages.player-location",
                origin.getBlockX(), origin.getBlockY(), origin.getBlockZ()));

        AtomicBoolean finished = new AtomicBoolean(false);
        BukkitTask search = Scheduling.runAsync(new BukkitRunnable() {
            @Override
            public void run() {
                Location beacon = scan(world, origin, maxRadius, this);
                if (isCancelled()) {
                    return;
                }
                Scheduling.runSync(() -> {
                    if (!Scheduling.isPluginEnabled() || !finished.compareAndSet(false, true)) {
                        return;
                    }
                    NavigationTasks.getInstance().cancelBeaconSearch(playerId);
                    if (beacon == null) {
                        callback.accept(null);
                        return;
                    }
                    String coordinates = String.format("x: %d, y: %d, z: %d",
                            beacon.getBlockX(), beacon.getBlockY(), beacon.getBlockZ());
                    Location center = beacon.clone().add(0.5, 0.5, 0.5);
                    player.sendMessage(ChatColor.AQUA + Messages.get(player, "messages.beacon-coords",
                            coordinates, String.format("%.1f", origin.distance(center))));
                    callback.accept(center);
                });
            }
        });
        if (search == null) {
            return;
        }

        BukkitTask timeout = Scheduling.runLaterAsync(() -> {
            if (!Scheduling.isPluginEnabled() || search.isCancelled()) {
                search.cancel();
                return;
            }
            search.cancel();
            Scheduling.runSync(() -> {
                if (!Scheduling.isPluginEnabled() || !finished.compareAndSet(false, true)) {
                    return;
                }
                NavigationTasks.getInstance().cancelBeaconSearch(playerId);
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.RED + Messages.get(player, "messages.beacon-search-timeout", searchId));
                    callback.accept(null);
                }
            });
        }, TIMEOUT_TICKS);
        if (timeout == null) {
            search.cancel();
            return;
        }
        NavigationTasks.getInstance().setBeaconSearch(playerId, search, timeout);
    }

    /** Scans square rings around {@code origin}; returns the beacon block closest to it, or {@code null}. */
    private static Location scan(World world, Location origin, int maxRadius, BukkitRunnable task) {
        int originX = origin.getBlockX();
        int originY = origin.getBlockY();
        int originZ = origin.getBlockZ();
        int minY = Math.max(world.getMinHeight(), originY - VERTICAL_RANGE);
        int maxY = Math.min(world.getMaxHeight() - 1, originY + VERTICAL_RANGE);

        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int radius = 0; radius <= maxRadius && !task.isCancelled(); radius++) {
            for (int x = originX - radius; x <= originX + radius && !task.isCancelled(); x++) {
                for (int z = originZ - radius; z <= originZ + radius && !task.isCancelled(); z++) {
                    boolean onRing = radius == 0 || x == originX - radius || x == originX + radius
                            || z == originZ - radius || z == originZ + radius;
                    if (!onRing) {
                        continue;
                    }
                    for (int y : columnOrder(originY, minY, maxY)) {
                        if (world.getBlockAt(x, y, z).getType() != Material.BEACON) {
                            continue;
                        }
                        Location beacon = new Location(world, x, y, z);
                        double distance = origin.distanceSquared(beacon.clone().add(0.5, 0.5, 0.5));
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearest = beacon;
                        }
                    }
                }
            }
            if (nearest != null && radius > MIN_RINGS_AFTER_FIND) {
                break;
            }
        }
        return nearest;
    }

    /** Y levels of a column ordered by distance from the player's height: y, y+1, y-1, y+2, y-2, ... */
    private static int[] columnOrder(int originY, int minY, int maxY) {
        int[] order = new int[Math.max(0, maxY - minY + 1)];
        int count = 0;
        if (originY >= minY && originY <= maxY) {
            order[count++] = originY;
        }
        for (int offset = 1; offset <= Math.max(originY - minY, maxY - originY); offset++) {
            if (originY + offset <= maxY) {
                order[count++] = originY + offset;
            }
            if (originY - offset >= minY) {
                order[count++] = originY - offset;
            }
        }
        return count == order.length ? order : java.util.Arrays.copyOf(order, count);
    }
}
