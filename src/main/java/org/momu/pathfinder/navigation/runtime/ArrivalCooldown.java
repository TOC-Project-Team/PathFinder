package org.momu.pathfinder.navigation.runtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents the "you have arrived" message and sound from repeating when a player reaches several targets in
 * quick succession.
 */
final class ArrivalCooldown {
    private static final long COOLDOWN_MILLIS = 3000L;
    private static final long CLEANUP_DELAY_TICKS = 60L;

    private static final Map<UUID, Long> cooldownUntil = new ConcurrentHashMap<>();

    private ArrivalCooldown() {
    }

    /**
     * @return {@code true} if the player may be notified now; the cooldown then starts
     */
    static boolean tryAcquire(UUID playerId) {
        long now = System.currentTimeMillis();
        Long current = cooldownUntil.get(playerId);
        if (current != null && current > now) {
            return false;
        }
        long expiry = now + COOLDOWN_MILLIS;
        cooldownUntil.put(playerId, expiry);
        Scheduling.runLater(() -> cooldownUntil.remove(playerId, expiry), CLEANUP_DELAY_TICKS);
        return true;
    }
}
