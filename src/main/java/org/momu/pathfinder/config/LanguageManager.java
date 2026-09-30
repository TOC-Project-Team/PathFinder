package org.momu.pathfinder.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Translated messages. The server default language comes from {@code config.yml}; players can pick their own
 * with {@code /toc lang}, which is remembered in {@code player-languages.yml}.
 *
 * <p>Messages are looked up by key ({@code messages.xyz}); a missing key is returned as-is. Arguments are
 * formatted with {@link MessageFormat} when the text contains {@code {0}}-style placeholders, otherwise with
 * {@link String#format}. Prefer {@link Messages#get} over calling this class directly.
 */
public class LanguageManager {
    private static final Pattern MESSAGE_FORMAT_PLACEHOLDER = Pattern.compile(".*\\{\\d+}.*");
    private static final Pattern LONE_APOSTROPHE = Pattern.compile("(?<!')'(?!')");
    private static final String PLAYER_LANGUAGES_FILE = "player-languages.yml";

    private static LanguageManager instance;

    private final JavaPlugin plugin;
    /** Messages in the server default language. */
    private FileConfiguration defaultMessages;
    private String defaultLanguage = LanguageFiles.FALLBACK;
    private final Map<UUID, String> playerLanguages = new HashMap<>();
    /** Loaded message files for languages players have chosen. */
    private final Map<String, FileConfiguration> loadedLanguages = new HashMap<>();
    private FileConfiguration playerLanguagesFile;

    private LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        LanguageFiles.copyBundled(plugin);
        loadLanguage();
        loadPlayerLanguages();
    }

    public static LanguageManager getInstance(JavaPlugin plugin) {
        if (instance == null) {
            instance = new LanguageManager(plugin);
        }
        return instance;
    }

    public static LanguageManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("LanguageManager has not been initialized yet!");
        }
        return instance;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Server default language
    // ---------------------------------------------------------------------------------------------------------

    /** Loads the default language named in config.yml, if it changed since the last load. */
    public void loadLanguage() {
        String configured = plugin.getConfig().getString("language", LanguageFiles.FALLBACK);
        if (configured.equals(defaultLanguage) && defaultMessages != null) {
            return;
        }
        boolean languageChanged = !configured.equals(defaultLanguage);
        defaultLanguage = configured;

        File file = LanguageFiles.file(plugin, defaultLanguage);
        if (!file.exists()) {
            file = createDefaultLanguageFile();
            if (file == null) {
                defaultMessages = new YamlConfiguration();
                plugin.getLogger().warning("Using an empty language configuration");
                return;
            }
        }
        defaultMessages = YamlConfiguration.loadConfiguration(file);
        updateDefaultLanguageFile(file, languageChanged);
        plugin.getLogger().info("Language file loaded: " + defaultLanguage);
    }

    /** Extracts the configured language from the jar, falling back to English. */
    private File createDefaultLanguageFile() {
        try {
            plugin.saveResource(LanguageFiles.resourcePath(defaultLanguage), false);
            plugin.getLogger().info("Created default language file: " + defaultLanguage + ".yml");
            return LanguageFiles.file(plugin, defaultLanguage);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not create language file " + defaultLanguage + ".yml: " + e.getMessage());
        }
        if (defaultLanguage.equals(LanguageFiles.FALLBACK)) {
            return null;
        }
        try {
            plugin.saveResource(LanguageFiles.resourcePath(LanguageFiles.FALLBACK), false);
            plugin.getLogger().info("Created fallback language file: " + LanguageFiles.FALLBACK + ".yml");
            defaultLanguage = LanguageFiles.FALLBACK;
            return LanguageFiles.file(plugin, LanguageFiles.FALLBACK);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not create fallback language file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Adds missing keys from the bundled file. When the server switched to this language, all bundled values
     * are applied so the file matches the shipped translation.
     */
    private void updateDefaultLanguageFile(File file, boolean languageChanged) {
        try {
            FileConfiguration bundled = LanguageFiles.loadBundled(plugin, defaultLanguage);
            if (bundled == null) {
                bundled = LanguageFiles.loadBundled(plugin, LanguageFiles.FALLBACK);
            }
            if (bundled == null) {
                plugin.getLogger().warning("No bundled language file to compare against");
                return;
            }
            boolean changed = LanguageFiles.migrateLegacyKeys(defaultMessages);
            changed |= LanguageFiles.merge(bundled, defaultMessages, "", languageChanged);
            if (languageChanged) {
                plugin.getLogger().info("Language switched, synchronizing all translation keys");
            }
            if (changed) {
                defaultMessages.save(file);
                plugin.getLogger().info("Language file updated with missing keys");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error while checking the language file: " + e.getMessage());
        }
    }

    /** Re-reads all language files, first adding keys that newer versions of PathFinder introduced. */
    public void reloadLanguage() {
        defaultMessages = null;
        loadedLanguages.clear();
        int synced = syncAllLanguageFiles();
        if (synced > 0) {
            plugin.getLogger().info("Language files updated during reload: " + synced + " files synchronized");
        }
        loadLanguage();
    }

    public String getCurrentLanguage() {
        return defaultLanguage;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Player languages
    // ---------------------------------------------------------------------------------------------------------

    private void loadPlayerLanguages() {
        File file = new File(plugin.getDataFolder(), PLAYER_LANGUAGES_FILE);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create " + PLAYER_LANGUAGES_FILE + ": " + e.getMessage());
                return;
            }
        }
        playerLanguagesFile = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = playerLanguagesFile.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String uuid : players.getKeys(false)) {
            try {
                String language = players.getString(uuid);
                if (language != null) {
                    playerLanguages.put(UUID.fromString(uuid), language);
                    loadLanguageFile(language);
                }
            } catch (IllegalArgumentException ignored) {
                // Skip malformed entries.
            }
        }
    }

    private void savePlayerLanguages() {
        if (playerLanguagesFile == null) {
            return;
        }
        playerLanguagesFile.set("players", null);
        for (Map.Entry<UUID, String> entry : playerLanguages.entrySet()) {
            playerLanguagesFile.set("players." + entry.getKey(), entry.getValue());
        }
        try {
            playerLanguagesFile.save(new File(plugin.getDataFolder(), PLAYER_LANGUAGES_FILE));
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save " + PLAYER_LANGUAGES_FILE + ": " + e.getMessage());
        }
    }

    private void loadLanguageFile(String language) {
        if (loadedLanguages.containsKey(language)) {
            return;
        }
        File file = LanguageFiles.file(plugin, language);
        if (!file.exists()) {
            try {
                plugin.saveResource(LanguageFiles.resourcePath(language), false);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to copy language file: " + language + ".yml");
                return;
            }
        }
        try {
            loadedLanguages.put(language, YamlConfiguration.loadConfiguration(file));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load language config: " + language);
        }
    }

    /** @return {@code false} if there is no such language file */
    public boolean setPlayerLanguage(UUID playerId, String language) {
        if (!LanguageFiles.file(plugin, language).exists()) {
            return false;
        }
        try {
            if (syncLanguageFile(language)) {
                loadedLanguages.remove(language);
            }
        } catch (Exception ignored) {
            // Use the file as it is.
        }
        loadLanguageFile(language);
        if (!loadedLanguages.containsKey(language)) {
            return false;
        }
        playerLanguages.put(playerId, language);
        savePlayerLanguages();
        return true;
    }

    /** The language the player picked, or {@code null} if they use the server default. */
    public String getPlayerLanguage(UUID playerId) {
        return playerLanguages.get(playerId);
    }

    public void removePlayerLanguage(UUID playerId) {
        playerLanguages.remove(playerId);
        savePlayerLanguages();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Lookup
    // ---------------------------------------------------------------------------------------------------------

    /** A message in the server default language. */
    public String getString(String key) {
        String value = defaultMessages.getString(key);
        return value != null ? value : key;
    }

    /** A message in the player's language, falling back to the server default. */
    public String getString(UUID playerId, String key) {
        String language = playerId == null ? null : playerLanguages.get(playerId);
        if (language == null) {
            return getString(key);
        }
        FileConfiguration messages = loadedLanguages.get(language);
        if (messages == null) {
            loadLanguageFile(language);
            messages = loadedLanguages.get(language);
            if (messages == null) {
                return getString(key);
            }
        }
        String value = messages.getString(key);
        if (value == null) {
            value = lookupAfterSync(language, key);
        }
        return value != null ? value : getString(key);
    }

    /** The key may be new in this version: add missing keys to the player's language file and try again. */
    private String lookupAfterSync(String language, String key) {
        try {
            if (syncLanguageFile(language)) {
                loadedLanguages.remove(language);
                loadLanguageFile(language);
                FileConfiguration reloaded = loadedLanguages.get(language);
                return reloaded == null ? null : reloaded.getString(key);
            }
        } catch (Exception ignored) {
            // Fall back to the default language.
        }
        return null;
    }

    public String getString(Player player, String key) {
        return player != null ? getString(player.getUniqueId(), key) : getString(key);
    }

    public String getString(String key, Object... args) {
        return format(getString(key), key, args);
    }

    public String getString(UUID playerId, String key, Object... args) {
        return format(getString(playerId, key), key, args);
    }

    public String getString(Player player, String key, Object... args) {
        return format(getString(player, key), key, args);
    }

    /** A message in a specific language, falling back to the server default. */
    public String getStringByLanguage(String language, String key, Object... args) {
        loadLanguageFile(language);
        FileConfiguration messages = loadedLanguages.get(language);
        if (messages == null) {
            plugin.getLogger().warning("Could not load language " + language + ", using the default language");
            return format(getString(key), key, args);
        }
        String pattern = messages.getString(key);
        return format(pattern != null ? pattern : getString(key), key, args);
    }

    private String format(String pattern, String key, Object... args) {
        String formatted = formatPattern(pattern, args);
        if (formatted == null) {
            plugin.getLogger().warning("Error formatting message for key: " + key);
            return pattern;
        }
        return formatted;
    }

    /**
     * Fills in a message's arguments.
     *
     * @return the formatted message, or {@code null} if the pattern does not fit the arguments
     */
    static String formatPattern(String pattern, Object... args) {
        if (MESSAGE_FORMAT_PLACEHOLDER.matcher(pattern).matches()) {
            try {
                return MessageFormat.format(escapeApostrophes(pattern), args);
            } catch (Exception ignored) {
                // Try printf-style below.
            }
        }
        try {
            return String.format(pattern, args);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * {@link MessageFormat} treats a single apostrophe as the start of quoted text, which would hide every
     * placeholder after "player {0}'s" or "l'End". Translations are plain text, so lone apostrophes are doubled
     * to keep them literal; an already doubled {@code ''} is left alone.
     */
    private static String escapeApostrophes(String pattern) {
        return LONE_APOSTROPHE.matcher(pattern).replaceAll("''");
    }

    // ---------------------------------------------------------------------------------------------------------
    // Available languages and file sync
    // ---------------------------------------------------------------------------------------------------------

    /** Every {@code lang/*.yml} in the data folder, including custom ones. */
    public String[] getAvailableLanguages() {
        File directory = new File(plugin.getDataFolder(), "lang");
        File[] files = directory.isDirectory() ? directory.listFiles((dir, name) -> name.endsWith(".yml")) : null;
        if (files == null) {
            return new String[0];
        }
        String[] languages = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            String name = files[i].getName();
            languages[i] = name.substring(0, name.length() - 4);
        }
        return languages;
    }

    public boolean isLanguageAvailable(String language) {
        return new File(plugin.getDataFolder(), "lang/" + language + ".yml").exists();
    }

    /**
     * Adds keys from the bundled version of a language to the server's copy.
     *
     * @return whether the file was created or changed
     */
    public boolean syncLanguageFile(String language) {
        try {
            FileConfiguration bundled = LanguageFiles.loadBundled(plugin, language);
            if (bundled == null) {
                return false;
            }
            File file = LanguageFiles.file(plugin, language);
            if (!file.exists()) {
                plugin.saveResource(LanguageFiles.resourcePath(language), false);
                return true;
            }
            FileConfiguration current = YamlConfiguration.loadConfiguration(file);
            boolean changed = LanguageFiles.migrateLegacyKeys(current);
            changed |= LanguageFiles.merge(bundled, current, "", false);
            if (!changed) {
                return false;
            }
            current.save(file);
            loadedLanguages.remove(language);
            loadLanguageFile(language);
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to sync language file: " + language);
            return false;
        }
    }

    /** @return how many bundled language files were changed */
    public int syncAllLanguageFiles() {
        int updated = 0;
        for (String language : LanguageFiles.BUNDLED) {
            if (syncLanguageFile(language)) {
                updated++;
            }
        }
        return updated;
    }
}
