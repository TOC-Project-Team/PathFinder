package org.momu.pathfinder.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.momu.pathfinder.api.event.PathFinderNavigationStopEvent.StopReason;
import org.momu.pathfinder.config.Messages;
import org.momu.pathfinder.navigation.NavigationService;
import org.momu.pathfinder.navigation.locate.BeaconLocator;
import org.momu.pathfinder.navigation.locate.StrongholdLocator;
import org.momu.pathfinder.navigation.runtime.Scheduling;
import org.momu.pathfinder.navigation.session.NavigationTracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * {@code /toc cd}: pick an online player to navigate to, plus buttons for privacy, stronghold, beacon and stop.
 *
 * <pre>
 * rows 1-4  player heads (36 per page)
 * row 6     45 previous | 46 privacy* | 47 stronghold | 48 beacon | 49 page / privacy* | 50 stop | 53 next
 * </pre>
 * The privacy toggle sits in slot 49 when there is only one page, otherwise in 46.
 */
final class PlayerNavigationMenu implements Menu {
    private static final int SIZE = 54;
    private static final int HEADS_PER_PAGE = 36;
    private static final int PREVIOUS_SLOT = 45;
    private static final int PRIVACY_SLOT_PAGED = 46;
    private static final int STRONGHOLD_SLOT = 47;
    private static final int BEACON_SLOT = 48;
    private static final int PAGE_SLOT = 49;
    private static final int STOP_SLOT = 50;
    private static final int NEXT_SLOT = 53;
    private static final int STRONGHOLD_SEARCH_RADIUS = 100000;
    private static final int BEACON_SEARCH_RADIUS = 100;

    private final MenuManager menus;

