package dev.cratecore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Read-only menu showing a crate's rewards and their chances. Rewards sit in a 7-wide area inside
 * a glass border, up to 4 rows (28 rewards). The holder type lets GuiListener block every click.
 */
final class CratePreviewGui implements InventoryHolder {

    private static final int COLUMNS = 7;
    private static final int MAX_INNER_ROWS = 4;
    private static final int MAX_REWARDS = COLUMNS * MAX_INNER_ROWS;

    private final Inventory inventory;

    CratePreviewGui(CrateCore plugin, Crate crate) {
        CrateManager manager = plugin.crates();

        List<Reward> rewards = crate.rewards();
        if (rewards.size() > MAX_REWARDS) {
            plugin.getLogger().warning("Crate " + crate.id() + " has " + rewards.size() + " rewards. The preview shows "
                    + MAX_REWARDS + " (all of them can still be won).");
            rewards = rewards.subList(0, MAX_REWARDS);
        }

        int innerRows = Math.max(1, (rewards.size() + COLUMNS - 1) / COLUMNS);
        int size = (innerRows + 2) * 9;

        Component title = MiniMessage.miniMessage().deserialize("<dark_gray>Rewards: </dark_gray>")
                .append(manager.component(crate.displayName()));
        this.inventory = Bukkit.createInventory(this, size, title);

        ItemStack filler = filler();
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, filler);
        }

        for (int i = 0; i < rewards.size(); i++) {
            int row = i / COLUMNS + 1;
            int col = i % COLUMNS + 1;
            inventory.setItem(row * 9 + col, manager.rewardIcon(crate, rewards.get(i)));
        }

        inventory.setItem(size - 5, infoItem(manager, crate));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    /** Uses the key's material for looks only. It carries no key tag, so it can't open anything. */
    private static ItemStack infoItem(CrateManager manager, Crate crate) {
        ItemStack item = new ItemStack(crate.keyMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("How to open", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Hold a ", NamedTextColor.GRAY)
                .append(manager.component(crate.keyName()))
                .append(Component.text(" and right-click", NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false),
                Component.text("the crate.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }
}
