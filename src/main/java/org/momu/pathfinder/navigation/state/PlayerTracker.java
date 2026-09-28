package org.momu.pathfinder.navigation.state;

import org.bukkit.Location;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.momu.pathfinder.api.NavigationSession;
import org.momu.pathfinder.api.NavigationType;
import org.momu.pathfinder.api.event.PathFinderNavigationStartEvent;
import org.momu.pathfinder.api.event.PathFinderNavigationStopEvent;
import org.momu.pathfinder.api.event.PathFinderNavigationStopEvent.StopReason;
import org.momu.pathfinder.bootstrap.PathFinderPlugin;
import org.momu.pathfinder.config.LanguageManager;
import org.momu.pathfinder.presentation.listener.MasterListener;

import java.io.File;
import java.io.IOException;

public class PlayerTracker {
    private final Set<UUID> hiddenPlayers = new HashSet<>();
    private final Set<UUID> navigatingPlayers = new HashSet<>();
    private final java.util.Map<UUID, UUID> navigationTargets = new java.util.HashMap<>();
    private final java.util.Map<UUID, Location> strongholdNavigations = new java.util.HashMap<>();
    private final java.util.Map<UUID, Location> beaconNavigations = new java.util.HashMap<>();
    private final java.util.Map<UUID, Location> waypointNavigations = new java.util.HashMap<>();
    private final java.util.Map<UUID, String> waypointNames = new java.util.HashMap<>();
    private final Set<UUID> customLocationNavigations = new HashSet<>();
    private boolean navigationEnabled = true;
    private final Set<UUID> actionBarSuppressedPlayers = new HashSet<>();

    private PlayerTracker() {
    }

