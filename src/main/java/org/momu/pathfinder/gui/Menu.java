package org.momu.pathfinder.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * A chest menu opened by PathFinder.
 */
interface Menu {
    /** Called for clicks on a non-empty slot of the menu's own inventory. */
    void handleClick(Player player, InventoryClickEvent event);
}
