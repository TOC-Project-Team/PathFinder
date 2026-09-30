package org.momu.pathfinder.listener;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.momu.pathfinder.api.event.PathFinderNavigationStopEvent.StopReason;
import org.momu.pathfinder.config.Messages;
import org.momu.pathfinder.navigation.NavigationService;
import org.momu.pathfinder.navigation.runtime.Scheduling;
import org.momu.pathfinder.navigation.session.ActiveNavigation;
import org.momu.pathfinder.navigation.session.NavigationTracker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ends navigations when the player (or their target) can no longer continue: death, spectator mode, leaving,
 * changing world. Players who were following someone who died pick the navigation back up after the respawn.
 */
public final class PlayerLifecycleListener implements Listener {
    private static final long RESUME_DELAY_TICKS = 20L;

    private final NavigationTracker tracker = NavigationTracker.getInstance();
    /** Dead player -> players who were following them. */
    private final Map<UUID, List<UUID>> followersOfDead = new HashMap<>();

    @EventHandler
    @SuppressWarnings("deprecation")
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();
        if (event.getNewGameMode() == GameMode.SPECTATOR && tracker.isNavigating(player.getUniqueId())) {
            tracker.stopNavigation(player.getUniqueId(), StopReason.GAME_MODE_CHANGED);
            player.sendMessage(ChatColor.YELLOW + Messages.get(player, "messages.spectator-mode"));
        }
    }

    @EventHandler
    @SuppressWarnings("deprecation")
    public void onDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        if (tracker.isNavigating(dead.getUniqueId())) {
            tracker.stopNavigation(dead.getUniqueId(), StopReason.PLAYER_DIED);
            dead.sendMessage(ChatColor.YELLOW + Messages.get(dead, "messages.death-navi"));
        }

        List<UUID> followers = new ArrayList<>();
        for (Player follower : Bukkit.getOnlinePlayers()) {
            ActiveNavigation navigation = tracker.getActive(follower.getUniqueId());
            if (navigation != null && dead.getUniqueId().equals(navigation.targetPlayer())) {
                followers.add(follower.getUniqueId());
                tracker.stopNavigation(follower.getUniqueId(), StopReason.TARGET_UNAVAILABLE);
                follower.sendMessage(ChatColor.YELLOW + Messages.get(follower, "messages.death-navi-2"));
            }
        }
        if (!followers.isEmpty()) {
            followersOfDead.put(dead.getUniqueId(), followers);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        UUID respawnedId = event.getPlayer().getUniqueId();
        if (!followersOfDead.containsKey(respawnedId)) {
            return;
        }
        Scheduling.runLater(() -> resumeFollowers(respawnedId), RESUME_DELAY_TICKS);
    }

    @SuppressWarnings("deprecation")
    private void resumeFollowers(UUID respawnedId) {
        List<UUID> followers = followersOfDead.remove(respawnedId);
        Player respawned = Bukkit.getPlayer(respawnedId);
        if (followers == null || respawned == null) {
            return;
        }
        for (UUID followerId : followers) {
            Player follower = Bukkit.getPlayer(followerId);
            if (follower != null && follower.isOnline()
                    && NavigationService.getInstance().navigateToPlayer(follower, respawned)) {
                follower.sendMessage(ChatColor.GREEN + Messages.get(follower, "messages.respawn-navi"));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (tracker.isNavigating(playerId)) {
            tracker.stopNavigation(playerId, StopReason.PLAYER_QUIT);
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        stopBecauseWorldChanged(event.getPlayer());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        for (Player player : event.getWorld().getPlayers()) {
            stopBecauseWorldChanged(player);
        }
    }

    @SuppressWarnings("deprecation")
    private void stopBecauseWorldChanged(Player player) {
        if (tracker.isNavigating(player.getUniqueId())) {
            tracker.stopNavigation(player.getUniqueId(), StopReason.WORLD_CHANGED);
            player.sendMessage(ChatColor.YELLOW + Messages.get(player, "messages.navigation-stopped"));
        }
    }
}
