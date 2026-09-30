package org.momu.pathfinder.navigation.runtime;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.momu.pathfinder.api.NavigationSession;
import org.momu.pathfinder.api.NavigationType;
import org.momu.pathfinder.api.event.PathFinderNavigationArriveEvent;
import org.momu.pathfinder.api.event.PathFinderNavigationStopEvent.StopReason;
import org.momu.pathfinder.config.Messages;
import org.momu.pathfinder.config.PathfinderConfig;
import org.momu.pathfinder.navigation.display.ActionBarHud;
import org.momu.pathfinder.navigation.display.PathRenderer;
import org.momu.pathfinder.navigation.locate.StrongholdLocator;
import org.momu.pathfinder.navigation.pathfinding.AStarPathfinder;
import org.momu.pathfinder.navigation.pathfinding.PathNode;
import org.momu.pathfinder.navigation.session.ActiveNavigation;
import org.momu.pathfinder.navigation.session.NavigationTracker;

import java.util.List;
import java.util.UUID;

/**
 * Guides one player to their navigation target. Every {@code path_refresh_ticks} it:
 * <ol>
 *     <li>checks on the main thread that the navigation and its target are still valid (stopping it with a
 *     message otherwise),</li>
 *     <li>searches a path off the main thread,</li>
 *     <li>back on the main thread, updates the action bar, detects arrival, or draws the path.</li>
 * </ol>
 */
public final class GuidanceTask extends BukkitRunnable {
    private static final double ARRIVAL_DISTANCE = 3.0;
    /** Within this distance of a stronghold, the exact end portal frame is looked up. */
    private static final double PORTAL_FRAME_LOOKUP_DISTANCE = 300.0;
    private static final int PORTAL_FRAME_SEARCH_RADIUS = 150;

    private final Player player;
    private final UUID playerId;
    private final NavigationType type;
    private final NavigationTracker tracker = NavigationTracker.getInstance();
    private boolean pathSearchRunning;
    private boolean portalFrameSearchRunning;

    private GuidanceTask(Player player, NavigationType type) {
        this.player = player;
        this.playerId = player.getUniqueId();
        this.type = type;
    }

