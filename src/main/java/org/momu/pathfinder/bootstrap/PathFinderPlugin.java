package org.momu.pathfinder.bootstrap;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.momu.pathfinder.api.PathFinderAPI;
import org.momu.pathfinder.api.PathFinderProvider;
import org.momu.pathfinder.api.event.PathFinderNavigationStopEvent.StopReason;
import org.momu.pathfinder.command.MainCommand;
import org.momu.pathfinder.config.LanguageManager;
import org.momu.pathfinder.config.PathfinderConfig;
import org.momu.pathfinder.integration.PathFinderApiImpl;
import org.momu.pathfinder.listener.MenuListener;
import org.momu.pathfinder.listener.PlayerLifecycleListener;
import org.momu.pathfinder.navigation.runtime.NavigationTasks;
import org.momu.pathfinder.navigation.session.NavigationTracker;
import org.momu.pathfinder.waypoint.service.WaypointService;

import java.io.File;
import java.util.UUID;

/**
 * Plugin entry point: loads configuration and data, registers commands, listeners and the developer API, and
 * shuts everything down again.
 */
public final class PathFinderPlugin extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 34371;

    private static PathFinderPlugin instance;

    private ConfigFileWatcher configWatcher;
    private Metrics metrics;

    public static PathFinderPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        try {
            ensureDataFiles();
            loadConfiguration();
            registerCommandsAndListeners();
            registerApi();
            startMetrics();
            configWatcher = new ConfigFileWatcher(this);
            configWatcher.start();
            getLogger().info("PathFinder enabled successfully.");
        } catch (Exception e) {
            getLogger().severe("Failed to enable PathFinder: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (configWatcher != null) {
            configWatcher.stop();
        }
        stopMetrics();
        unregisterApi();
        try {
            NavigationTracker.getInstance().stopAllNavigations(StopReason.PLUGIN_DISABLED);
            NavigationTracker.getInstance().savePreferences(this);
        } catch (Exception e) {
            getLogger().warning("Failed to persist player tracker state: " + e.getMessage());
        }
        NavigationTasks.getInstance().cancelEverything();
        instance = null;
        getLogger().info("PathFinder shutdown complete");
    }

    private void ensureDataFiles() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Unable to create plugin data folder: " + getDataFolder());
        }
        saveDefaultConfig();
        if (!new File(getDataFolder(), "pathfinder.yml").exists()) {
            saveResource("pathfinder.yml", false);
        }
    }

    private void loadConfiguration() {
        LanguageManager.getInstance(this);
        reloadConfig();
        PathfinderConfig.load(this);
        WaypointService.getInstance().init(this);
        NavigationTracker.getInstance().loadPreferences(this);
    }

    /** Re-reads every configuration and data file. Used by {@code /toc reload} and the file watcher. */
    public void reloadConfigurations() {
        reloadConfig();
        LanguageManager.getInstance(this).reloadLanguage();
        PathfinderConfig.load(this);
        WaypointService.getInstance().init(this);
        NavigationTracker.getInstance().loadPreferences(this);
        getLogger().info(LanguageManager.getInstance().getString("messages.config-reload-complete"));
    }

    private void registerCommandsAndListeners() {
        PluginCommand toc = getCommand("toc");
        if (toc == null) {
            throw new IllegalStateException("Command 'toc' is not defined in plugin.yml");
        }
        MainCommand mainCommand = new MainCommand(this);
        toc.setExecutor(mainCommand);
        toc.setTabCompleter(mainCommand);

        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerLifecycleListener(), this);
    }

    private void registerApi() {
        PathFinderAPI api = new PathFinderApiImpl(this);
        PathFinderProvider.register(api);
        getServer().getServicesManager().register(PathFinderAPI.class, api, this, ServicePriority.Normal);
    }

    private void unregisterApi() {
        getServer().getServicesManager().unregisterAll(this);
        PathFinderProvider.unregister();
    }

    /** Operators and {@code toc.admin} holders ignore location privacy and the server-wide switch. */
    public boolean canBypassNavigationRestrictions(Player player) {
        return player != null && (player.isOp() || player.hasPermission("toc.admin"));
    }

    public boolean canBypassNavigationRestrictions(UUID playerId) {
        return canBypassNavigationRestrictions(Bukkit.getPlayer(playerId));
    }

    // ---------------------------------------------------------------------------------------------------------
    // bStats
    // ---------------------------------------------------------------------------------------------------------

    private void startMetrics() {
        if (!getConfig().getBoolean("metrics", true)) {
            return;
        }
        try {
            metrics = new Metrics(this, BSTATS_PLUGIN_ID);
            metrics.addCustomChart(new SimplePie("language",
                    () -> LanguageManager.getInstance().getCurrentLanguage()));
            metrics.addCustomChart(new SimplePie("waypoint_count",
                    () -> bucketWaypointCount(WaypointService.getInstance().list(null).size())));
            metrics.addCustomChart(new SimplePie("allow_navigation_to_invisible",
                    () -> String.valueOf(getConfig().getBoolean("allow_navigation_to_invisible", false))));
        } catch (Exception e) {
            getLogger().warning("Failed to start bStats metrics: " + e.getMessage());
        }
    }

    private void stopMetrics() {
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
    }

    private static String bucketWaypointCount(int count) {
        if (count == 0) return "0";
        if (count <= 5) return "1-5";
        if (count <= 20) return "6-20";
        if (count <= 50) return "21-50";
        if (count <= 100) return "51-100";
        return "100+";
    }
}
