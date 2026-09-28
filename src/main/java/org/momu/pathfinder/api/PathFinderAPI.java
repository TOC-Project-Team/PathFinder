package org.momu.pathfinder.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API for other plugins to control PathFinder.
 * <p>
 * Obtain an instance with {@link PathFinderProvider#get()} or through Bukkit's
 * {@link org.bukkit.plugin.ServicesManager}. All methods must be called from the main server thread.
 * <p>
 * Navigation lifecycle can be observed with the events in {@link org.momu.pathfinder.api.event}.
 */
public interface PathFinderAPI {

    /**
     * Version of this API. Incremented when methods are added; existing methods are kept compatible.
     */
    int API_VERSION = 1;

    // ---------------------------------------------------------------- navigation

    /**
     * Guides a player to an arbitrary location with particles and an action bar.
     * Any navigation the player already has is replaced.
     *
     * @param player      the player to guide
     * @param target      the destination, must be in the player's world
     * @param displayName name shown in the action bar, or {@code null}
     */
    NavigationResult navigateToLocation(Player player, Location target, String displayName);

    /**
     * Same as {@link #navigateToLocation(Player, Location, String)} but optionally hides the
     * distance/direction action bar for this session (particles are still shown).
     */
    NavigationResult navigateToLocation(Player player, Location target, String displayName, boolean showActionBar);

    /**
     * Guides a player to a saved waypoint.
     *
     * @param waypointName waypoint name (case-insensitive)
     */
    NavigationResult navigateToWaypoint(Player player, String waypointName);

    /**
     * Same as {@link #navigateToWaypoint(Player, String)} but optionally hides the action bar.
     */
    NavigationResult navigateToWaypoint(Player player, String waypointName, boolean showActionBar);

    /**
     * Guides a player to another online player, following them as they move.
     * The target's location-privacy setting is honoured unless the navigating player has {@code toc.admin}.
     */
    NavigationResult navigateToPlayer(Player player, Player target);

    /**
     * Stops the player's current navigation.
     *
     * @return {@code true} if the player was navigating
     */
    boolean stopNavigation(UUID playerId);

    /**
     * @see #stopNavigation(UUID)
     */
    boolean stopNavigation(Player player);

    boolean isNavigating(UUID playerId);

    /**
     * @return a snapshot of the player's current navigation, or empty if they are not navigating
     */
    Optional<NavigationSession> getSession(UUID playerId);

    /**
     * @return snapshots of every active navigation
     */
    Collection<NavigationSession> getActiveSessions();

    // ---------------------------------------------------------------- global settings

    /**
     * @return whether navigation is globally enabled (admins can always navigate)
     */
    boolean isNavigationEnabled();

    /**
     * Globally enables or disables navigation. Disabling stops every navigation of players
     * without {@code toc.admin}. The setting is persisted.
     */
    void setNavigationEnabled(boolean enabled);

    /**
     * @return whether the player has hidden their location from player navigation
     */
    boolean isLocationHidden(UUID playerId);

    /**
     * Hides or reveals a player's location from player navigation. The setting is persisted.
     */
    void setLocationHidden(UUID playerId, boolean hidden);

    // ---------------------------------------------------------------- waypoints

    /**
     * Creates a waypoint.
     *
     * @param name     1-32 characters, unique (case-insensitive)
     * @param location location with a loaded world and a Y inside the world's build height
     * @return {@code true} if created, {@code false} if the name is invalid/taken or the location is invalid
     */
    boolean createWaypoint(String name, Location location);

    /**
     * @return {@code true} if a waypoint was removed
     */
    boolean removeWaypoint(String name);

    /**
     * @return {@code true} if renamed, {@code false} if the old name is missing or the new name is invalid/taken
     */
    boolean renameWaypoint(String oldName, String newName);

    /**
     * Moves an existing waypoint to a new location (world included).
     *
     * @return {@code true} if moved, {@code false} if the waypoint is missing or the location is invalid
     */
    boolean moveWaypoint(String name, Location location);

    Optional<WaypointSnapshot> getWaypoint(String name);

    /**
     * @return every waypoint, sorted by name
     */
    List<WaypointSnapshot> getWaypoints();

    /**
     * @return every waypoint in the given world, sorted by name
     */
    List<WaypointSnapshot> getWaypoints(World world);
}
