package org.momu.pathfinder.navigation.session;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.momu.pathfinder.api.NavigationSession;
import org.momu.pathfinder.api.NavigationType;
import org.momu.pathfinder.api.event.PathFinderNavigationStartEvent;
import org.momu.pathfinder.api.event.PathFinderNavigationStopEvent;
import org.momu.pathfinder.api.event.PathFinderNavigationStopEvent.StopReason;
import org.momu.pathfinder.bootstrap.PathFinderPlugin;
import org.momu.pathfinder.navigation.runtime.NavigationTasks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Source of truth for who is navigating where, plus the location-privacy and server-wide on/off settings.
 *
 * <p>Starting a navigation fires {@link PathFinderNavigationStartEvent} (which listeners may cancel) and
 * replaces any navigation the player already had. Stopping fires {@link PathFinderNavigationStopEvent} and
 * cancels the player's guidance task. This class only tracks state; {@code NavigationService} starts the
 * guidance itself.
 */
public final class NavigationTracker {
    private static final NavigationTracker INSTANCE = new NavigationTracker();

    private final Map<UUID, ActiveNavigation> active = new HashMap<>();
    private final Set<UUID> actionBarSuppressed = new HashSet<>();
    private final NavigationPreferences preferences = new NavigationPreferences();

    private NavigationTracker() {
    }