    PlayerNavigationMenu(MenuManager menus) {
        this.menus = menus;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------------------

    void open(Player viewer, int requestedPage) {
        Component title = Component.text(Messages.get(viewer, "messages.player-navigation-cd"))
                .color(NamedTextColor.LIGHT_PURPLE);
        Inventory inventory = Bukkit.createInventory(viewer, SIZE, title);
        menus.register(inventory, this);

        List<Player> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
        targets.removeIf(target -> target.getUniqueId().equals(viewer.getUniqueId()));
        targets.sort(Comparator.comparing(Player::getName));

        int totalPages = (int) Math.ceil((double) targets.size() / HEADS_PER_PAGE);
        int page = Math.max(0, requestedPage);
        if (totalPages > 0 && page >= totalPages) {
            page = totalPages - 1;
        }
        menus.setNavigationMenuPage(viewer.getUniqueId(), page);

        int first = page * HEADS_PER_PAGE;
        int last = Math.min(first + HEADS_PER_PAGE, targets.size());
        for (int i = first; i < last; i++) {
            inventory.setItem(i - first, playerHead(viewer, targets.get(i)));
        }

        if (totalPages > 1) {
            if (page > 0) {
                inventory.setItem(PREVIOUS_SLOT, button(viewer, Material.ARROW, "messages.prev-page",
                        NamedTextColor.YELLOW, "messages.prev-page-desc"));
            }
            if (page < totalPages - 1) {
                inventory.setItem(NEXT_SLOT, button(viewer, Material.ARROW, "messages.next-page",
                        NamedTextColor.YELLOW, "messages.next-page-desc"));
            }
            inventory.setItem(PAGE_SLOT, MenuItems.item(Material.PAPER,
                    Component.text(Messages.get(viewer, "messages.page-indicator", page + 1, totalPages))
                            .color(NamedTextColor.GOLD)));
        }

        inventory.setItem(totalPages <= 1 ? PAGE_SLOT : PRIVACY_SLOT_PAGED, privacyButton(viewer));
        if (viewer.isOp() || viewer.hasPermission("toc.admin")) {
            inventory.setItem(STRONGHOLD_SLOT, strongholdButton(viewer));
        }
        if (viewer.isOp()) {
            inventory.setItem(BEACON_SLOT, beaconButton(viewer));
        }
        if (NavigationTracker.getInstance().isNavigating(viewer.getUniqueId())) {
            inventory.setItem(STOP_SLOT, button(viewer, Material.ENDER_PEARL, "messages.stop", NamedTextColor.RED,
                    "messages.stop-desc"));
        }

        ItemStack filler = MenuItems.filler();
        for (int slot = 0; slot < SIZE; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
        viewer.openInventory(inventory);
    }

    private static ItemStack button(Player viewer, Material material, String nameKey, NamedTextColor color,
                                    String descriptionKey) {
        return MenuItems.item(material, Component.text(Messages.get(viewer, nameKey)).color(color),
                Component.text(Messages.get(viewer, descriptionKey)).color(NamedTextColor.GRAY));
    }

    private static ItemStack playerHead(Player viewer, Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (!(head.getItemMeta() instanceof SkullMeta meta)) {
            return head;
        }
        meta.setPlayerProfile(target.getPlayerProfile());
        meta.displayName(Component.text(target.getName()).color(NamedTextColor.YELLOW));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(Messages.get(viewer, "messages.navigate-to-player")).color(NamedTextColor.GRAY));
        if (viewer.getWorld().equals(target.getWorld())) {
            double distance = viewer.getLocation().distance(target.getLocation());
            lore.add(Component.text(Messages.get(viewer, "messages.navigate-to-player-distance"))
                    .color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(String.format("%.1fm", distance)).color(NamedTextColor.AQUA)));
        } else {
            lore.add(Component.text(Messages.get(viewer, "messages.navigate-to-player-dimension"))
                    .color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(worldName(viewer, target.getWorld())).color(NamedTextColor.GOLD)));
        }
        lore.add(Component.text(Messages.get(viewer, "messages.navigate-to-player-direction"))
                .color(NamedTextColor.DARK_GRAY)
                .append(Component.text(clockDirection(viewer, target.getLocation())).color(NamedTextColor.GOLD)));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private static ItemStack privacyButton(Player viewer) {
        boolean hidden = NavigationTracker.getInstance().isLocationHidden(viewer.getUniqueId());
        return MenuItems.item(hidden ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text(Messages.get(viewer, "messages.player-navigation-privacy")).color(NamedTextColor.GOLD),
                Component.text(Messages.get(viewer, "messages.player-navigation-privacy-desc")).color(NamedTextColor.GRAY),
                hidden
                        ? Component.text(Messages.get(viewer, "messages.player-navigation-privacy-hidden"))
                                .color(NamedTextColor.GREEN)
                        : Component.text(Messages.get(viewer, "messages.player-navigation-privacy-visible"))
                                .color(NamedTextColor.RED));
    }

    private static ItemStack strongholdButton(Player viewer) {
        return MenuItems.item(Material.END_PORTAL_FRAME,
                Component.text(Messages.get(viewer, "messages.stronghold-navigate")).color(NamedTextColor.LIGHT_PURPLE),
                Component.text(Messages.get(viewer, "messages.stronghold-desc")).color(NamedTextColor.GRAY),
                Component.text(Messages.get(viewer, "messages.stronghold-desc-detail")).color(NamedTextColor.GRAY));
    }

    private static ItemStack beaconButton(Player viewer) {
        return MenuItems.item(Material.BEACON,
                Component.text(Messages.get(viewer, "messages.beacon")).color(NamedTextColor.AQUA),
                Component.text(Messages.get(viewer, "messages.beacon-desc")).color(NamedTextColor.GRAY),
                Component.empty(),
                Component.text(Messages.get(viewer, "messages.beacon-desc-op")).color(NamedTextColor.RED),
                Component.text(Messages.get(viewer, "messages.beacon-desc-radius")).color(NamedTextColor.YELLOW));
    }

    private static String worldName(Player viewer, World world) {
        if (world == null) {
            return Messages.get(viewer, "messages.unknown");
        }
        return switch (world.getEnvironment()) {
            case NORMAL -> Messages.get(viewer, "messages.normal-world");
            case NETHER -> Messages.get(viewer, "messages.nether-world");
            case THE_END -> Messages.get(viewer, "messages.end-world");
            default -> world.getName();
        };
    }