    public static PlayerTracker getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final PlayerTracker INSTANCE = new PlayerTracker();
    }

    public boolean toggleHideLocation(UUID playerUUID) {
        boolean hidden;
        if (hiddenPlayers.contains(playerUUID)) {
            hiddenPlayers.remove(playerUUID);
            hidden = false;
        } else {
            hiddenPlayers.add(playerUUID);
            hidden = true;
        }
        saveData(PathFinderPlugin.getInstance());
        return hidden;
    }

    public void setLocationHidden(UUID playerUUID, boolean hidden) {
        if (playerUUID == null || isLocationHidden(playerUUID) == hidden) {
            return;
        }
        toggleHideLocation(playerUUID);
    }

    public boolean isLocationHidden(UUID playerUUID) {
        return hiddenPlayers.contains(playerUUID);
    }

    public boolean isNavigationBlockedByInvisibility(org.bukkit.entity.Player targetPlayer) {
        if (targetPlayer == null || !targetPlayer.isOnline()) {
            return false;
        }
        try {
            boolean allowInvisible = PathFinderPlugin.getInstance().getConfig()
                    .getBoolean("allow_navigation_to_invisible", false);
            boolean isInvisible = targetPlayer.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY)
                    || targetPlayer.isInvisible();
            return !allowInvisible && isInvisible;
        } catch (Exception ignore) {
            return false;
        }
    }

    public boolean setNavigationTarget(UUID playerUUID, UUID targetUUID) {
        if (!canPlayerUseNavigation(playerUUID)) {
            return false;
        }

        UUID currentTarget = navigationTargets.get(playerUUID);
        if (targetUUID != null && targetUUID.equals(currentTarget)) {
            return false;
        }

        try {
            org.bukkit.entity.Player targetPlayerCheck = org.bukkit.Bukkit.getPlayer(targetUUID);
            if (isNavigationBlockedByInvisibility(targetPlayerCheck)) {
                return false;
            }
        } catch (Exception ignore) {}

        if (isLocationHidden(targetUUID)) {
            boolean canBypass = PathFinderPlugin.getInstance().canBypassNavigationRestrictions(playerUUID);

            if (!canBypass) {
                return false;
            }
        }

        org.bukkit.entity.Player startTarget = org.bukkit.Bukkit.getPlayer(targetUUID);
        NavigationSession pending = new NavigationSession(playerUUID, NavigationType.PLAYER,
                startTarget != null ? startTarget.getLocation() : null, targetUUID,
                startTarget != null ? startTarget.getName() : null);
        if (!callStartEvent(playerUUID, pending)) {
            return false;
        }

        stopNavigation(playerUUID, StopReason.REPLACED);

        navigationTargets.put(playerUUID, targetUUID);
        navigatingPlayers.add(playerUUID);

        org.bukkit.entity.Player targetPlayer = org.bukkit.Bukkit.getPlayer(targetUUID);
        org.bukkit.entity.Player navPlayer = org.bukkit.Bukkit.getPlayer(playerUUID);
        if (targetPlayer != null && navPlayer != null && targetPlayer.isOnline()) {
            boolean shouldShowNotification = true;

            if (isLocationHidden(targetUUID)) {
                boolean canBypass = PathFinderPlugin.getInstance().canBypassNavigationRestrictions(playerUUID);
                if (canBypass) {
                    shouldShowNotification = false;
                }
            }

            if (!navigationEnabled) {
                boolean canBypass = PathFinderPlugin.getInstance().canBypassNavigationRestrictions(playerUUID);
                if (canBypass) {
                    shouldShowNotification = false;
                }
            }

            if (shouldShowNotification) {
                org.bukkit.boss.BossBar bossBar = org.bukkit.Bukkit
                        .createBossBar(
                                LanguageManager.getInstance().getString(targetPlayer,
                                        "messages.navigating-player-to-you",
                                        navPlayer.getName()),
                                org.bukkit.boss.BarColor.BLUE, org.bukkit.boss.BarStyle.SOLID);
                bossBar.setProgress(1.0);
                bossBar.addPlayer(targetPlayer);
                bossBar.setVisible(true);
                targetPlayer
                        .sendMessage("§a" + LanguageManager.getInstance().getString(targetPlayer,
                                "messages.navigating-player-to-you",
                                navPlayer.getName()));
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        bossBar.removeAll();
                    }
                }.runTaskLater(PathFinderPlugin.getInstance(), 100L);
            }
        }
        return true;
    }

    public boolean canPlayerUseNavigation(UUID playerUUID) {
        if (!navigationEnabled) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(playerUUID);
            if (player == null)
                return false;

            return PathFinderPlugin.getInstance().canBypassNavigationRestrictions(playerUUID);
        }

        return true;
    }

    public void stopNavigation(UUID playerUUID) {
        stopNavigation(playerUUID, StopReason.OTHER);
    }

    public void stopNavigation(UUID playerUUID, StopReason reason) {
        NavigationSession endedSession = getSession(playerUUID);
        UUID targetUUID = getNavigationTarget(playerUUID);
        org.bukkit.entity.Player navPlayer = org.bukkit.Bukkit.getPlayer(playerUUID);

        navigationTargets.remove(playerUUID);
        strongholdNavigations.remove(playerUUID);
        beaconNavigations.remove(playerUUID);
        waypointNavigations.remove(playerUUID);
        waypointNames.remove(playerUUID);
        customLocationNavigations.remove(playerUUID);
        navigatingPlayers.remove(playerUUID);
        actionBarSuppressedPlayers.remove(playerUUID);

        if (targetUUID != null && navPlayer != null) {
            org.bukkit.entity.Player targetPlayer = org.bukkit.Bukkit.getPlayer(targetUUID);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                boolean shouldShowNotification = true;

                if (isLocationHidden(targetUUID)) {
                    boolean canBypass = PathFinderPlugin.getInstance().canBypassNavigationRestrictions(playerUUID);
                    if (canBypass) {
                        shouldShowNotification = false;
                    }
                }

                if (!navigationEnabled) {
                    boolean canBypass = PathFinderPlugin.getInstance().canBypassNavigationRestrictions(playerUUID);
                    if (canBypass) {
                        shouldShowNotification = false;
                    }
                }

                if (shouldShowNotification) {
                    org.bukkit.boss.BossBar bossBar = org.bukkit.Bukkit
                            .createBossBar(
                                    LanguageManager.getInstance().getString(targetPlayer,
                                            "messages.navigating-player-to-you-cancelled", navPlayer.getName()),
                                    org.bukkit.boss.BarColor.RED, org.bukkit.boss.BarStyle.SOLID);
                    bossBar.setProgress(1.0);
                    bossBar.addPlayer(targetPlayer);
                    bossBar.setVisible(true);
                    targetPlayer
                            .sendMessage("§a" + LanguageManager.getInstance().getString(targetPlayer,
                                    "messages.navigating-player-to-you-cancelled",
                                    navPlayer.getName()));
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            bossBar.removeAll();
                        }
                    }.runTaskLater(PathFinderPlugin.getInstance(), 100L);
                }
            }
        }
        MasterListener.getGuiManager().removeParticleTask(playerUUID);

        if (endedSession != null) {
            org.bukkit.Bukkit.getPluginManager().callEvent(new PathFinderNavigationStopEvent(navPlayer, endedSession,
                    reason == null ? StopReason.OTHER : reason));
        }
    }

    private boolean callStartEvent(UUID playerUUID, NavigationSession session) {
        PathFinderNavigationStartEvent event = new PathFinderNavigationStartEvent(
                org.bukkit.Bukkit.getPlayer(playerUUID), session);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    /**
     * Builds an API snapshot of the player's current navigation, or {@code null} if they are not navigating.
     * The lookup order matches the priority used by PathFinding.startPathfinding.
     */
    public NavigationSession getSession(UUID playerUUID) {
        if (playerUUID == null) {
            return null;
        }
        Location beacon = beaconNavigations.get(playerUUID);
        if (beacon != null) {
            return new NavigationSession(playerUUID, NavigationType.BEACON, beacon.clone(), null, null);
        }
        Location waypoint = waypointNavigations.get(playerUUID);
        if (waypoint != null) {
            NavigationType type = customLocationNavigations.contains(playerUUID)
                    ? NavigationType.LOCATION : NavigationType.WAYPOINT;
            return new NavigationSession(playerUUID, type, waypoint.clone(), null, waypointNames.get(playerUUID));
        }
        Location stronghold = strongholdNavigations.get(playerUUID);
        if (stronghold != null) {
            return new NavigationSession(playerUUID, NavigationType.STRONGHOLD, stronghold.clone(), null, null);
        }
        UUID targetUUID = navigationTargets.get(playerUUID);
        if (targetUUID != null) {
            org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(targetUUID);
            return new NavigationSession(playerUUID, NavigationType.PLAYER,
                    target != null ? target.getLocation() : null, targetUUID,
                    target != null ? target.getName() : null);
        }
        return null;
    }

    public void suppressActionBarForCurrentSession(UUID playerUUID) {
        if (playerUUID != null) actionBarSuppressedPlayers.add(playerUUID);
    }

    public boolean isActionBarSuppressed(UUID playerUUID) {
        return playerUUID != null && actionBarSuppressedPlayers.contains(playerUUID);
    }

    public boolean isNavigating(UUID playerUUID) {
        return navigatingPlayers.contains(playerUUID) || strongholdNavigations.containsKey(playerUUID) || waypointNavigations.containsKey(playerUUID);
    }

    public UUID getNavigationTarget(UUID playerUUID) {
        return navigationTargets.get(playerUUID);
    }

    public boolean setStrongholdNavigation(UUID playerUUID, Location strongholdLocation) {
        if (!canPlayerUseNavigation(playerUUID)) {
            return false;
        }

        // Refining an active stronghold target (e.g. to the portal frame) is not a new navigation.
        if (!strongholdNavigations.containsKey(playerUUID)) {
            NavigationSession pending = new NavigationSession(playerUUID, NavigationType.STRONGHOLD,
                    strongholdLocation, null, null);
            if (!callStartEvent(playerUUID, pending)) {
                return false;
            }
            if (isNavigating(playerUUID)) {
                stopNavigation(playerUUID, StopReason.REPLACED);
            }
        }

        navigationTargets.remove(playerUUID);
        beaconNavigations.remove(playerUUID);

        strongholdNavigations.put(playerUUID, strongholdLocation);
        navigatingPlayers.add(playerUUID);
        return true;
    }

    public Location getStrongholdNavigation(UUID playerUUID) {
        return strongholdNavigations.get(playerUUID);
    }

    public boolean setBeaconNavigation(UUID playerUUID, Location beaconLocation) {
        if (!canPlayerUseNavigation(playerUUID)) {
            return false;
        }

        NavigationSession pending = new NavigationSession(playerUUID, NavigationType.BEACON, beaconLocation, null,
                null);
        if (!callStartEvent(playerUUID, pending)) {
            return false;
        }

        stopNavigation(playerUUID, StopReason.REPLACED);

        beaconNavigations.put(playerUUID, beaconLocation);
        navigatingPlayers.add(playerUUID);
        return true;
    }

    public Location getBeaconNavigation(UUID playerUUID) {
        return beaconNavigations.get(playerUUID);
    }

    public boolean setWaypointNavigation(UUID playerUUID, Location location, String name) {
        return setWaypointNavigation(playerUUID, location, name, NavigationType.WAYPOINT);
    }

    /**
     * Starts a fixed-location navigation. {@code type} is {@link NavigationType#WAYPOINT} for saved waypoints and
     * {@link NavigationType#LOCATION} for plugin supplied locations; both use the same runtime path.
     */
    public boolean setWaypointNavigation(UUID playerUUID, Location location, String name, NavigationType type) {
        if (!canPlayerUseNavigation(playerUUID)) {
            return false;
        }
        NavigationType sessionType = type == NavigationType.LOCATION ? NavigationType.LOCATION : NavigationType.WAYPOINT;
        NavigationSession pending = new NavigationSession(playerUUID, sessionType, location, null, name);
        if (!callStartEvent(playerUUID, pending)) {
            return false;
        }
        if (isNavigating(playerUUID)) {
            stopNavigation(playerUUID, StopReason.REPLACED);
        }
        navigationTargets.remove(playerUUID);
        strongholdNavigations.remove(playerUUID);
        beaconNavigations.remove(playerUUID);
        waypointNavigations.put(playerUUID, location);
        if (name != null) waypointNames.put(playerUUID, name);
        if (sessionType == NavigationType.LOCATION) customLocationNavigations.add(playerUUID);
        navigatingPlayers.add(playerUUID);
        return true;
    }

    public Location getWaypointNavigation(UUID playerUUID) {
        return waypointNavigations.get(playerUUID);
    }

    public String getWaypointName(UUID playerUUID) {
        return waypointNames.get(playerUUID);
    }

    public void clearAllNavigations() {
        navigationTargets.clear();
        strongholdNavigations.clear();
        beaconNavigations.clear();
        waypointNavigations.clear();
        waypointNames.clear();
        customLocationNavigations.clear();
        navigatingPlayers.clear();
    }

    public java.util.Set<UUID> getAllNavigatingPlayers() {
        java.util.Set<UUID> set = new java.util.HashSet<>(navigatingPlayers);
        set.addAll(navigationTargets.keySet());
        set.addAll(strongholdNavigations.keySet());
        set.addAll(beaconNavigations.keySet());
        set.addAll(waypointNavigations.keySet());
        return set;
    }

    public void stopAllNavigations() {
        Set<UUID> allNavigatingPlayers = new HashSet<>(navigatingPlayers);

        for (UUID playerUUID : allNavigatingPlayers) {
            stopNavigation(playerUUID, StopReason.PLUGIN_DISABLED);
        }

        clearAllNavigations();
    }

    public boolean toggleNavigationEnabled() {
        navigationEnabled = !navigationEnabled;
        if (!navigationEnabled) {
            stopNonPrivilegedNavigations();
        }
        saveData(PathFinderPlugin.getInstance());
        return navigationEnabled;
    }

    public void setNavigationEnabled(boolean enabled) {
        if (navigationEnabled != enabled) {
            toggleNavigationEnabled();
        }
    }

    public boolean isNavigationEnabled() {
        return navigationEnabled;
    }

    private void stopNonPrivilegedNavigations() {
        Set<UUID> allNavigatingPlayers = new HashSet<>(navigatingPlayers);

        for (UUID playerUUID : allNavigatingPlayers) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(playerUUID);
            if (player == null)
                continue;

            if (!PathFinderPlugin.getInstance().canBypassNavigationRestrictions(playerUUID)) {
                stopNavigation(playerUUID, StopReason.NAVIGATION_DISABLED);
            }
        }
    }

    private static final String DATA_FILE_NAME = "playerdata.yml";

    public void loadData(JavaPlugin plugin) {
        if (plugin == null)
            return;
        File file = new File(plugin.getDataFolder(), DATA_FILE_NAME);
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String uuidStr : yaml.getStringList("hiddenPlayers")) {
            try {
                hiddenPlayers.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {
            }
        }

        navigationEnabled = yaml.getBoolean("navigationEnabled", true);
    }

    public void saveData(JavaPlugin plugin) {
        if (plugin == null)
            return;
        File file = new File(plugin.getDataFolder(), DATA_FILE_NAME);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("hiddenPlayers", hiddenPlayers.stream().map(UUID::toString).toList());

        yaml.set("navigationEnabled", navigationEnabled);

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning(
                    LanguageManager.getInstance().getString("messages.pathfinder-config-update-error", e.getMessage()));
        }
    }

}
