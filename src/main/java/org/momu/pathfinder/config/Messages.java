package org.momu.pathfinder.config;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Shortcut for looking up translated messages in the language of whoever will read them.
 * Console senders and {@code null} get the server's default language.
 */
public final class Messages {
    private Messages() {
    }

    public static String get(CommandSender reader, String key, Object... args) {
        Player player = reader instanceof Player p ? p : null;
        LanguageManager language = LanguageManager.getInstance();
        // Without arguments the raw text is returned; formatting it would still touch quotes and percent signs.
        return args.length == 0 ? language.getString(player, key) : language.getString(player, key, args);
    }
}
