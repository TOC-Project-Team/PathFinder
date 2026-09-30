package org.momu.pathfinder.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.momu.pathfinder.bootstrap.PathFinderPlugin;
import org.momu.pathfinder.command.nav.NavCommand;
import org.momu.pathfinder.config.LanguageManager;
import org.momu.pathfinder.config.Messages;
import org.momu.pathfinder.gui.MenuManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * {@code /toc <reload|cd|status|lang|admin|nav>}.
 */
public class MainCommand implements CommandExecutor, TabCompleter {
    private static final String SEPARATOR = "═══════════════════════════════════";

    private final PathFinderPlugin plugin;

    public MainCommand(PathFinderPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "cd" -> openNavigationMenu(sender);
            case "status" -> showStatus(sender);
            case "lang" -> LanguageCommand.execute(plugin, sender, args);
            case "admin" -> openAdminMenu(sender);
            case "nav" -> {
                try {
                    NavCommand.execute(sender, args);
                } catch (Exception e) {
                    red(sender, Messages.get(sender, "messages.nav-error", e.getMessage()));
                }
            }
            default -> red(sender, Messages.get(sender, "messages.unknown-command", label));
        }
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("--- PathFinder v" + plugin.getPluginMeta().getVersion() + " ---",
                NamedTextColor.GOLD));
        if (sender.hasPermission("toc.cd")) {
            helpLine(sender, label, "cd", "messages.cd");
        }
        helpLine(sender, label, "status", "messages.status");
        helpLine(sender, label, "lang <language>", "messages.lang");
        if (sender.hasPermission("toc.admin")) {
            helpLine(sender, label, "reload", "messages.reload");
            helpLine(sender, label, "admin", "messages.admin");
        }
    }

    private static void helpLine(CommandSender sender, String label, String usage, String descriptionKey) {
        sender.sendMessage(Component.text("/" + label + " " + usage, NamedTextColor.AQUA)
                .append(Component.text(Messages.get(sender, descriptionKey), NamedTextColor.GRAY)));
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("toc.admin")) {
            return true;
        }
        red(sender, Messages.get(sender, "messages.no-permission"));
        return false;
    }

    private void reload(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        try {
            plugin.reloadConfigurations();
            sender.sendMessage(Component.text(Messages.get(sender, "messages.reload-success"), NamedTextColor.GREEN));
        } catch (Exception e) {
            red(sender, Messages.get(sender, "messages.reload-error"));
            plugin.getLogger().severe("Reload failed: " + e.getMessage());
        }
    }

    private void openNavigationMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            red(sender, Messages.get(sender, "messages.console-cd-not-available"));
            gray(sender, "messages.console-cd-gui-required", "messages.console-cd-alternative");
            return;
        }
        if (!sender.hasPermission("toc.cd")) {
            red(sender, Messages.get(sender, "messages.no-permission"));
            return;
        }
        MenuManager.getInstance().openNavigationMenu(player);
    }

    private void openAdminMenu(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            red(sender, Messages.get(sender, "messages.console-admin-not-available"));
            gray(sender, "messages.console-admin-gui-required", "messages.console-admin-commands",
                    "messages.console-admin-reload", "messages.console-admin-status");
            return;
        }
        if (!requireAdmin(sender)) {
            return;
        }
        MenuManager.getInstance().openAdminMenu(player);
    }

    private void showStatus(CommandSender sender) {
        if (!requireAdmin(sender)) {
            return;
        }
        sender.sendMessage(Component.text("", NamedTextColor.WHITE));
        sender.sendMessage(Component.text(SEPARATOR, NamedTextColor.AQUA));
        sender.sendMessage(Component.text(Messages.get(sender, "messages.status-report-title"), NamedTextColor.GOLD));
        sender.sendMessage(Component.text(SEPARATOR, NamedTextColor.AQUA));
        white(sender, Messages.get(sender, "messages.status-plugin-version", plugin.getPluginMeta().getVersion()));
        white(sender, Messages.get(sender, "messages.status-players-online",
                Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers()));
        white(sender, Messages.get(sender, "messages.status-server", Bukkit.getVersion()));
        sender.sendMessage(Component.text(SEPARATOR, NamedTextColor.AQUA));
        sender.sendMessage(Component.text("", NamedTextColor.WHITE));
    }

    private static void red(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    private static void white(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.WHITE));
    }

    private static void gray(CommandSender sender, String... keys) {
        for (String key : keys) {
            sender.sendMessage(Component.text(Messages.get(sender, key), NamedTextColor.GRAY));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                                      String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("status", "lang"));
            if (sender.hasPermission("toc.cd")) {
                options.add("cd");
            }
            if (sender.hasPermission("toc.admin")) {
                options.add("reload");
                options.add("admin");
            }
            options.add("nav");
            return matching(options, args[0]);
        }
        if (args[0].equalsIgnoreCase("nav")) {
            return matching(navSubcommands(sender), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("lang")) {
            List<String> languages = new ArrayList<>(Arrays.asList(LanguageManager.getInstance().getAvailableLanguages()));
            languages.add("reset");
            return matching(languages, args[1]);
        }
        return new ArrayList<>();
    }

    private static List<String> navSubcommands(CommandSender sender) {
        boolean console = !(sender instanceof Player);
        boolean wildcard = sender.hasPermission("toc.nav.*");
        boolean manager = wildcard || sender.hasPermission("toc.admin") || console || ((Player) sender).isOp();
        List<String> options = new ArrayList<>();
        for (String action : List.of("add", "remove", "rename", "set", "start")) {
            if (manager || sender.hasPermission("toc.nav." + action)) {
                options.add(action);
            }
        }
        for (String action : List.of("go", "stop", "list")) {
            if (sender.hasPermission("toc.nav." + action) || wildcard || console) {
                options.add(action);
            }
        }
        if (sender.hasPermission("toc.view") || console) {
            options.add("view");
        }
        return options;
    }

    private static List<String> matching(List<String> options, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
