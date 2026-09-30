package org.momu.pathfinder.command.nav;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.momu.pathfinder.config.Messages;

import java.util.List;
import java.util.Locale;

/**
 * {@code /toc nav <add|remove|rename|set|start|go|stop|list|view>}. Waypoint management lives in
 * {@link WaypointCommands}, navigation control in {@link NavigationCommands}.
 */
public final class NavCommand {
    private NavCommand() {
    }

    /** @param args the full {@code /toc} arguments, {@code args[0]} being {@code nav} */
    public static void execute(CommandSender sender, String[] args) {
        List<String> tokens = Arguments.tokenize(args, 1);
        if (tokens.isEmpty()) {
            info(sender, Messages.get(sender, "messages.nav-usage"));
            return;
        }
        String action = tokens.get(0).toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "add" -> WaypointCommands.add(sender, tokens);
                case "remove" -> WaypointCommands.remove(sender, tokens);
                case "rename" -> WaypointCommands.rename(sender, tokens);
                case "set" -> WaypointCommands.set(sender, tokens);
                case "list" -> WaypointCommands.list(sender, tokens);
                case "start" -> NavigationCommands.start(sender, tokens);
                case "go" -> NavigationCommands.go(sender, tokens);
                case "stop" -> NavigationCommands.stop(sender, tokens);
                case "view" -> NavigationCommands.view(sender, tokens);
                default -> {
                    error(sender, Messages.get(sender, "messages.nav-unknown-op", action));
                    info(sender, Messages.get(sender, "messages.nav-usage"));
                }
            }
        } catch (CommandFailure failure) {
            error(sender, failure.getMessage());
        } catch (Exception e) {
            error(sender, Messages.get(sender, "messages.nav-error", e.getMessage()));
        }
    }

    /**
     * Players need the given node, {@code toc.nav.*} or {@code toc.admin}; the console may do everything.
     */
    static void requirePermission(CommandSender sender, String permission) {
        if (!(sender instanceof Player player)) {
            return;
        }
        if (player.hasPermission(permission) || player.hasPermission("toc.nav.*") || player.hasPermission("toc.admin")) {
            return;
        }
        throw new CommandFailure(Messages.get(player, "messages.no-permission"));
    }

    /** Throws the translated message as a {@link CommandFailure}. */
    static CommandFailure failure(CommandSender sender, String key, Object... args) {
        return new CommandFailure(Messages.get(sender, key, args));
    }

    static void ok(CommandSender sender, String message) {
        sender.sendMessage("§a" + message);
    }

    static void info(CommandSender sender, String message) {
        sender.sendMessage("§7" + message);
    }

    static void error(CommandSender sender, String message) {
        sender.sendMessage("§c" + message);
    }
}
