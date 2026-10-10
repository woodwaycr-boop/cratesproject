package dev.cratecore;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class CrateListener implements Listener {

    private final CrateCore plugin;

    CrateListener(CrateCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Cancellation by other plugins (e.g. a WorldGuard spawn region) is deliberately ignored, so
     * crates keep working in protected areas.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Action action = event.getAction();
        if (block == null || (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK)) {
            return;
        }

        CrateManager manager = plugin.crates();
        String crateId = manager.crateAt(block);
        if (crateId == null) {
            return;
        }

        // Never let the chest underneath open, and never let a key be placed as a block.
        event.setCancelled(true);

        // The event fires once per hand. Only act on the main hand.
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Crate crate = manager.crate(crateId);
        if (crate == null) {
            return; // crate was removed from config.yml
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("cratecore.use")) {
            plugin.messages().send(player, "no-permission");
            return;
        }

        if (action == Action.LEFT_CLICK_BLOCK) {
            player.openInventory(new CratePreviewGui(plugin, crate).getInventory());
        } else {
            open(player, block, crate);
        }
    }

    private void open(Player player, Block block, Crate crate) {
        CrateManager manager = plugin.crates();
        Messages messages = plugin.messages();

        // Key lock: one opening at a time per player.
        if (manager.isSpinning(player)) {
            messages.send(player, "already-opening");
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!crate.id().equals(manager.crateIdOfKey(hand))) {
            messages.send(player, "no-key",
                    Placeholder.component("key", manager.component(crate.keyName())));
            plugin.effects().play(player, "no-key", 1.0f, 1.0f);
            return;
        }

        // Check before taking the key, so a missing economy never eats one.
        Economy economy = plugin.economy();
        if (crate.hasMoneyReward() && economy == null) {
            messages.send(player, "no-economy");
            return;
        }

        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        int winnerIndex = manager.rollIndex(crate);

        // Animation switched off: open instantly.
        if (!plugin.getConfig().getBoolean("animation.enabled", true)) {
            takeKey(player, hand);
            Reward reward = crate.rewards().get(winnerIndex);
            manager.giveReward(player, reward, economy);
            manager.announce(player, crate, reward, center, manager.isGreatWin(crate, reward), true);
            return;
        }

        CrateSpin spin = new CrateSpin(plugin, player, crate, winnerIndex, center);
        if (!spin.open()) {
            return; // another plugin blocked the menu, and the key was not touched
        }
        takeKey(player, hand);
        manager.addSpin(player, spin);
        spin.begin();
    }

    private void takeKey(Player player, ItemStack hand) {
        int amount = hand.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            ItemStack remaining = hand.clone();
            remaining.setAmount(amount - 1);
            player.getInventory().setItemInMainHand(remaining);
        }
    }

    /** Closing the reel menu early skips the animation, but the reward is still paid. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CrateSpin spin) {
            spin.onClose();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CrateSpin spin = plugin.crates().spinOf(event.getPlayer());
        if (spin != null) {
            spin.onQuit();
        }
    }

    /** Fireworks explode with damage by default. Ours are decoration only. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(plugin.effects().fireworkTag(), PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.crates().crateAt(event.getBlock()) != null) {
            event.setCancelled(true);
            plugin.messages().send(event.getPlayer(), "crate-protected");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.crates().crateAt(block) != null);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> plugin.crates().crateAt(block) != null);
    }
}
