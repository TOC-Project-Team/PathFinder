package org.momu.pathfinder.bootstrap;

import org.momu.pathfinder.config.LanguageManager;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reloads the configuration when {@code config.yml}, {@code pathfinder.yml} or a {@code lang/*.yml} file is
 * edited on disk. Watches on a background thread and reloads on the main thread one second after the change.
 */
final class ConfigFileWatcher {
    /** Editors often write a file several times in a row; changes this close together trigger one reload. */
    private static final long DEBOUNCE_MILLIS = 1500L;
    private static final long RELOAD_DELAY_TICKS = 20L;

    private final PathFinderPlugin plugin;
    private final Path dataFolder;
    private final Map<WatchKey, Path> watchedDirectories = new HashMap<>();
    private final Map<String, Long> lastChangeMillis = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private WatchService watchService;
    private ExecutorService executor;

    ConfigFileWatcher(PathFinderPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder().toPath();
    }

    void start() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            watchedDirectories.clear();
            watch(dataFolder);
            Path langDirectory = dataFolder.resolve("lang");
            if (Files.isDirectory(langDirectory)) {
                watch(langDirectory);
            }

            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "PathFinder-ConfigReloadWatcher");
                thread.setDaemon(true);
                return thread;
            });
            running.set(true);
            executor.submit(this::watchLoop);
            plugin.getLogger().info(message("messages.watching-directory", dataFolder.toString()));
        } catch (Exception e) {
            plugin.getLogger().warning(message("messages.config-watcher-error", e.getMessage()));
        }
    }

    void stop() {
        try {
            running.set(false);
            if (watchService != null) {
                watchService.close();
                plugin.getLogger().info(LanguageManager.getInstance().getString("messages.watch-service-closed"));
            }
            watchedDirectories.clear();
            if (executor != null) {
                executor.shutdownNow();
            }
        } catch (Exception e) {
            plugin.getLogger().warning(message("messages.watch-service-close-error", e.getMessage()));
        }
    }

    private void watch(Path directory) throws IOException {
        WatchKey key = directory.register(watchService,
                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
        watchedDirectories.put(key, directory);
    }

    private void watchLoop() {
        while (running.get()) {
            try {
                WatchKey key = watchService.take();
                Path directory = watchedDirectories.get(key);
                if (directory != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() != StandardWatchEventKinds.OVERFLOW) {
                            onChange(relativeName(directory, (Path) event.context()));
                        }
                    }
                }
                if (!key.reset()) {
                    watchedDirectories.remove(key);
                    break;
                }
            } catch (InterruptedException e) {
                if (running.get()) {
                    plugin.getLogger().warning("Config file watcher interrupted: " + e.getMessage());
                }
                break;
            } catch (Exception e) {
                if (running.get()) {
                    plugin.getLogger().warning(message("messages.config-watcher-error", e.getMessage()));
                }
            }
        }
    }

    private void onChange(String fileName) {
        if (!isWatchedFile(fileName)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastChangeMillis.get(fileName);
        if (last != null && now - last < DEBOUNCE_MILLIS) {
            return;
        }
        lastChangeMillis.put(fileName, now);

        plugin.getLogger().info(message("messages.config-change-detected", fileName));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                plugin.getLogger().info(LanguageManager.getInstance().getString("messages.reloading-config"));
                plugin.reloadConfigurations();
                plugin.getLogger().info(LanguageManager.getInstance().getString("messages.config-reloaded"));
            } catch (Exception e) {
                plugin.getLogger().warning(message("messages.config-watcher-error", e.getMessage()));
            }
        }, RELOAD_DELAY_TICKS);
    }

    /** File name relative to the data folder, e.g. {@code config.yml} or {@code lang/en-US.yml}. */
    private String relativeName(Path directory, Path changed) {
        if (changed == null) {
            return "";
        }
        if (directory.equals(dataFolder)) {
            return changed.toString();
        }
        return dataFolder.relativize(directory.resolve(changed)).toString().replace('\\', '/');
    }

    private static boolean isWatchedFile(String fileName) {
        if (fileName.equals("pathfinder.yml") || fileName.equals("config.yml")) {
            return true;
        }
        return fileName.startsWith("lang/") && fileName.endsWith(".yml");
    }

    private static String message(String key, Object argument) {
        return LanguageManager.getInstance().getString(key, argument);
    }
}
