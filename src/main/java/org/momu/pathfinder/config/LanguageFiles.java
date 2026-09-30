package org.momu.pathfinder.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * The {@code lang/*.yml} files: copying the bundled ones into the data folder and bringing server copies up to
 * date with the bundled keys.
 */
final class LanguageFiles {
    static final List<String> BUNDLED = List.of("zh-CN", "zh-TW", "en-US", "ru-RU", "pt-PT", "fr-FR", "es-ES", "de-DE");
    static final String FALLBACK = "en-US";

    private LanguageFiles() {
    }

    static File directory(JavaPlugin plugin) {
        File directory = new File(plugin.getDataFolder(), "lang");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    static File file(JavaPlugin plugin, String language) {
        return new File(directory(plugin), language + ".yml");
    }

    static String resourcePath(String language) {
        return "lang/" + language + ".yml";
    }

    /** Copies every bundled language file that does not exist yet. */
    static void copyBundled(JavaPlugin plugin) {
        for (String language : BUNDLED) {
            if (!file(plugin, language).exists()) {
                try {
                    plugin.saveResource(resourcePath(language), false);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not create language file " + language + ".yml: " + e.getMessage());
                }
            }
        }
    }

    /** The bundled version of a language, or {@code null} if PathFinder does not ship it. */
    static FileConfiguration loadBundled(JavaPlugin plugin, String language) {
        InputStream stream = plugin.getResource(resourcePath(language));
        if (stream == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Adds keys from {@code source} that {@code target} lacks. With {@code overwrite}, values that differ from
     * {@code source} are replaced too.
     *
     * @return whether {@code target} changed
     */
    static boolean merge(ConfigurationSection source, FileConfiguration target, String path, boolean overwrite) {
        ConfigurationSection section = path.isEmpty() ? source : source.getConfigurationSection(path);
        if (section == null) {
            return false;
        }
        boolean changed = false;
        Set<String> keys = section.getKeys(false);
        for (String key : keys) {
            String keyPath = path.isEmpty() ? key : path + "." + key;
            if (source.isConfigurationSection(keyPath)) {
                if (!target.isConfigurationSection(keyPath)) {
                    target.createSection(keyPath);
                    changed = true;
                }
                changed |= merge(source, target, keyPath, overwrite);
            } else if (!target.contains(keyPath) || overwrite) {
                Object value = source.get(keyPath);
                if (value != null && !value.equals(target.get(keyPath))) {
                    target.set(keyPath, value);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Renames keys that were misspelled in older versions. */
    static boolean migrateLegacyKeys(FileConfiguration config) {
        String current = "messages.target-beacon-disappear";
        String legacy = "messages.target-beacon-dissappear";
        if (!config.contains(current) && config.contains(legacy)) {
            config.set(current, config.get(legacy));
            return true;
        }
        return false;
    }
}
