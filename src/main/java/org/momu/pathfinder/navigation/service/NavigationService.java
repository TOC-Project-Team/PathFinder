package org.momu.pathfinder.navigation.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.momu.pathfinder.api.NavigationType;
import org.momu.pathfinder.bootstrap.PathFinderPlugin;
import org.momu.pathfinder.navigation.runtime.PathFinding;
import org.momu.pathfinder.presentation.listener.MasterListener;
import org.momu.pathfinder.navigation.state.PlayerTracker;

public final class NavigationService {
    private NavigationService() {}
    private static class Holder {
        private static final NavigationService INSTANCE = new NavigationService();
    }
    public static NavigationService getInstance() { return Holder.INSTANCE; }

    public boolean startForPlayer(Player player, Location target, String displayName) {
        return startForPlayer(player, target, displayName, NavigationType.WAYPOINT);
    }

    /**
     * Starts a fixed-location navigation, replacing any current one.
     *
     * @return {@code false} if navigation is disabled for the player or a listener cancelled the start event
     */
    public boolean startForPlayer(Player player, Location target, String displayName, NavigationType type) {
        if (player == null || target == null) return false;
        if (!PlayerTracker.getInstance().setWaypointNavigation(player.getUniqueId(), target, displayName, type)) {
            return false;
        }
        if (!MasterListener.getGuiManager().isParticleFeatureEnabled(player.getUniqueId())) {
            MasterListener.getGuiManager().toggleParticleFeature(player.getUniqueId());
        }
        Bukkit.getScheduler().runTask(PathFinderPlugin.getInstance(), () -> {
            PathFinding.startPathfinding(player);
        });
        return true;
    }
}
