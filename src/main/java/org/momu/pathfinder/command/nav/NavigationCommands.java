package org.momu.pathfinder.command.nav;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.momu.pathfinder.api.NavigationType;
import org.momu.pathfinder.api.event.PathFinderNavigationStopEvent.StopReason;
import org.momu.pathfinder.config.Messages;
import org.momu.pathfinder.navigation.NavigationService;
import org.momu.pathfinder.navigation.session.ActiveNavigation;
import org.momu.pathfinder.navigation.session.NavigationTracker;
import org.momu.pathfinder.waypoint.model.Waypoint;
import org.momu.pathfinder.waypoint.service.WaypointService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.momu.pathfinder.command.nav.NavCommand.*;

/**
 * Navigation control: {@code start}, {@code go}, {@code stop} and {@code view}.
 */
final class NavigationCommands {
    private NavigationCommands() {
    }

    /** {@code start <player> <waypoint> [-nobar]}: sends another player to a waypoint. */
    static void start(CommandSender sender, List<String> tokens) {
        requirePermission(sender, "toc.nav.start");
        if (tokens.size() < 3) {
            info(sender, Messages.get(sender, "messages.nav-usage-start"));
            return;
        }
        Player target = requireOnlinePlayer(sender, tokens.get(1));
        Waypoint waypoint = requireWaypoint(sender, target, tokens.get(2));
        boolean hideActionBar = tokens.subList(3, tokens.size()).stream().anyMatch("-nobar"::equalsIgnoreCase);
        startNavigation(sender, target, waypoint);
        if (hideActionBar) {
            NavigationTracker.getInstance().suppressActionBar(target.getUniqueId());
        }
        ok(sender, Messages.get(sender, "messages.nav-start-ok", waypoint.getName()));
    }

    /** {@code go <waypoint>}: navigates the sender to a waypoint. */
    static void go(CommandSender sender, List<String> tokens) {
        if (!(sender instanceof Player player)) {
            throw failure(null, "messages.player-usage");
        }
        requirePermission(player, "toc.nav.go");
        if (tokens.size() < 2) {
            info(sender, Messages.get(sender, "messages.nav-usage-go"));
            return;
        }
        Waypoint waypoint = requireWaypoint(player, player, tokens.get(1));
        startNavigation(player, player, waypoint);
        ok(sender, Messages.get(player, "messages.nav-go-started", waypoint.getName()));
    }

    /** Looks up a waypoint in {@code navigator}'s world. */
    private static Waypoint requireWaypoint(CommandSender sender, Player navigator, String name) {
        Waypoint waypoint = WaypointService.getInstance().get(name);
        if (waypoint == null) {
            throw failure(sender, "messages.nav-missing");
        }
        if (!navigator.getWorld().getName().equals(waypoint.getWorld())) {
            throw failure(sender, "messages.target-dimension");
        }
        return waypoint;
    }

    private static void startNavigation(CommandSender sender, Player navigator, Waypoint waypoint) {
        if (!NavigationTracker.getInstance().canUseNavigation(navigator.getUniqueId())) {
            throw failure(sender, "messages.global-navigation-disabled");
        }
        if (!NavigationService.getInstance().navigateToLocation(navigator, waypoint.toLocation(), waypoint.getName(),
                NavigationType.WAYPOINT)) {
            throw failure(sender, "messages.nav-start-cancelled");
        }
    }

    /**
     * <pre>
     * stop            stops the sender's own navigation (toc.nav.stop)
     * stop &lt;player&gt;   stops someone else's (toc.nav.stop.other)
     * </pre>
     */
    static void stop(CommandSender sender, List<String> tokens) {
        if (tokens.size() >= 2) {
            stopOther(sender, tokens.get(1));
            return;
        }
        if (!(sender instanceof Player player)) {
            throw failure(null, "messages.player-usage");
        }
        requirePermission(player, "toc.nav.stop");
        NavigationTracker.getInstance().stopNavigation(player.getUniqueId(), StopReason.CANCELLED);
        ok(sender, Messages.get(player, "messages.nav-stop-ok"));
    }

