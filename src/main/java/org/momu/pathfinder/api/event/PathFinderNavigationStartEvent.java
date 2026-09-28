package org.momu.pathfinder.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.momu.pathfinder.api.NavigationSession;

/**
 * Fired before a player starts navigating, whether started from the GUI, a command or the API.
 * Cancelling it prevents the navigation (the player's previous navigation, if any, keeps running).
 */
public class PathFinderNavigationStartEvent extends PathFinderNavigationEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    public PathFinderNavigationStartEvent(Player player, NavigationSession session) {
        super(player, session);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
