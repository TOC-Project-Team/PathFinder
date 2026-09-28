package org.momu.pathfinder.api;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Immutable snapshot of a player's current navigation.
 *
 * @param playerId       the navigating player
 * @param type           what kind of target is being navigated to
 * @param targetLocation the target location at the time the snapshot was taken; {@code null} when unknown
 *                       (for example a target player that just went offline)
 * @param targetPlayerId the target player for {@link NavigationType#PLAYER}, otherwise {@code null}
 * @param displayName    the name shown in the action bar, may be {@code null}
 */
public record NavigationSession(UUID playerId,
                                NavigationType type,
                                Location targetLocation,
                                UUID targetPlayerId,
                                String displayName) {

    @Override
    public Location targetLocation() {
        return targetLocation == null ? null : targetLocation.clone();
    }
}
