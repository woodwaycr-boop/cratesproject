package dev.cratecore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * One crate opening: a 3-row menu whose middle row scrolls like a roulette and slows to a stop on
 * the winning reward.
 *
 * The winner is rolled BEFORE the spin starts and the reel is built around it. The reward is paid
 * the moment the reel stops, or immediately if the player closes the menu, quits, dies, or the
 * plugin is disabled, so a started spin can never lose the key without paying out. The only gap
 * is a hard server crash during the few seconds of a spin.
 *
 * While a spin exists in CrateManager's map, that player can't open another crate.
 */
final class CrateSpin implements InventoryHolder {

    private static final int SIZE = 27;
    private static final int REEL_START = 9;
    private static final int VISIBLE = 9;
    private static final int CENTER_OFFSET = 4;
    private static final int POINTER_TOP = 4;
    private static final int POINTER_BOTTOM = 22;
    private static final int[] BORDER_SLOTS = {0, 1, 2, 3, 5, 6, 7, 8, 18, 19, 20, 21, 23, 24, 25, 26};
    private static final Material[] PANES = {
            Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE, Material.PINK_STAINED_GLASS_PANE
    };

    private final CrateCore plugin;
    private final CrateManager manager;
    private final Player player;
    private final Crate crate;
    private final int winnerIndex;
    private final Reward winner;
    private final boolean great;
    private final Location center;
    private final int steps;
    private final int maxDelay;
    /** Reward indexes in scroll order. The window starts at {@code step}; the winner sits where the window ends up centered. */
    private final int[] strip;
    private final ItemStack[] icons;
    private final ItemStack[] borders = new ItemStack[PANES.length];
    private final Inventory inventory;

    private int step;
    private int frame;
    private boolean rewarded;
    private boolean finished;
    private BukkitTask task;

    CrateSpin(CrateCore plugin, Player player, Crate crate, int winnerIndex, Location center) {
        this.plugin = plugin;
        this.manager = plugin.crates();
        this.player = player;
        this.crate = crate;
        this.winnerIndex = winnerIndex;
        this.winner = crate.rewards().get(winnerIndex);
        this.great = manager.isGreatWin(crate, winner);
        this.center = center;
        this.steps = Math.max(10, plugin.getConfig().getInt("animation.steps", 40));
        this.maxDelay = Math.max(2, plugin.getConfig().getInt("animation.max-delay-ticks", 9));

        List<Reward> rewards = crate.rewards();
        this.icons = new ItemStack[rewards.size()];
        for (int i = 0; i < icons.length; i++) {
            icons[i] = manager.rewardIcon(crate, rewards.get(i));
        }

        this.strip = new int[steps + VISIBLE - 1];
        for (int i = 0; i < strip.length; i++) {
            strip[i] = manager.rollIndex(crate);
        }
        strip[steps - 1 + CENTER_OFFSET] = winnerIndex;

        for (int i = 0; i < PANES.length; i++) {
            borders[i] = pane(PANES[i]);
        }

        Component title = Component.text("Opening ", NamedTextColor.DARK_GRAY)
                .append(manager.component(crate.displayName()));
        this.inventory = Bukkit.createInventory(this, SIZE, title);
        inventory.setItem(POINTER_TOP, pointer("▼"));
        inventory.setItem(POINTER_BOTTOM, pointer("▲"));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // ------------------------------------------------------------------ start

    /** Opens the menu. False if another plugin cancelled it, in which case nothing has been spent yet. */
    boolean open() {
        return player.openInventory(inventory) != null;
    }

    /** Call after the key has been taken and the spin registered. */
    void begin() {
        plugin.effects().play(player, "start", 1.0f, 1.0f);
        render();
        tickEffects();
        scheduleNext();
    }

    // ------------------------------------------------------------------ spinning

    private void scheduleNext() {
        if (step >= steps - 1) {
            land();
            return;
        }
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (finished) {
                return;
            }
            step++;
            render();
            if (step < steps - 1) {
                tickEffects();
            }
            scheduleNext();
        }, delayFor(step));
    }

    /** Starts at 1 tick and eases out to maxDelay, so the reel slows down like a real wheel. */
    private long delayFor(int currentStep) {
        double progress = currentStep / (double) (steps - 1);
        return 1L + Math.round((maxDelay - 1) * progress * progress * progress);
    }

    private void tickEffects() {
        double progress = step / (double) (steps - 1);
        float pitch = (float) (1.9 - 1.1 * progress);
        plugin.effects().play(player, "tick", 0.6f, pitch);
        plugin.effects().spinParticles(center, step);
    }

    private void render() {
        for (int i = 0; i < VISIBLE; i++) {
            inventory.setItem(REEL_START + i, icons[strip[step + i]]);
        }
        renderBorder(step);
    }

    /** Chasing rainbow: each border slot picks a colour from its position plus the offset. */
    private void renderBorder(int offset) {
        for (int slot : BORDER_SLOTS) {
            inventory.setItem(slot, borders[(slot + offset) % borders.length]);
        }
    }

    // ------------------------------------------------------------------ landing

    private void land() {
        task = null;
        grant();

        for (int i = 0; i < VISIBLE; i++) {
            inventory.setItem(REEL_START + i, i == CENTER_OFFSET ? glowing(icons[winnerIndex]) : pane(Material.GRAY_STAINED_GLASS_PANE));
        }
        manager.announce(player, crate, winner, center, great, true);

        int ticks = Math.max(10, plugin.getConfig().getInt("animation.celebration-ticks", 40));
        final int rounds = Math.max(1, ticks / 3);
        task = new BukkitRunnable() {
            private int remaining = rounds;

            @Override
            public void run() {
                if (finished || remaining-- <= 0) {
                    cancel();
                    end();
                    return;
                }
                frame++;
                renderBorder(frame * 3);
            }
        }.runTaskTimer(plugin, 3L, 3L);
    }

    // ------------------------------------------------------------------ finishing

    /** Pays the reward exactly once. */
    private void grant() {
        if (rewarded) {
            return;
        }
        rewarded = true;
        manager.giveReward(player, winner, plugin.economy());
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean menuIsOpen() {
        return player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == this;
    }

    /** Normal end after the celebration: close the menu and release the key lock. */
    private void end() {
        if (finished) {
            return;
        }
        finished = true;
        cancelTask();
        manager.removeSpin(player.getUniqueId());
        if (menuIsOpen()) {
            player.closeInventory();
        }
    }

    /** The menu was closed (by the player, a death, another plugin...). Skips the rest of the animation. */
    void onClose() {
        if (finished) {
            return;
        }
        cancelTask();
        if (!rewarded) {
            grant();
            manager.announce(player, crate, winner, center, great, true);
        }
        finished = true;
        manager.removeSpin(player.getUniqueId());
    }

    /** The player disconnected mid-spin: pay out quietly. */
    void onQuit() {
        if (finished) {
            return;
        }
        cancelTask();
        if (!rewarded) {
            grant();
            manager.announce(player, crate, winner, center, great, false);
        }
        finished = true;
        manager.removeSpin(player.getUniqueId());
    }

    /** Plugin shutting down or reloading. Schedules nothing, because tasks can't be created while disabling. */
    void forceFinish() {
        boolean wasOpen = menuIsOpen();
        onQuit();
        if (wasOpen) {
            player.closeInventory();
        }
    }

    // ------------------------------------------------------------------ items

    private static ItemStack glowing(ItemStack icon) {
        ItemStack copy = icon.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        copy.setItemMeta(meta);
        return copy;
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack pointer(String symbol) {
        ItemStack item = new ItemStack(Material.END_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(symbol, NamedTextColor.YELLOW, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }
}
