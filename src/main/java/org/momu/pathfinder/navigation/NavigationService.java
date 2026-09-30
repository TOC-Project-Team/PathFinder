package org.momu.pathfinder.navigation;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.momu.pathfinder.api.NavigationType;
import org.momu.pathfinder.navigation.runtime.GuidanceTask;
import org.momu.pathfinder.navigation.session.NavigationTracker;

/**
 * Starts navigations. Commands, menus, listeners and the developer API all go through here so that every
 * navigation is registered with {@link NavigationTracker} and then gets its {@link GuidanceTask}.
 *
 * <p>Each method returns {@code false} when navigation is disabled for the player, a listener cancelled
 * {@code PathFinderNavigationStartEvent}, or (for players) the target cannot be followed.
 */
public final class NavigationService {
    private static final NavigationService INSTANCE = new NavigationService();

    private NavigationService() {
    }

    public static NavigationService getInstance() {
        return INSTANCE;
    }

    public boolean navigateToPlayer(Player player, Player target) {
        if (!NavigationTracker.getInstance().startPlayerNavigation(player.getUniqueId(), target.getUniqueId())) {
            return false;
        }
        GuidanceTask.start(player);
        return true;
    }

    /** Navigates to a saved waypoint ({@link NavigationType#WAYPOINT}) or plugin location ({@link NavigationType#LOCATION}). */
    public boolean navigateToLocation(Player player, Location target, String displayName, NavigationType type) {
        if (player == null || target == null) {
            return false;
        }
        if (!NavigationTracker.getInstance().startLocationNavigation(player.getUniqueId(), target, displayName, type)) {
            return false;
        }
        GuidanceTask.start(player);
        return true;
    }

    public boolean navigateToStronghold(Player player, Location stronghold) {
        if (!NavigationTracker.getInstance().startStrongholdNavigation(player.getUniqueId(), stronghold)) {
            return false;
        }
        GuidanceTask.start(player);
        return true;
    }

    public boolean navigateToBeacon(Player player, Location beacon) {
        if (!NavigationTracker.getInstance().startBeaconNavigation(player.getUniqueId(), beacon)) {
            return false;
        }
        GuidanceTask.start(player);
        return true;
    }
}
