package org.momu.pathfinder.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.momu.pathfinder.api.NavigationSession;

/**
 * Fired when a player reaches their navigation target. A {@link PathFinderNavigationStopEvent} with
 * {@link PathFinderNavigationStopEvent.StopReason#ARRIVED} follows immediately after.
 */
public class PathFinderNavigationArriveEvent extends PathFinderNavigationEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public PathFinderNavigationArriveEvent(Player player, NavigationSession session) {
        super(player, session);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
