package org.momu.pathfinder.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Settings from {@code pathfinder.yml}. See that file for what each value does. Missing keys are added from
 * the bundled default on load; the defaults below only apply if the file cannot provide a value.
 */
public final class PathfinderConfig {
    // Search limits
    public static int MAX_SEARCH_RADIUS = 3000;
    public static int MAX_ITERATIONS = 10000;

    // Display
    public static double PARTICLE_SPACING = 0.5;
    public static int MAX_PARTICLE_DISTANCE = 30;
    public static float PARTICLE_SIZE = 1.0f;
    public static int PATH_REFRESH_TICKS = 15;

    // Movement costs
    public static double DIAGONAL_COST = 4.0;
    public static double STRAIGHT_COST = 2.0;
    public static double RIGHT_ANGLE_TURN_COST = 1.5;
    public static double DIAGONAL_TURN_COST = 0.5;
    public static double BREAK_BLOCK_COST = 100.0;
    /** Not listed in the bundled pathfinder.yml, but read if present. */
    public static double WATER_COST = 10.0;
    public static double DOOR_COST = 0.0;
    public static double TRAPDOOR_COST = 0.0;
    public static double SCAFFOLDING_COST = 0.0;
    public static double JUMP_COST = 0.0;
    public static double VERTICAL_COST = 1.0;
    public static double FALL_COST = 1.0;
    public static double BLOCK_JUMP_COST = 1.0;

    // Movement limits
    public static int MAX_BLOCK_JUMP_DISTANCE = 3;
    public static int MAX_SAFE_FALL_HEIGHT = 3;

    private static final String FILE_NAME = "pathfinder.yml";

    private PathfinderConfig() {
    }

    public static void load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        addMissingDefaults(plugin, config, file);

        MAX_SEARCH_RADIUS = config.getInt("max_search_radius", 3000);
        MAX_ITERATIONS = config.getInt("max_iterations", 10000);

        PARTICLE_SPACING = config.getDouble("particle_spacing", 0.5);
        MAX_PARTICLE_DISTANCE = config.getInt("max_particle_distance", 30);
        PARTICLE_SIZE = (float) config.getDouble("particle_size", 1.0);
        PATH_REFRESH_TICKS = config.getInt("path_refresh_ticks", 15);

        DIAGONAL_COST = config.getDouble("diagonal_cost", 4.0);
        STRAIGHT_COST = config.getDouble("straight_cost", 2.0);
        RIGHT_ANGLE_TURN_COST = config.getDouble("right_angle_turn_cost", 1.5);
        DIAGONAL_TURN_COST = config.getDouble("diagonal_turn_cost", 0.5);
        BREAK_BLOCK_COST = config.getDouble("break_block_cost", 100.0);
        WATER_COST = config.getDouble("water_cost", 10.0);
        DOOR_COST = config.getDouble("door_cost", 0.0);
        TRAPDOOR_COST = config.getDouble("trapdoor_cost", 0.0);
        SCAFFOLDING_COST = config.getDouble("scaffolding_cost", 0.0);
        JUMP_COST = config.getDouble("jump_cost", 0.0);
        VERTICAL_COST = config.getDouble("vertical_cost", 1.0);
        FALL_COST = config.getDouble("fall_cost", 1.0);
        BLOCK_JUMP_COST = config.getDouble("block_jump_cost", 1.0);

        MAX_BLOCK_JUMP_DISTANCE = config.getInt("max_block_jump_distance", 3);
        MAX_SAFE_FALL_HEIGHT = config.getInt("max_safe_fall_height", 3);
    }

    /** Copies keys that exist in the bundled pathfinder.yml but not in the server's copy. */
    private static void addMissingDefaults(JavaPlugin plugin, FileConfiguration config, File file) {
        try (InputStream bundled = plugin.getResource(FILE_NAME)) {
            if (bundled == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(bundled, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                config.save(file);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not add missing defaults to " + FILE_NAME + ": " + e.getMessage());
        }
    }
}
