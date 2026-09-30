package org.momu.pathfinder.command.nav;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.momu.pathfinder.config.Messages;
import org.momu.pathfinder.waypoint.model.Waypoint;
import org.momu.pathfinder.waypoint.service.WaypointService;

import java.util.List;
import java.util.Locale;

import static org.momu.pathfinder.command.nav.NavCommand.*;

/**
 * Waypoint management: {@code add}, {@code remove}, {@code rename}, {@code set} and {@code list}.
 */
final class WaypointCommands {
    private static final int MAX_NAME_LENGTH = 32;

    private WaypointCommands() {
    }

    /**
     * <pre>
     * add &lt;name&gt;                      at the player's position
     * add &lt;x&gt; &lt;y&gt; &lt;z&gt; [world]         named after its coordinates
     * add &lt;name&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; [world]
     * </pre>
     * The world defaults to the player's current world.
     */
    static void add(CommandSender sender, List<String> tokens) {
        requirePermission(sender, "toc.nav.add");
        if (tokens.size() == 2 && sender instanceof Player player) {
            addAtPlayer(player, tokens.get(1));
            return;
        }

        String name;
        Double x;
        Double y;
        Double z;
        String worldName;
        boolean coordinatesFirst = tokens.size() >= 4 && tokens.size() <= 5
                && Arguments.isNumber(tokens.get(1)) && Arguments.isNumber(tokens.get(2))
                && Arguments.isNumber(tokens.get(3));
        if (coordinatesFirst) {
            x = Arguments.parseDouble(tokens.get(1));
            y = Arguments.parseDouble(tokens.get(2));
            z = Arguments.parseDouble(tokens.get(3));
            worldName = tokens.size() >= 5 ? tokens.get(4) : playerWorld(sender);
            name = String.format("%s,%s,%s", Arguments.formatCoordinate(x), Arguments.formatCoordinate(y),
                    Arguments.formatCoordinate(z));
        } else if (tokens.size() >= 5) {
            name = tokens.get(1);
            x = Arguments.parseDouble(tokens.get(2));
            y = Arguments.parseDouble(tokens.get(3));
            z = Arguments.parseDouble(tokens.get(4));
            worldName = tokens.size() >= 6 ? tokens.get(5) : playerWorld(sender);
        } else {
            info(sender, Messages.get(sender, "messages.nav-usage-add"));
            return;
        }

        if (x == null || y == null || z == null) {
            throw failure(sender, "messages.nav-coord-number");
        }
        if (worldName == null) {
            throw failure(sender, "messages.nav-world-required");
        }
        World world = requireWorld(sender, worldName);
        requireValidY(sender, world, y);
        requireValidName(sender, name);
        save(sender, name, worldName, x, y, z);
    }

    private static void addAtPlayer(Player player, String name) {
        requireValidName(player, name);
        String worldName = player.getWorld().getName();
        World world = requireWorld(player, worldName);
        Location location = player.getLocation();
        requireValidY(player, world, location.getY());
        save(player, name, worldName, location.getX(), location.getY(), location.getZ());
    }

    private static void save(CommandSender sender, String name, String worldName, double x, double y, double z) {
        if (!WaypointService.getInstance().add(name, worldName, x, y, z)) {
            throw failure(sender, "messages.nav-add-fail");
        }
        ok(sender, Messages.get(sender, "messages.nav-add-ok", name, worldName, x, y, z));
    }

    private static String playerWorld(CommandSender sender) {
        return sender instanceof Player player ? player.getWorld().getName() : null;
    }

    private static void requireValidName(CommandSender sender, String name) {
        if (name.trim().isEmpty()) {
            throw failure(sender, "messages.nav-name-empty");
        }
        if (name.trim().length() > MAX_NAME_LENGTH) {
            throw failure(sender, "messages.nav-name-too-long");
        }
    }