    public static NavigationTracker getInstance() {
        return INSTANCE;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Starting and stopping
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Starts following another player.
     *
     * @return {@code false} if navigation is disabled for the player, they already follow this target, the
     * target is invisible or hides their location, or a listener cancelled the start
     */
    public boolean startPlayerNavigation(UUID playerId, UUID targetId) {
        if (!canUseNavigation(playerId)) {
            return false;
        }
        ActiveNavigation current = active.get(playerId);
        if (current != null && targetId != null && targetId.equals(current.targetPlayer())) {
            return false;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (isHiddenByInvisibility(target)) {
            return false;
        }
        if (isLocationHidden(targetId) && !canBypassRestrictions(playerId)) {
            return false;
        }
        NavigationSession pending = new NavigationSession(playerId, NavigationType.PLAYER,
                target != null ? target.getLocation() : null, targetId, target != null ? target.getName() : null);
        if (!callStartEvent(playerId, pending)) {
            return false;
        }

        stopNavigation(playerId, StopReason.REPLACED);
        active.put(playerId, ActiveNavigation.toPlayer(targetId));

        Player navigator = Bukkit.getPlayer(playerId);
        if (target != null && navigator != null && target.isOnline() && shouldNotifyTarget(playerId, targetId)) {
            TargetNotifier.navigationStarted(target, navigator);
        }
        return true;
    }

    /**
     * Starts navigating to a stronghold. If the player is already navigating to one, only the target moves and
     * no events fire.
     */
    public boolean startStrongholdNavigation(UUID playerId, Location stronghold) {
        ActiveNavigation current = active.get(playerId);
        if (current != null && current.type() == NavigationType.STRONGHOLD) {
            if (!canUseNavigation(playerId)) {
                return false;
            }
            refineStrongholdTarget(playerId, stronghold);
            return true;
        }
        return startFixedTarget(playerId, NavigationType.STRONGHOLD, stronghold, null);
    }

    /** Starts navigating to a beacon. */
    public boolean startBeaconNavigation(UUID playerId, Location beacon) {
        return startFixedTarget(playerId, NavigationType.BEACON, beacon, null);
    }

    /**
     * Starts a fixed-location navigation. {@code type} is {@link NavigationType#WAYPOINT} for saved waypoints and
     * {@link NavigationType#LOCATION} for plugin supplied locations; both behave the same at runtime.
     */
    public boolean startLocationNavigation(UUID playerId, Location location, String name, NavigationType type) {
        NavigationType sessionType = type == NavigationType.LOCATION ? NavigationType.LOCATION : NavigationType.WAYPOINT;
        return startFixedTarget(playerId, sessionType, location, name);
    }

    private boolean startFixedTarget(UUID playerId, NavigationType type, Location location, String name) {
        if (!canUseNavigation(playerId)) {
            return false;
        }
        NavigationSession pending = new NavigationSession(playerId, type, location, null, name);
        if (!callStartEvent(playerId, pending)) {
            return false;
        }
        stopNavigation(playerId, StopReason.REPLACED);
        active.put(playerId, ActiveNavigation.toLocation(type, location, name));
        return true;
    }

    /**
     * Moves an active stronghold navigation to a more precise spot (the end portal frame). This is not a new
     * navigation, so no events fire. Does nothing if the player is no longer navigating to a stronghold.
     */
    public void refineStrongholdTarget(UUID playerId, Location location) {
        ActiveNavigation current = active.get(playerId);
        if (current != null && current.type() == NavigationType.STRONGHOLD && canUseNavigation(playerId)) {
            active.put(playerId, ActiveNavigation.toLocation(NavigationType.STRONGHOLD, location, null));
        }
    }

    public void stopNavigation(UUID playerId, StopReason reason) {
        NavigationSession endedSession = getSession(playerId);
        ActiveNavigation ended = active.remove(playerId);
        actionBarSuppressed.remove(playerId);

        Player navigator = Bukkit.getPlayer(playerId);
        if (ended != null && ended.targetPlayer() != null && navigator != null) {
            Player target = Bukkit.getPlayer(ended.targetPlayer());
            if (target != null && target.isOnline() && shouldNotifyTarget(playerId, ended.targetPlayer())) {
                TargetNotifier.navigationStopped(target, navigator);
            }
        }
        NavigationTasks.getInstance().cancelGuidance(playerId);

        if (endedSession != null) {
            Bukkit.getPluginManager().callEvent(new PathFinderNavigationStopEvent(navigator, endedSession,
                    reason == null ? StopReason.OTHER : reason));
        }
    }

    public void stopAllNavigations(StopReason reason) {
        for (UUID playerId : Set.copyOf(active.keySet())) {
            stopNavigation(playerId, reason);
        }
        active.clear();
    }

    private boolean callStartEvent(UUID playerId, NavigationSession session) {
        PathFinderNavigationStartEvent event = new PathFinderNavigationStartEvent(Bukkit.getPlayer(playerId), session);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    /** Admins who ignore privacy or the global switch navigate silently. */
    private boolean shouldNotifyTarget(UUID playerId, UUID targetId) {
        boolean silentlyBypassing = isLocationHidden(targetId) || !preferences.isNavigationEnabled();
        return !(silentlyBypassing && canBypassRestrictions(playerId));
    }

    // ---------------------------------------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------------------------------------

    public boolean isNavigating(UUID playerId) {
        return active.containsKey(playerId);
    }

    /** The player's current navigation, or {@code null}. */
    public ActiveNavigation getActive(UUID playerId) {
        return active.get(playerId);
    }

    public Set<UUID> getNavigatingPlayers() {
        return Set.copyOf(active.keySet());
    }

    /** Builds an API snapshot of the player's current navigation, or {@code null} if they are not navigating. */
    public NavigationSession getSession(UUID playerId) {
        ActiveNavigation navigation = playerId == null ? null : active.get(playerId);
        if (navigation == null) {
            return null;
        }
        if (navigation.type() == NavigationType.PLAYER) {
            Player target = Bukkit.getPlayer(navigation.targetPlayer());
            return new NavigationSession(playerId, NavigationType.PLAYER,
                    target != null ? target.getLocation() : null, navigation.targetPlayer(),
                    target != null ? target.getName() : null);
        }
        return new NavigationSession(playerId, navigation.type(), navigation.location().clone(), null,
                navigation.displayName());
    }

    /** Hides the action bar until the player's current navigation ends. */
    public void suppressActionBar(UUID playerId) {
        if (playerId != null) {
            actionBarSuppressed.add(playerId);
        }
    }

    public boolean isActionBarSuppressed(UUID playerId) {
        return playerId != null && actionBarSuppressed.contains(playerId);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Permissions, privacy and the global switch
    // ---------------------------------------------------------------------------------------------------------

    /** While navigation is disabled server-wide, only admins can navigate. */
    public boolean canUseNavigation(UUID playerId) {
        if (preferences.isNavigationEnabled()) {
            return true;
        }
        return Bukkit.getPlayer(playerId) != null && canBypassRestrictions(playerId);
    }

    public boolean canBypassRestrictions(UUID playerId) {
        return PathFinderPlugin.getInstance().canBypassNavigationRestrictions(playerId);
    }

    /** Whether {@code target} is invisible and the config forbids navigating to invisible players. */
    public boolean isHiddenByInvisibility(Player target) {
        if (target == null || !target.isOnline()) {
            return false;
        }
        try {
            boolean allowInvisible = PathFinderPlugin.getInstance().getConfig()
                    .getBoolean("allow_navigation_to_invisible", false);
            boolean invisible = target.hasPotionEffect(PotionEffectType.INVISIBILITY) || target.isInvisible();
            return invisible && !allowInvisible;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean isLocationHidden(UUID playerId) {
        return preferences.isHidden(playerId);
    }

    /** @return the new state */
    public boolean toggleLocationHidden(UUID playerId) {
        boolean hidden = !preferences.isHidden(playerId);
        preferences.setHidden(playerId, hidden);
        preferences.save(PathFinderPlugin.getInstance());
        return hidden;
    }

    public void setLocationHidden(UUID playerId, boolean hidden) {
        if (playerId != null && isLocationHidden(playerId) != hidden) {
            toggleLocationHidden(playerId);
        }
    }

    public boolean isNavigationEnabled() {
        return preferences.isNavigationEnabled();
    }

    /**
     * Flips the server-wide switch. Turning it off stops everyone's navigation except admins'.
     *
     * @return the new state
     */
    public boolean toggleNavigationEnabled() {
        boolean enabled = !preferences.isNavigationEnabled();
        preferences.setNavigationEnabled(enabled);
        if (!enabled) {
            for (UUID playerId : Set.copyOf(active.keySet())) {
                if (Bukkit.getPlayer(playerId) != null && !canBypassRestrictions(playerId)) {
                    stopNavigation(playerId, StopReason.NAVIGATION_DISABLED);
                }
            }
        }
        preferences.save(PathFinderPlugin.getInstance());
        return enabled;
    }

    public void setNavigationEnabled(boolean enabled) {
        if (preferences.isNavigationEnabled() != enabled) {
            toggleNavigationEnabled();
        }
    }

    public void loadPreferences(JavaPlugin plugin) {
        preferences.load(plugin);
    }

    public void savePreferences(JavaPlugin plugin) {
        preferences.save(plugin);
    }
}
