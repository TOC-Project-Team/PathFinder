package org.momu.pathfinder.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.momu.pathfinder.api.NavigationSession;

/**
 * Fired after a player's navigation has ended, for any reason.
 */
public class PathFinderNavigationStopEvent extends PathFinderNavigationEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final StopReason reason;

    public PathFinderNavigationStopEvent(Player player, NavigationSession session, StopReason reason) {
        super(player, session);
        this.reason = reason;
    }

    public StopReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum StopReason {
        /** The player reached the target. */
        ARRIVED,
        /** The player (or an admin) stopped it through the GUI or a command. */
        CANCELLED,
        /** A plugin stopped it through {@link org.momu.pathfinder.api.PathFinderAPI#stopNavigation}. */
        API,
        /** A new navigation replaced this one. */
        REPLACED,
        /** The target became unreachable: target player offline/dead/hidden, beacon removed, etc. */
        TARGET_UNAVAILABLE,
        /** The navigating player died. */
        PLAYER_DIED,
        /** The navigating player left the server. */
        PLAYER_QUIT,
        /** The navigating player changed world, or their world was unloaded. */
        WORLD_CHANGED,
        /** The navigating player switched to spectator mode. */
        GAME_MODE_CHANGED,
        /** Navigation was globally disabled. */
        NAVIGATION_DISABLED,
        /** PathFinder is shutting down. */
        PLUGIN_DISABLED,
        /** Any other reason. */
        OTHER
    }
}