    private static World requireWorld(CommandSender sender, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw failure(sender, "messages.nav-world-missing", worldName);
        }
        return world;
    }

    private static void requireValidY(CommandSender sender, World world, double y) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            throw failure(sender, "messages.nav-y-out-of-range", world.getMinHeight(), world.getMaxHeight() - 1);
        }
    }

    /** {@code remove <name>} */
    static void remove(CommandSender sender, List<String> tokens) {
        requirePermission(sender, "toc.nav.remove");
        if (tokens.size() < 2) {
            info(sender, Messages.get(sender, "messages.nav-usage-remove"));
            return;
        }
        if (!WaypointService.getInstance().remove(tokens.get(1))) {
            throw failure(sender, "messages.nav-missing");
        }
        ok(sender, Messages.get(sender, "messages.nav-remove-ok", tokens.get(1)));
    }

    /** {@code rename <old> <new>} */
    static void rename(CommandSender sender, List<String> tokens) {
        requirePermission(sender, "toc.nav.rename");
        if (tokens.size() < 3) {
            info(sender, Messages.get(sender, "messages.nav-usage-rename"));
            return;
        }
        String oldName = tokens.get(1);
        String newName = tokens.get(2);
        if (newName.trim().isEmpty() || newName.trim().length() > MAX_NAME_LENGTH) {
            throw failure(sender, "messages.nav-name-invalid");
        }
        if (!WaypointService.getInstance().rename(oldName, newName)) {
            throw failure(sender, "messages.nav-rename-fail");
        }
        ok(sender, Messages.get(sender, "messages.nav-rename-ok", oldName, newName));
    }

    /** {@code set <name> <x|y|z|world> <value>} */
    static void set(CommandSender sender, List<String> tokens) {
        requirePermission(sender, "toc.nav.set");
        if (tokens.size() < 4) {
            info(sender, Messages.get(sender, "messages.nav-usage-set"));
            return;
        }
        String name = tokens.get(1);
        String field = tokens.get(2).toLowerCase(Locale.ROOT);
        String value = tokens.get(3);
        switch (field) {
            case "x", "y", "z" -> {
                Double number = Arguments.parseDouble(value);
                if (number == null) {
                    throw failure(sender, "messages.nav-value-number");
                }
                Waypoint waypoint = WaypointService.getInstance().get(name);
                World world = waypoint == null ? null : Bukkit.getWorld(waypoint.getWorld());
                if (field.equals("y") && world != null) {
                    requireValidY(sender, world, number);
                }
            }
            case "world" -> requireWorld(sender, value);
            default -> throw failure(sender, "messages.nav-field-invalid");
        }
        if (!WaypointService.getInstance().setField(name, field, value)) {
            throw failure(sender, "messages.nav-set-fail");
        }
        ok(sender, Messages.get(sender, "messages.nav-set-ok", name, field, value));
    }

    /** {@code list [--world=<world>] [--page=<n>]} */
    static void list(CommandSender sender, List<String> tokens) {
        if (sender instanceof Player) {
            requirePermission(sender, "toc.nav.list");
        }
        String worldFilter = null;
        int page;
        if (tokens.size() >= 2 && tokens.get(1).startsWith("--world=")) {
            worldFilter = tokens.get(1).substring("--world=".length());
            page = Arguments.parsePage(tokens.size() >= 3 ? tokens.get(2) : null);
        } else {
            page = Arguments.parsePage(tokens.size() >= 2 ? tokens.get(1) : null);
        }

        List<Waypoint> waypoints = WaypointService.getInstance().list(worldFilter);
        if (waypoints.isEmpty()) {
            info(sender, Messages.get(sender, "messages.nav-list-empty"));
            return;
        }
        int totalPages = ChatPages.pageCount(waypoints.size());
        page = ChatPages.clamp(page, waypoints.size());

        String header = Messages.get(sender, "messages.nav-list-header",
                (worldFilter == null ? "*" : worldFilter) + "  (" + page + "/" + Math.max(1, totalPages) + ")");
        info(sender, removeAtSigns(header));
        for (Waypoint waypoint : ChatPages.slice(waypoints, page)) {
            String line = Messages.get(sender, "messages.nav-list-item", "§a" + waypoint.getName(),
                    "§b" + waypoint.getWorld(), "§e" + (int) waypoint.getX(), "§e" + (int) waypoint.getY(),
                    "§e" + (int) waypoint.getZ());
            sender.sendMessage("§7" + removeAtSigns(line));
        }
        ChatPages.sendNavigation(sender, "/toc nav list" + (worldFilter != null ? " --world=" + worldFilter : ""),
                page, totalPages);
    }

    /** Older language files put an "@" before coordinates; drop it. */
    private static String removeAtSigns(String text) {
        return text.replace(" @§", " §").replace(" @", " ");
    }
}
