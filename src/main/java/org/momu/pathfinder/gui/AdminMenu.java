package org.momu.pathfinder.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.momu.pathfinder.config.Messages;
import org.momu.pathfinder.navigation.session.NavigationTracker;

/**
 * {@code /toc admin}: a single toggle for server-wide navigation.
 */
final class AdminMenu implements Menu {
    static final Component TITLE = Component.text("PathFinder").color(NamedTextColor.BLUE);
    private static final int TOGGLE_SLOT = 13;

    private final MenuManager menus;

    AdminMenu(MenuManager menus) {
        this.menus = menus;
    }

    void open(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 27, TITLE);
        menus.register(inventory, this);

        boolean enabled = NavigationTracker.getInstance().isNavigationEnabled();
        String status = Messages.get(player, "messages.current-status") + ": "
                + Messages.get(player, enabled ? "messages.enabled" : "messages.disabled");
        inventory.setItem(TOGGLE_SLOT, MenuItems.item(enabled ? Material.ENDER_PEARL : Material.BARRIER,
                Component.text(Messages.get(player, "messages.global-navi"))
                        .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED),
                Component.text(status).color(NamedTextColor.GRAY)));

        player.openInventory(inventory);
    }

    @Override
    public void handleClick(Player player, InventoryClickEvent event) {
        Material clicked = event.getCurrentItem().getType();
        if (event.getSlot() == TOGGLE_SLOT && (clicked == Material.ENDER_PEARL || clicked == Material.BARRIER)) {
            toggleNavigation(player);
        }
    }

    @SuppressWarnings("deprecation")
    private void toggleNavigation(Player player) {
        if (!player.hasPermission("toc.admin")) {
            player.sendMessage(Component.text(Messages.get(player, "messages.no-permission")).color(NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            open(player);
            return;
        }
        boolean enabled = NavigationTracker.getInstance().toggleNavigationEnabled();
        player.sendMessage(enabled
                ? ChatColor.GREEN + Messages.get(player, "messages.global-navi-open")
                : ChatColor.YELLOW + Messages.get(player, "messages.global-navi-closed"));
        player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1.0f, 1.0f);
        open(player);
    }
}
