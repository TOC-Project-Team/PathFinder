package org.momu.pathfinder.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens PathFinder's menus and routes clicks back to them. Open menu inventories are tracked so clicks in
 * them can be recognized and cancelled. On Folia each player's menus are handled on that player's region
 * thread, so the maps are shared between threads.
 */
public final class MenuManager {
    private static final MenuManager INSTANCE = new MenuManager();

    private final Map<Inventory, Menu> openMenus = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> navigationMenuPages = new ConcurrentHashMap<>();
    private final AdminMenu adminMenu = new AdminMenu(this);
    private final PlayerNavigationMenu navigationMenu = new PlayerNavigationMenu(this);

    private MenuManager() {
    }

    public static MenuManager getInstance() {
        return INSTANCE;
    }

    /** The admin menu with the server-wide navigation switch. */
    public void openAdminMenu(Player player) {
        if (player != null) {
            adminMenu.open(player);
        }
    }

    /** The player list for navigating to other players, first page. */
    public void openNavigationMenu(Player player) {
        openNavigationMenu(player, 0);
    }

    public void openNavigationMenu(Player player, int page) {
        navigationMenu.open(player, page);
    }

    void register(Inventory inventory, Menu menu) {
        openMenus.put(inventory, menu);
    }

    Integer getNavigationMenuPage(UUID playerId) {
        return navigationMenuPages.get(playerId);
    }

    void setNavigationMenuPage(UUID playerId, int page) {
        navigationMenuPages.put(playerId, page);
    }

    public boolean isMenu(Inventory inventory) {
        return openMenus.containsKey(inventory);
    }

    public void handleClick(Player player, Inventory inventory, InventoryClickEvent event) {
        Menu menu = openMenus.get(inventory);
        if (menu != null) {
            menu.handleClick(player, event);
        }
    }

    public void handleClose(Inventory inventory) {
        openMenus.remove(inventory);
    }
}
