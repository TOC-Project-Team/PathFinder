package org.momu.pathfinder.waypoint.service;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.momu.pathfinder.bootstrap.PathFinderPlugin;
import org.momu.pathfinder.navigation.runtime.Scheduling;
import org.momu.pathfinder.waypoint.model.Waypoint;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The saved waypoints. Every change is written to {@code waypoints.yml} in the background.
 */
public class WaypointService {
    private static final WaypointService INSTANCE = new WaypointService();

    private final Map<String, Waypoint> waypoints = new HashMap<>();
    private WaypointStorage storage;

    private WaypointService() {
    }

    public static WaypointService getInstance() {
        return INSTANCE;
    }

    /** (Re)loads the waypoints from the plugin's data folder. */
    public synchronized void init(PathFinderPlugin plugin) {
        storage = new WaypointStorage(new File(plugin.getDataFolder(), "waypoints.yml"));
        waypoints.clear();
        for (Waypoint waypoint : storage.load()) {
            waypoints.put(waypoint.getKey(), waypoint);
        }
    }

    /** @return {@code false} if the name is empty or taken, the world is not loaded, or y is out of range */
    public synchronized boolean add(String name, String world, double x, double y, double z) {
        if (name == null || name.trim().isEmpty()) return false;
        String key = Waypoint.keyOf(name);
        if (waypoints.containsKey(key)) return false;
        if (!isValidPosition(world, y)) return false;
        waypoints.put(key, new Waypoint(name, world, x, y, z));
        saveAsync();
        return true;
    }

    public synchronized boolean remove(String name) {
        if (waypoints.remove(Waypoint.keyOf(name)) == null) return false;
        saveAsync();
        return true;
    }

    public synchronized boolean rename(String oldName, String newName) {
        String oldKey = Waypoint.keyOf(oldName);
        String newKey = Waypoint.keyOf(newName);
        if (!waypoints.containsKey(oldKey) || waypoints.containsKey(newKey)) return false;
        Waypoint waypoint = waypoints.remove(oldKey);
        waypoint.setName(newName);
        waypoints.put(newKey, waypoint);
        saveAsync();
        return true;
    }

    /**
     * Changes one field. {@code field} is {@code x}, {@code y}, {@code z} or {@code world}; a coordinate that is
     * not a number leaves the old value in place.
     */
    public synchronized boolean setField(String name, String field, String value) {
        Waypoint waypoint = waypoints.get(Waypoint.keyOf(name));
        if (waypoint == null) return false;
        switch (field.toLowerCase(Locale.ROOT)) {
            case "x" -> waypoint.setX(parseDouble(value, waypoint.getX()));
            case "y" -> waypoint.setY(parseDouble(value, waypoint.getY()));
            case "z" -> waypoint.setZ(parseDouble(value, waypoint.getZ()));
            case "world" -> {
                if (Bukkit.getWorld(value) == null) return false;
                waypoint.setWorld(value);
            }
            default -> {
                return false;
            }
        }
        saveAsync();
        return true;
    }

    public synchronized boolean move(String name, String world, double x, double y, double z) {
        Waypoint waypoint = waypoints.get(Waypoint.keyOf(name));
        if (waypoint == null || !isValidPosition(world, y)) return false;
        waypoint.setWorld(world);
        waypoint.setX(x);
        waypoint.setY(y);
        waypoint.setZ(z);
        saveAsync();
        return true;
    }

    public synchronized Waypoint get(String name) {
        return waypoints.get(Waypoint.keyOf(name));
    }

    /** All waypoints sorted by name, optionally only those in one world. */
    public synchronized List<Waypoint> list(String worldName) {
        List<Waypoint> list = new ArrayList<>(waypoints.values());
        if (worldName != null) {
            list.removeIf(waypoint -> !waypoint.getWorld().equals(worldName));
        }
        list.sort(Comparator.comparing(Waypoint::getName, String::compareToIgnoreCase));
        return list;
    }

    private static boolean isValidPosition(String worldName, double y) {
        World world = Bukkit.getWorld(worldName);
        return world != null && y >= world.getMinHeight() && y < world.getMaxHeight();
    }

    private void saveAsync() {
        PathFinderPlugin plugin = PathFinderPlugin.getInstance();
        if (plugin == null || storage == null) return;
        WaypointStorage target = storage;
        List<WaypointStorage.Snapshot> snapshot = waypoints.values().stream().map(WaypointStorage.Snapshot::of).toList();
        Scheduling.runAsync(() -> {
            try {
                target.save(snapshot);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save waypoints.yml: " + e.getMessage());
            }
        });
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return fallback;
        }
    }
}
