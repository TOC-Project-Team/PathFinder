package org.momu.pathfinder.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.momu.pathfinder.gui.MenuManager;

/**
 * Keeps items in PathFinder menus from being taken and forwards clicks to the menu.
 */
public final class MenuListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory clickedArea = clickedInventory(event, player);
        MenuManager menus = MenuManager.getInstance();
        if (clickedArea == null || !menus.isMenu(clickedArea)) {
            return;
        }
        event.setCancelled(true);

        Inventory menu = event.getView().getTopInventory();
        if (event.getClickedInventory() != menu || event.getCurrentItem() == null) {
            return;
        }
        menus.handleClick(player, menu, event);
    }

    /** The inventory that was clicked, falling back to the open menu for clicks outside any slot. */
    private static Inventory clickedInventory(InventoryClickEvent event, Player player) {
        try {
            Inventory clicked = event.getClickedInventory();
            return clicked != null ? clicked : player.getOpenInventory().getTopInventory();
        } catch (Exception e) {
            try {
                return player.getOpenInventory().getTopInventory();
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            MenuManager.getInstance().handleClose(event.getView().getTopInventory());
        }
    }
}
