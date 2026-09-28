package org.momu.pathfinder.api;

/**
 * Outcome of a navigation request made through {@link PathFinderAPI}.
 */
public enum NavigationResult {
    /** Navigation started. */
    SUCCESS,
    /** The navigating player is null or offline. */
    PLAYER_OFFLINE,
    /** The target location is null or its world is not loaded. */
    INVALID_TARGET,
    /** No waypoint exists with the given name. */
    WAYPOINT_NOT_FOUND,
    /** The target is in a different world from the navigating player. */
    WORLD_MISMATCH,
    /** The target player is offline, dead, in spectator mode, invisible or has hidden their location. */
    TARGET_UNAVAILABLE,
    /** The player is already navigating to this exact target player. */
    ALREADY_NAVIGATING,
    /** Navigation is globally disabled and the player cannot bypass it. */
    NAVIGATION_DISABLED,
    /** A listener cancelled the {@link org.momu.pathfinder.api.event.PathFinderNavigationStartEvent}. */
    CANCELLED;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
