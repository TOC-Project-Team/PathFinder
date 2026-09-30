package org.momu.pathfinder.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageFormattingTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+}");

    @Test
    void apostrophesDoNotSwallowPlaceholders() {
        assertEquals("Stopped player Bob's navigation to Spawn",
                LanguageManager.formatPattern("Stopped player {0}'s navigation to {1}", "Bob", "Spawn"));
        assertEquals("Point d'itinéraire supprimé : home",
                LanguageManager.formatPattern("Point d'itinéraire supprimé : {0}", "home"));
    }

    @Test
    void escapedApostrophesStillWork() {
        assertEquals("3 o'clock", LanguageManager.formatPattern("{0} o''clock", 3));
    }

    @Test
    void printfMessagesAreUnchanged() {
        assertEquals("It's 5", LanguageManager.formatPattern("It's %s", 5));
    }

    /** Every placeholder in every bundled translation gets filled in, and apostrophes survive. */
    @Test
    void allBundledTranslationsFormat() {
        List<String> problems = new ArrayList<>();
        for (String language : LanguageFiles.BUNDLED) {
            YamlConfiguration messages = YamlConfiguration.loadConfiguration(
                    new File("src/main/resources/lang/" + language + ".yml"));
            for (String key : messages.getKeys(true)) {
                String pattern = messages.getString(key);
                if (messages.isConfigurationSection(key) || pattern == null
                        || !PLACEHOLDER.matcher(pattern).find()) {
                    continue;
                }
                Object[] args = new Object[10];
                for (int i = 0; i < args.length; i++) {
                    args[i] = "ARG" + i;
                }
                String formatted = LanguageManager.formatPattern(pattern, args);
                long expectedApostrophes = pattern.replace("''", "'").chars().filter(c -> c == '\'').count();
                if (formatted == null || PLACEHOLDER.matcher(formatted).find()
                        || formatted.chars().filter(c -> c == '\'').count() != expectedApostrophes) {
                    problems.add(language + " " + key + " -> " + formatted);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