    /**
     * Starts guidance for the player's current navigation on the next tick, replacing any running guidance.
     * Callers set the navigation in {@link NavigationTracker} first.
     */
    public static void start(Player player) {
        Scheduling.runSync(() -> {
            NavigationTracker tracker = NavigationTracker.getInstance();
            UUID playerId = player.getUniqueId();
            // The navigation may have been stopped, or the player may have left, before this ran.
            if (!player.isOnline() || !tracker.isNavigating(playerId)) {
                return;
            }
            ActiveNavigation navigation = tracker.getActive(playerId);
            if (navigation.type() == NavigationType.PLAYER && !canStartFollowing(player, navigation)) {
                return;
            }
            NavigationTasks.getInstance().cancelGuidance(playerId);
            BukkitTask task = Scheduling.runTimer(new GuidanceTask(player, navigation.type()), 0L,
                    PathfinderConfig.PATH_REFRESH_TICKS);
            if (task != null && Scheduling.isPluginEnabled()) {
                NavigationTasks.getInstance().setGuidance(playerId, task);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static boolean canStartFollowing(Player player, ActiveNavigation navigation) {
        NavigationTracker tracker = NavigationTracker.getInstance();
        Player target = Bukkit.getPlayer(navigation.targetPlayer());
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + Messages.get(player, "messages.target-offline"));
            tracker.stopNavigation(player.getUniqueId(), StopReason.TARGET_UNAVAILABLE);
            return false;
        }
        if (tracker.isHiddenByInvisibility(target)) {
            player.sendMessage(ChatColor.YELLOW + Messages.get(player, "messages.target-hidden"));
            tracker.stopNavigation(player.getUniqueId(), StopReason.TARGET_UNAVAILABLE);
            return false;
        }
        if (tracker.isLocationHidden(target.getUniqueId()) && !tracker.canBypassRestrictions(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + Messages.get(player, "messages.target-hidden"));
            tracker.stopNavigation(player.getUniqueId(), StopReason.TARGET_UNAVAILABLE);
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        if (!Scheduling.isPluginEnabled()) {
            cancel();
            return;
        }
        if (!player.isOnline()) {
            cancel();
            NavigationTasks.getInstance().cancelGuidance(playerId);
            return;
        }
        if (player.isDead()) {
            cancel();
            tracker.stopNavigation(playerId, StopReason.PLAYER_DIED);
            return;
        }
        ActiveNavigation navigation = tracker.getActive(playerId);
        if (navigation == null || navigation.type() != type) {
            cancel();
            return;
        }
        Location target = resolveTarget(navigation);
        if (target == null || pathSearchRunning) {
            return;
        }

        Location goal = WaterLanding.adjustTarget(pathGoal(target));
        Location from = toBlock(player.getLocation());
        boolean airborne = Airborne.isAirborne(player);
        pathSearchRunning = true;
        Scheduling.runAsync(() -> {
            List<PathNode> path = airborne ? null : AStarPathfinder.findPath(from, goal);
            Scheduling.runSync(() -> {
                pathSearchRunning = false;
                showFrame(target, path);
            });
        });
    }

    /**
     * Where the target is right now. Stops the navigation with a message and returns {@code null} if it can no
     * longer be reached.
     */
    private Location resolveTarget(ActiveNavigation navigation) {
        return switch (navigation.type()) {
            case PLAYER -> resolvePlayerTarget(navigation.targetPlayer());
            case BEACON -> {
                Location beacon = navigation.location().clone();
                if (beacon.getBlock().getType() != Material.BEACON) {
                    stopUnavailable(ChatColor.RED, "messages.target-beacon-disappear");
                    yield null;
                }
                yield beacon;
            }
            default -> navigation.location().clone();
        };
    }

    private Location resolvePlayerTarget(UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            return stopUnavailable(ChatColor.RED, "messages.target-offline");
        }
        if (tracker.isHiddenByInvisibility(target)) {
            return stopUnavailable(ChatColor.YELLOW, "messages.target-hidden");
        }
        if (target.getGameMode() == GameMode.SPECTATOR) {
            return stopUnavailable(ChatColor.YELLOW, "messages.target-spectator");
        }
        if (target.isDead()) {
            return stopUnavailable(ChatColor.YELLOW, "messages.target-dead");
        }
        if (!player.getWorld().equals(target.getWorld())) {
            return stopUnavailable(ChatColor.RED, "messages.target-dimension");
        }
        if (tracker.isLocationHidden(target.getUniqueId()) && !tracker.canBypassRestrictions(playerId)) {
            return stopUnavailable(ChatColor.RED, "messages.target-hidden-2");
        }
        return target.getLocation().clone();
    }

    @SuppressWarnings("deprecation")
    private Location stopUnavailable(ChatColor color, String messageKey) {
        player.sendMessage(color + Messages.get(player, messageKey));
        cancel();
        tracker.stopNavigation(playerId, StopReason.TARGET_UNAVAILABLE);
        return null;
    }

    /** The point the path search aims for: far targets are clamped to {@code max_search_radius}. */
    private Location pathGoal(Location target) {
        if (type == NavigationType.STRONGHOLD) {
            return toBlock(target);
        }
        Location from = player.getLocation();
        if (from.distance(target) > PathfinderConfig.MAX_SEARCH_RADIUS) {
            Vector direction = target.toVector().subtract(from.toVector()).normalize();
            return toBlock(from.clone().add(direction.multiply(PathfinderConfig.MAX_SEARCH_RADIUS)));
        }
        return target.clone();
    }

    private static Location toBlock(Location location) {
        Location block = location.clone();
        block.setX(block.getBlockX());
        block.setY(block.getBlockY());
        block.setZ(block.getBlockZ());
        return block;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Main-thread frame
    // ---------------------------------------------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    private void showFrame(Location target, List<PathNode> path) {
        if (!Scheduling.isPluginEnabled() || isCancelled()) {
            return;
        }
        if (player.isDead()) {
            tracker.stopNavigation(playerId, StopReason.PLAYER_DIED);
            return;
        }
        if (type == NavigationType.PLAYER) {
            ActiveNavigation navigation = tracker.getActive(playerId);
            Player targetPlayer = navigation == null ? null : Bukkit.getPlayer(navigation.targetPlayer());
            if (targetPlayer != null && targetPlayer.isDead()) {
                tracker.stopNavigation(playerId, StopReason.TARGET_UNAVAILABLE);
                player.sendMessage(ChatColor.YELLOW + Messages.get(player, "messages.target-dead"));
                return;
            }
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            tracker.stopNavigation(playerId, StopReason.GAME_MODE_CHANGED);
            player.sendMessage(ChatColor.YELLOW + Messages.get(player, "messages.spectator-mode"));
            return;
        }

        updateActionBar(target);
        if (player.getLocation().distance(target) <= ARRIVAL_DISTANCE) {
            arrive();
            return;
        }
        PathRenderer.render(player, path);
    }

    private void updateActionBar(Location target) {
        ActiveNavigation navigation = tracker.getActive(playerId);
        String targetName = Messages.get(player, "messages.unknown-target");
        if (navigation != null) {
            switch (navigation.type()) {
                case BEACON -> targetName = Messages.get(player, "messages.beacon-block");
                case STRONGHOLD -> {
                    Location stronghold = navigation.location();
                    boolean atFrame = stronghold.getBlock().getType() == Material.END_PORTAL_FRAME;
                    if (!atFrame && player.getLocation().distance(stronghold) < PORTAL_FRAME_LOOKUP_DISTANCE) {
                        lookUpPortalFrame(stronghold);
                        return;
                    }
                    targetName = Messages.get(player, atFrame ? "messages.end-portal-frame" : "messages.stronghold-name");
                }
                case WAYPOINT, LOCATION -> {
                    String name = navigation.displayName();
                    if (name != null && !name.isEmpty()) {
                        targetName = name;
                    }
                }
                case PLAYER -> {
                    Player targetPlayer = Bukkit.getPlayer(navigation.targetPlayer());
                    if (targetPlayer != null) {
                        targetName = targetPlayer.getName();
                    }
                }
            }
        }
        ActionBarHud.show(player, target, targetName);
    }

    /** Near a stronghold, switches the target to its end portal frame (or gives up if there is none). */
    private void lookUpPortalFrame(Location stronghold) {
        if (portalFrameSearchRunning) {
            return;
        }
        portalFrameSearchRunning = true;
        StrongholdLocator.findNearestPortalFrameAsync(stronghold, PORTAL_FRAME_SEARCH_RADIUS, frame -> {
            portalFrameSearchRunning = false;
            if (!Scheduling.isPluginEnabled() || isCancelled()) {
                return;
            }
            if (frame == null) {
                player.sendMessage("§c" + Messages.get(player, "messages.end-portal-frame-not-found"));
                tracker.stopNavigation(playerId, StopReason.TARGET_UNAVAILABLE);
                return;
            }
            tracker.refineStrongholdTarget(playerId, frame);
            player.sendMessage("§e" + Messages.get(player, "messages.end-portal-frame-coords",
                    frame.getBlockX(), frame.getBlockY(), frame.getBlockZ()));
            ActionBarHud.show(player, frame, Messages.get(player, "messages.end-portal-frame"));
        });
    }

    @SuppressWarnings("deprecation")
    private void arrive() {
        boolean notify = ArrivalCooldown.tryAcquire(playerId);
        NavigationSession session = tracker.getSession(playerId);
        NavigationTasks.getInstance().cancelGuidance(playerId);
        if (session != null) {
            Bukkit.getPluginManager().callEvent(new PathFinderNavigationArriveEvent(player, session));
        }
        tracker.stopNavigation(playerId, StopReason.ARRIVED);
        if (notify) {
            player.sendMessage(ChatColor.GREEN + Messages.get(player, "messages.arrive-destination"));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        }
    }
}
