package org.momu.pathfinder.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.momu.pathfinder.bootstrap.PathFinderPlugin;
import org.momu.pathfinder.config.LanguageManager;
import org.momu.pathfinder.config.Messages;

/**
 * {@code /toc lang [language|reset]}. Players change their own language; the console changes the server
 * default.
 */
final class LanguageCommand {
    private LanguageCommand() {
    }

    static void execute(PathFinderPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            showCurrent(sender);
            return;
        }
        String language = args[1];
        if (sender instanceof Player player) {
            if (language.equalsIgnoreCase("reset")) {
                resetPlayerLanguage(player);
            } else {
                setPlayerLanguage(player, language);
            }
        } else if (language.equalsIgnoreCase("reset")) {
            sender.sendMessage(Component.text(Messages.get(sender, "messages.lang-console-cannot-reset"), NamedTextColor.RED));
        } else {
            setDefaultLanguage(plugin, sender, language);
        }
    }

    private static void showCurrent(CommandSender sender) {
        LanguageManager languages = LanguageManager.getInstance();
        if (sender instanceof Player player) {
            String current = languages.getPlayerLanguage(player.getUniqueId());
            String message = current != null
                    ? Messages.get(player, "messages.lang-current", current)
                    : Messages.get(player, "messages.lang-default", languages.getCurrentLanguage());
            sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(Messages.get(sender, "messages.lang-console-default",
                    languages.getCurrentLanguage()), NamedTextColor.GREEN));
        }

        String[] available = languages.getAvailableLanguages();
        if (available.length > 0) {
            sender.sendMessage(Component.text(Messages.get(sender, "messages.lang-available",
                    String.join(", ", available)), NamedTextColor.GRAY));
        }
        sender.sendMessage(Component.text(Messages.get(sender, "messages.lang-usage"), NamedTextColor.YELLOW));
    }

    private static void resetPlayerLanguage(Player player) {
        LanguageManager.getInstance().removePlayerLanguage(player.getUniqueId());
        if (!player.hasPermission("toc.lang")) {
            player.sendMessage(Component.text(Messages.get(player, "messages.no-permission"), NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text(Messages.get(player, "messages.lang-reset"), NamedTextColor.GREEN));
    }

    private static void setPlayerLanguage(Player player, String language) {
        LanguageManager languages = LanguageManager.getInstance();
        if (!player.hasPermission("toc.lang")) {
            // Players who lose the permission fall back to the server language.
            languages.removePlayerLanguage(player.getUniqueId());
            player.sendMessage(Component.text(Messages.get(player, "messages.no-permission"), NamedTextColor.RED));
            return;
        }
        if (languages.setPlayerLanguage(player.getUniqueId(), language)) {
            player.sendMessage(Component.text(languages.getStringByLanguage(language, "messages.lang-set", language),
                    NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text(Messages.get(player, "messages.lang-not-found", language),
                    NamedTextColor.RED));
        }
    }

    private static void setDefaultLanguage(PathFinderPlugin plugin, CommandSender sender, String language) {
        LanguageManager languages = LanguageManager.getInstance();
        if (!languages.isLanguageAvailable(language)) {
            sender.sendMessage(Component.text(Messages.get(sender, "messages.lang-not-found", language),
                    NamedTextColor.RED));
            return;
        }
        plugin.getConfig().set("language", language);
        plugin.saveConfig();
        languages.loadLanguage();

        sender.sendMessage(Component.text(Messages.get(sender, "messages.lang-default-set", language), NamedTextColor.GREEN));
        plugin.getLogger().info("Default language changed to: " + language);

        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN));
        for (String key : new String[] { "messages.c-command-cd", "messages.c-command-reload",
                "messages.c-command-status", "messages.c-command-admin" }) {
            sender.sendMessage(Component.text(Messages.get(sender, key), NamedTextColor.WHITE));
        }
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.GREEN));
    }
}
