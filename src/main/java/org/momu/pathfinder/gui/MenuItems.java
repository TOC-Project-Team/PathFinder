package org.momu.pathfinder.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Builds menu icons.
 */
final class MenuItems {
    private MenuItems() {
    }

    static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (!lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    static ItemStack item(Material material, Component name, Component... lore) {
        return item(material, name, List.of(lore));
    }

    /** A plain gray pane used to fill empty slots. */
    static ItemStack filler() {
        return new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1);
    }
}
