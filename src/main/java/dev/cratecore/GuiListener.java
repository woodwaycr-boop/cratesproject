package dev.cratecore;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** The preview menu and the spinning reel are read-only: every click or drag while they are open is cancelled. */
final class GuiListener implements Listener {

    private static boolean isCrateMenu(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof CratePreviewGui || holder instanceof CrateSpin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (isCrateMenu(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (isCrateMenu(event.getInventory())) {
            event.setCancelled(true);
        }
    }
}
