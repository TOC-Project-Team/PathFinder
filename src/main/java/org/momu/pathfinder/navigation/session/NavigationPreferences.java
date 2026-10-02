package org.momu.pathfinder.navigation.session;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.momu.pathfinder.config.LanguageManager;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Settings that survive restarts, stored in {@code playerdata.yml}: which players hide their location from
 * others, and whether navigation is enabled server-wide.
 */
final class NavigationPreferences {
    private static final String FILE_NAME = "playerdata.yml";

    private final Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();
    private volatile boolean navigationEnabled = true;

    boolean isHidden(UUID playerId) {
        return hiddenPlayers.contains(playerId);
    }

    void setHidden(UUID playerId, boolean hidden) {
        if (hidden) {
            hiddenPlayers.add(playerId);
        } else {
            hiddenPlayers.remove(playerId);
        }
    }

    boolean isNavigationEnabled() {
        return navigationEnabled;
    }

    void setNavigationEnabled(boolean enabled) {
        navigationEnabled = enabled;
    }

    void load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String uuid : yaml.getStringList("hiddenPlayers")) {
            try {
                hiddenPlayers.add(UUID.fromString(uuid));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed entries.
            }
        }
        navigationEnabled = yaml.getBoolean("navigationEnabled", true);
    }

    synchronized void save(JavaPlugin plugin) {
        if (plugin == null) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("hiddenPlayers", hiddenPlayers.stream().map(UUID::toString).toList());
        yaml.set("navigationEnabled", navigationEnabled);
        try {
            yaml.save(new File(plugin.getDataFolder(), FILE_NAME));
        } catch (IOException e) {
            plugin.getLogger().warning(LanguageManager.getInstance()
                    .getString("messages.pathfinder-config-update-error", e.getMessage()));
        }
    }
}
