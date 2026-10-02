package org.momu.pathfinder.integration;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.momu.pathfinder.api.NavigationResult;
import org.momu.pathfinder.api.NavigationSession;
import org.momu.pathfinder.api.NavigationType;
import org.momu.pathfinder.api.PathFinderAPI;
import org.momu.pathfinder.api.WaypointSnapshot;
import org.momu.pathfinder.api.event.PathFinderNavigationStopEvent.StopReason;
import org.momu.pathfinder.bootstrap.PathFinderPlugin;
import org.momu.pathfinder.navigation.NavigationService;
import org.momu.pathfinder.navigation.session.ActiveNavigation;
import org.momu.pathfinder.navigation.session.NavigationTracker;
import org.momu.pathfinder.waypoint.model.Waypoint;
import org.momu.pathfinder.waypoint.service.WaypointService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PathFinderApiImpl implements PathFinderAPI {
    private static final int MAX_WAYPOINT_NAME_LENGTH = 32;

    private final PathFinderPlugin plugin;

    public PathFinderApiImpl(PathFinderPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public NavigationResult navigateToLocation(Player player, Location target, String displayName) {
        return navigateToLocation(player, target, displayName, true);
    }

    @Override
    public NavigationResult navigateToLocation(Player player, Location target, String displayName,
                                               boolean showActionBar) {
        return startFixedNavigation(player, target, displayName, NavigationType.LOCATION, showActionBar);
    }

    @Override
    public NavigationResult navigateToWaypoint(Player player, String waypointName) {
        return navigateToWaypoint(player, waypointName, true);
    }

    @Override
    public NavigationResult navigateToWaypoint(Player player, String waypointName, boolean showActionBar) {
        if (waypointName == null) {
            return NavigationResult.WAYPOINT_NOT_FOUND;
        }
        Waypoint waypoint = WaypointService.getInstance().get(waypointName);
        if (waypoint == null) {
            return NavigationResult.WAYPOINT_NOT_FOUND;
        }
        return startFixedNavigation(player, waypoint.toLocation(), waypoint.getName(), NavigationType.WAYPOINT,
                showActionBar);
    }

    private NavigationResult startFixedNavigation(Player player, Location target, String displayName,
                                                  NavigationType type, boolean showActionBar) {
        if (player == null || !player.isOnline()) {
            return NavigationResult.PLAYER_OFFLINE;
        }
        if (target == null || target.getWorld() == null) {
            return NavigationResult.INVALID_TARGET;
        }
        if (!player.getWorld().equals(target.getWorld())) {
            return NavigationResult.WORLD_MISMATCH;
        }
        NavigationTracker tracker = NavigationTracker.getInstance();
        if (!tracker.canUseNavigation(player.getUniqueId())) {
            return NavigationResult.NAVIGATION_DISABLED;
        }
        if (!NavigationService.getInstance().navigateToLocation(player, target.clone(), displayName, type)) {
            return NavigationResult.CANCELLED;
        }
        if (!showActionBar) {
            tracker.suppressActionBar(player.getUniqueId());
        }
        return NavigationResult.SUCCESS;
    }

    @Override
    public NavigationResult navigateToPlayer(Player player, Player target) {
        if (player == null || !player.isOnline()) {
            return NavigationResult.PLAYER_OFFLINE;
        }
        if (target == null || !target.isOnline() || NavigationTracker.isDead(target)
                || target.getGameMode() == GameMode.SPECTATOR || target.getUniqueId().equals(player.getUniqueId())) {
            return NavigationResult.TARGET_UNAVAILABLE;
        }
        if (!player.getWorld().equals(target.getWorld())) {
            return NavigationResult.WORLD_MISMATCH;
        }
        NavigationTracker tracker = NavigationTracker.getInstance();
        if (!tracker.canUseNavigation(player.getUniqueId())) {
            return NavigationResult.NAVIGATION_DISABLED;
        }
        if (tracker.isHiddenByInvisibility(target)
                || (tracker.isLocationHidden(target.getUniqueId())
                    && !plugin.canBypassNavigationRestrictions(player))) {
            return NavigationResult.TARGET_UNAVAILABLE;
        }
        ActiveNavigation current = tracker.getActive(player.getUniqueId());
        if (current != null && target.getUniqueId().equals(current.targetPlayer())) {
            return NavigationResult.ALREADY_NAVIGATING;
        }
        if (!NavigationService.getInstance().navigateToPlayer(player, target)) {
            return NavigationResult.CANCELLED;
        }
        return NavigationResult.SUCCESS;
    }

    @Override
    public boolean stopNavigation(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        NavigationTracker tracker = NavigationTracker.getInstance();
        boolean wasNavigating = tracker.isNavigating(playerId);
        tracker.stopNavigation(playerId, StopReason.API);
        return wasNavigating;
    }

    @Override
    public boolean stopNavigation(Player player) {
        return player != null && stopNavigation(player.getUniqueId());
    }

    @Override
    public boolean isNavigating(UUID playerId) {
        return playerId != null && NavigationTracker.getInstance().isNavigating(playerId);
    }

    @Override
    public Optional<NavigationSession> getSession(UUID playerId) {
        return Optional.ofNullable(NavigationTracker.getInstance().getSession(playerId));
    }

    @Override
    public Collection<NavigationSession> getActiveSessions() {
        NavigationTracker tracker = NavigationTracker.getInstance();
        List<NavigationSession> sessions = new ArrayList<>();
        for (UUID playerId : tracker.getNavigatingPlayers()) {
            NavigationSession session = tracker.getSession(playerId);
            if (session != null) {
                sessions.add(session);
            }
        }
        return List.copyOf(sessions);
    }

    @Override
    public boolean isNavigationEnabled() {
        return NavigationTracker.getInstance().isNavigationEnabled();
    }

    @Override
    public void setNavigationEnabled(boolean enabled) {
        NavigationTracker.getInstance().setNavigationEnabled(enabled);
    }

    @Override
    public boolean isLocationHidden(UUID playerId) {
        return playerId != null && NavigationTracker.getInstance().isLocationHidden(playerId);
    }

    @Override
    public void setLocationHidden(UUID playerId, boolean hidden) {
        NavigationTracker.getInstance().setLocationHidden(playerId, hidden);
    }

    @Override
    public boolean createWaypoint(String name, Location location) {
        if (!isValidWaypointName(name) || location == null || location.getWorld() == null) {
            return false;
        }
        return WaypointService.getInstance().add(name.trim(), location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ());
    }

    @Override
    public boolean removeWaypoint(String name) {
        return name != null && WaypointService.getInstance().remove(name);
    }

    @Override
    public boolean renameWaypoint(String oldName, String newName) {
        if (oldName == null || !isValidWaypointName(newName)) {
            return false;
        }
        return WaypointService.getInstance().rename(oldName, newName.trim());
    }

    @Override
    public boolean moveWaypoint(String name, Location location) {
        if (name == null || location == null || location.getWorld() == null) {
            return false;
        }
        return WaypointService.getInstance().move(name, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ());
    }

    @Override
    public Optional<WaypointSnapshot> getWaypoint(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(WaypointService.getInstance().get(name)).map(PathFinderApiImpl::snapshot);
    }

    @Override
    public List<WaypointSnapshot> getWaypoints() {
        return WaypointService.getInstance().list(null).stream().map(PathFinderApiImpl::snapshot).toList();
    }

    @Override
    public List<WaypointSnapshot> getWaypoints(World world) {
        if (world == null) {
            return List.of();
        }
        return WaypointService.getInstance().list(world.getName()).stream().map(PathFinderApiImpl::snapshot).toList();
    }

    private static boolean isValidWaypointName(String name) {
        return name != null && !name.trim().isEmpty() && name.trim().length() <= MAX_WAYPOINT_NAME_LENGTH;
    }

    private static WaypointSnapshot snapshot(Waypoint waypoint) {
        return new WaypointSnapshot(waypoint.getName(), waypoint.getKey(), waypoint.getWorld(),
                waypoint.getX(), waypoint.getY(), waypoint.getZ(), waypoint.getCreatedAt(), waypoint.getUpdatedAt());
    }
}