    private static void stopOther(CommandSender sender, String targetName) {
        if (sender instanceof Player player
                && !player.hasPermission("toc.nav.stop.other") && !player.hasPermission("toc.nav.*")) {
            throw failure(player, "messages.no-permission");
        }
        Player target = requireOnlinePlayer(sender, targetName);
        NavigationTracker tracker = NavigationTracker.getInstance();
        ActiveNavigation navigation = tracker.getActive(target.getUniqueId());
        if (navigation == null) {
            sender.sendMessage("§e" + Messages.get(sender, "messages.nav-stop-other-none", target.getName()));
            return;
        }
        String destination = describeDestination(sender, navigation, false);
        tracker.stopNavigation(target.getUniqueId(), StopReason.CANCELLED);
        sender.sendMessage("§a" + Messages.get(sender, "messages.nav-stop-other-ok", target.getName(),
                destination == null ? Messages.get(null, "messages.unknown") : destination));
    }

    /** {@code view [--page=<n>]}: lists who is navigating where. */
    static void view(CommandSender sender, List<String> tokens) {
        if (sender instanceof Player player && !(player.hasPermission("toc.view") || player.isOp())) {
            throw failure(player, "messages.no-permission");
        }
        int page = Arguments.parsePage(tokens.size() >= 2 ? tokens.get(1) : null);

        NavigationTracker tracker = NavigationTracker.getInstance();
        List<String> rows = new ArrayList<>();
        for (UUID playerId : tracker.getNavigatingPlayers()) {
            Player navigator = Bukkit.getPlayer(playerId);
            ActiveNavigation navigation = tracker.getActive(playerId);
            if (navigator == null || navigation == null) {
                continue;
            }
            // Rows are written in the navigator's language, as before.
            String destination = describeDestination(navigator, navigation, true);
            if (destination != null) {
                rows.add(Messages.get(navigator, "messages.view-line", "§a" + navigator.getName(), "§b" + destination));
            }
        }

        if (rows.isEmpty()) {
            info(sender, Messages.get(sender, "messages.none"));
            return;
        }
        int totalPages = ChatPages.pageCount(rows.size());
        page = ChatPages.clamp(page, rows.size());
        sender.sendMessage("§7" + Messages.get(sender, "messages.view-header", page + "/" + Math.max(1, totalPages)));
        ChatPages.slice(rows, page).forEach(sender::sendMessage);
        ChatPages.sendNavigation(sender, "/toc nav view", page, totalPages);
    }

    /**
     * A short name for where a navigation leads, or {@code null} if unknown.
     *
     * @param savedWaypointsOnly for waypoint/location navigations, only name it if a saved waypoint has that name
     */
    private static String describeDestination(CommandSender reader, ActiveNavigation navigation,
                                              boolean savedWaypointsOnly) {
        return switch (navigation.type()) {
            case PLAYER -> {
                Player target = Bukkit.getPlayer(navigation.targetPlayer());
                yield target == null ? null : target.getName();
            }
            case WAYPOINT, LOCATION -> {
                String name = navigation.displayName();
                if (!savedWaypointsOnly || name == null) {
                    yield name;
                }
                Waypoint waypoint = WaypointService.getInstance().get(name);
                yield waypoint == null ? null : waypoint.getName();
            }
            case BEACON -> Messages.get(reader, "messages.beacon-block");
            case STRONGHOLD -> Messages.get(reader, "messages.stronghold-name");
        };
    }

    private static Player requireOnlinePlayer(CommandSender sender, String name) {
        Player player = Bukkit.getPlayer(name);
        if (player == null || !player.isOnline()) {
            throw failure(sender, "messages.unknown-player");
        }
        return player;
    }
}