    /** Direction to {@code target} as a clock position, 12 being straight ahead. */
    private static String clockDirection(Player viewer, Location target) {
        Location from = viewer.getLocation();
        double deltaX = target.getX() - from.getX();
        double deltaZ = target.getZ() - from.getZ();
        double yaw = Math.toRadians(from.getYaw());
        double rotatedX = deltaX * Math.cos(yaw) + deltaZ * Math.sin(yaw);
        double rotatedZ = -deltaX * Math.sin(yaw) + deltaZ * Math.cos(yaw);

        double angle = Math.toDegrees(Math.atan2(-rotatedX, rotatedZ));
        if (angle < 0) {
            angle += 360;
        }
        int hour = (int) Math.round(angle / 30.0);
        hour = (hour == 0 || hour == 12) ? 12 : hour % 12;
        return Messages.get(viewer, "messages.clock-direction", hour);
    }

    // ---------------------------------------------------------------------------------------------------------
    // Clicks
    // ---------------------------------------------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public void handleClick(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        switch (clicked.getType()) {
            case ARROW -> turnPage(player, event.getSlot());
            case ENDER_PEARL -> {
                NavigationTracker.getInstance().stopNavigation(player.getUniqueId(), StopReason.CANCELLED);
                player.sendMessage(ChatColor.YELLOW + Messages.get(player, "messages.navigation-stopped"));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.5f);
                menus.openNavigationMenu(player);
            }
            case PLAYER_HEAD -> navigateToHead(player, clicked);
            case LIME_DYE, GRAY_DYE -> togglePrivacy(player);
            case END_PORTAL_FRAME -> navigateToStronghold(player);
            case BEACON -> navigateToBeacon(player);
            default -> {
            }
        }
    }

    private void turnPage(Player player, int slot) {
        Integer page = menus.getNavigationMenuPage(player.getUniqueId());
        if (page == null) {
            return;
        }
        if (slot == PREVIOUS_SLOT && page > 0) {
            menus.openNavigationMenu(player, page - 1);
        } else if (slot == NEXT_SLOT) {
            menus.openNavigationMenu(player, page + 1);
        }
    }

    @SuppressWarnings("deprecation")
    private void navigateToHead(Player player, ItemStack head) {
        Player target = headOwner(head);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + Messages.get(player, "messages.unknown-player"));
            return;
        }
        NavigationTracker tracker = NavigationTracker.getInstance();
        boolean targetHidden = tracker.isLocationHidden(target.getUniqueId());
        boolean canBypass = tracker.canBypassRestrictions(player.getUniqueId());

        String refusal = null;
        if (target.getGameMode() == GameMode.SPECTATOR) {
            refusal = ChatColor.YELLOW + Messages.get(player, "messages.cannot-navigate-spectator");
        } else if (tracker.isHiddenByInvisibility(target)) {
            refusal = ChatColor.YELLOW + Messages.get(player, "messages.target-hidden");
        } else if (targetHidden && !canBypass) {
            refusal = ChatColor.RED + Messages.get(player, "messages.target-hidden");
        } else if (!tracker.canUseNavigation(player.getUniqueId())) {
            refusal = ChatColor.RED + Messages.get(player, "messages.global-navigation-disabled");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        } else if (!NavigationService.getInstance().navigateToPlayer(player, target)) {
            boolean alreadyFollowing = tracker.getActive(player.getUniqueId()) != null
                    && target.getUniqueId().equals(tracker.getActive(player.getUniqueId()).targetPlayer());
            refusal = alreadyFollowing
                    ? ChatColor.YELLOW + Messages.get(player, "messages.already-navigating-to-player", target.getName())
                    : ChatColor.RED + Messages.get(player, "messages.nav-start-cancelled");
        }
        if (refusal != null) {
            player.sendMessage(refusal);
            menus.openNavigationMenu(player);
            return;
        }

        String bypassNote = targetHidden && canBypass ? Messages.get(player, "messages.bypass-location-privacy") : "";
        player.sendMessage(ChatColor.GREEN + Messages.get(player, "messages.set-navigation-target")
                + target.getName() + bypassNote);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        menus.openNavigationMenu(player);
    }

    /** The player a head in this menu stands for. */
    @SuppressWarnings("deprecation")
    private static Player headOwner(ItemStack head) {
        if (!(head.getItemMeta() instanceof SkullMeta meta)) {
            return null;
        }
        PlayerProfile profile = meta.getPlayerProfile();
        UUID id = profile == null ? null : profile.getId();
        if (id != null) {
            return Bukkit.getPlayer(id);
        }
        return Bukkit.getPlayer(ChatColor.stripColor(meta.getDisplayName()));
    }

    @SuppressWarnings("deprecation")
    private void togglePrivacy(Player player) {
        boolean hidden = NavigationTracker.getInstance().toggleLocationHidden(player.getUniqueId());
        player.sendMessage(ChatColor.GREEN + Messages.get(player, "messages.location-hidden-status",
                Messages.get(player, hidden ? "messages.location-hidden" : "messages.location-visible")));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        menus.openNavigationMenu(player);
    }

    @SuppressWarnings("deprecation")
    private boolean refuseIfNavigationDisabled(Player player) {
        if (NavigationTracker.getInstance().canUseNavigation(player.getUniqueId())) {
            return false;
        }
        player.sendMessage(ChatColor.RED + Messages.get(player, "messages.global-navigation-disabled"));
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        menus.openNavigationMenu(player);
        return true;
    }

    @SuppressWarnings("deprecation")
    private void navigateToStronghold(Player player) {
        if (refuseIfNavigationDisabled(player)) {
            return;
        }
        player.sendMessage(ChatColor.YELLOW + Messages.get(player, "messages.stronghold-searching"));
        long startTime = System.currentTimeMillis();
        Scheduling.runSync(() -> {
            Location stronghold = StrongholdLocator.locateNearest(player, STRONGHOLD_SEARCH_RADIUS);
            double seconds = (System.currentTimeMillis() - startTime) / 1000.0;
            if (stronghold == null) {
                player.sendMessage(ChatColor.RED + Messages.get(player, "messages.stronghold-not-found"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                menus.openNavigationMenu(player);
                return;
            }
            player.sendMessage(ChatColor.GREEN + Messages.get(player, "messages.stronghold-search-complete"));
            player.sendMessage(ChatColor.GRAY + Messages.get(player, "messages.stronghold-search-time",
                    String.format("%.2f", seconds)));
            player.sendMessage(ChatColor.GRAY + Messages.get(player, "messages.stronghold-coordinates",
                    stronghold.getBlockX(), stronghold.getBlockY(), stronghold.getBlockZ()));
            if (!NavigationService.getInstance().navigateToStronghold(player, stronghold)) {
                player.sendMessage(ChatColor.RED + Messages.get(player, "messages.nav-start-cancelled"));
                menus.openNavigationMenu(player);
                return;
            }
            player.sendMessage(ChatColor.GREEN + Messages.get(player, "messages.stronghold-navigation-set"));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.0f);
            menus.openNavigationMenu(player);
        });
    }

    @SuppressWarnings("deprecation")
    private void navigateToBeacon(Player player) {
        if (refuseIfNavigationDisabled(player)) {
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.5f, 1.0f);
        menus.openNavigationMenu(player);
        BeaconLocator.findNearestAsync(player, BEACON_SEARCH_RADIUS, beacon -> {
            if (beacon == null) {
                if (player.isOnline()) {
                    player.sendMessage(ChatColor.RED + Messages.get(player, "messages.no-beacon-found"));
                }
                return;
            }
            if (!NavigationService.getInstance().navigateToBeacon(player, beacon)) {
                player.sendMessage(ChatColor.RED + Messages.get(player, "messages.nav-start-cancelled"));
                return;
            }
            player.sendMessage(ChatColor.GREEN + Messages.get(player, "messages.beacon-navigation-set"));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.0f);
        });
    }
}
