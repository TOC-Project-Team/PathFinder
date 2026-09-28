package org.momu.pathfinder.api.event;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.momu.pathfinder.api.NavigationSession;

/**
 * Base class for PathFinder navigation events.
 * <p>
 * Events are fired on the main thread in almost all cases. When PathFinder has to end a navigation from its
 * asynchronous path worker, the event is fired asynchronously ({@link #isAsynchronous()} returns {@code true}).
 */
public abstract class PathFinderNavigationEvent extends Event {
    private final Player player;
    private final NavigationSession session;

    protected PathFinderNavigationEvent(Player player, NavigationSession session) {
        super(!Bukkit.isPrimaryThread());
        this.player = player;
        this.session = session;
    }

    /**
     * @return the navigating player, may be {@code null} if they are no longer online
     */
    public Player getPlayer() {
        return player;
    }

    public NavigationSession getSession() {
        return session;
    }
}
