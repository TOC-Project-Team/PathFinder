package org.momu.pathfinder.api;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Immutable view of a saved waypoint. Modify waypoints through {@link PathFinderAPI}.
 *
 * @param name      display name
 * @param key       normalized lookup key (lower-case name)
 * @param worldName name of the world the waypoint lives in
 * @param createdAt creation time in epoch milliseconds
 * @param updatedAt last modification time in epoch milliseconds
 */
public record WaypointSnapshot(String name,
                               String key,
                               String worldName,
                               double x,
                               double y,
                               double z,
                               long createdAt,
                               long updatedAt) {

    /**
     * @return the waypoint as a Bukkit location, or {@code null} if its world is not loaded
     */
    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }
}
