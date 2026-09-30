package org.momu.pathfinder.navigation.session;

import org.bukkit.Location;
import org.momu.pathfinder.api.NavigationType;

import java.util.UUID;

/**
 * What a player is currently navigating to.
 *
 * @param type         the kind of target
 * @param location     fixed target location; {@code null} for {@link NavigationType#PLAYER}
 * @param targetPlayer the followed player for {@link NavigationType#PLAYER}, otherwise {@code null}
 * @param displayName  name shown to the player for waypoints and plugin locations, may be {@code null}
 */
public record ActiveNavigation(NavigationType type, Location location, UUID targetPlayer, String displayName) {

    static ActiveNavigation toPlayer(UUID targetPlayer) {
        return new ActiveNavigation(NavigationType.PLAYER, null, targetPlayer, null);
    }

    static ActiveNavigation toLocation(NavigationType type, Location location, String displayName) {
        return new ActiveNavigation(type, location, null, displayName);
    }

    /** Waypoints and plugin supplied locations are handled the same way at runtime. */
    public boolean isFixedLocation() {
        return type == NavigationType.WAYPOINT || type == NavigationType.LOCATION;
    }
}
