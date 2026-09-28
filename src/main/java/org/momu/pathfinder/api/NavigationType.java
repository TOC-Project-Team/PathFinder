package org.momu.pathfinder.api;

/**
 * The kind of target a navigation session is guiding a player towards.
 */
public enum NavigationType {
    /** Following another online player. */
    PLAYER,
    /** Heading to a saved waypoint (see {@link PathFinderAPI#getWaypoints()}). */
    WAYPOINT,
    /** Heading to an arbitrary location supplied by a plugin. */
    LOCATION,
    /** Heading to the nearest beacon block. */
    BEACON,
    /** Heading to the nearest stronghold / end portal frame. */
    STRONGHOLD
}
