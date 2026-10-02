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
 * <p>
 * On Folia, events are fired on the thread that started, ended or arrived at the navigation: usually the
 * thread of the region that owns the navigating player, but a command from the console or another player, a
 * target player's death, or an API call can fire them on another region or the global region thread. Use the
 * player's {@link org.bukkit.entity.Entity#getScheduler() entity scheduler} to act on the player from a
 * listener.
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
