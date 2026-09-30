package org.momu.pathfinder.waypoint.service;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.momu.pathfinder.waypoint.model.Waypoint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes {@code waypoints.yml}.
 */
final class WaypointStorage {
    private final File file;

    WaypointStorage(File file) {
        this.file = file;
    }

    List<Waypoint> load() {
        List<Waypoint> waypoints = new ArrayList<>();
        if (!file.exists()) {
            return waypoints;
        }
        ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("waypoints");
        if (section == null) {
            return waypoints;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            waypoints.add(new Waypoint(entry.getString("name", key), entry.getString("world", "world"),
                    entry.getDouble("x"), entry.getDouble("y"), entry.getDouble("z")));
        }
        return waypoints;
    }

    /** Writes a snapshot of the waypoints. Safe to call from any thread. */
    void save(List<Snapshot> waypoints) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("waypoints");
        for (Snapshot waypoint : waypoints) {
            ConfigurationSection entry = root.createSection(waypoint.key());
            entry.set("name", waypoint.name());
            entry.set("world", waypoint.world());
            entry.set("x", waypoint.x());
            entry.set("y", waypoint.y());
            entry.set("z", waypoint.z());
            entry.set("createdAt", waypoint.createdAt());
            entry.set("updatedAt", waypoint.updatedAt());
        }
        yaml.save(file);
    }

    /** Immutable copy of a waypoint taken on the main thread for writing on another. */
    record Snapshot(String key, String name, String world, double x, double y, double z, long createdAt,
                    long updatedAt) {
        static Snapshot of(Waypoint waypoint) {
            return new Snapshot(waypoint.getKey(), waypoint.getName(), waypoint.getWorld(), waypoint.getX(),
                    waypoint.getY(), waypoint.getZ(), waypoint.getCreatedAt(), waypoint.getUpdatedAt());
        }
    }
}
