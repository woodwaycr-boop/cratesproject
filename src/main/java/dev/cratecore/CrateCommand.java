package dev.cratecore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /crate preview [crate]                    anyone
 * /crate set|remove|givekey|list|reload     cratecore.admin (givekey also works from the console, e.g. for store plugins)
 */
final class CrateCommand implements TabExecutor {

    private static final List<String> ADMIN_SUBCOMMANDS = List.of("set", "remove", "givekey", "list", "reload");

    private final CrateCore plugin;

    CrateCommand(CrateCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();

        if (args.length == 0) {
            messages.send(sender, "usage");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("preview")) {
            preview(sender, args);
            return true;
        }

        if (!ADMIN_SUBCOMMANDS.contains(sub)) {
            messages.send(sender, "usage");
            return true;
        }
        if (!sender.hasPermission("cratecore.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }

        switch (sub) {
            case "set" -> set(sender, args);
            case "remove" -> remove(sender);
            case "givekey" -> giveKey(sender, args);
            case "list" -> list(sender);
            default -> reload(sender);
        }
        return true;
    }

    private void preview(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        if (!player.hasPermission("cratecore.use")) {
            messages.send(player, "no-permission");
            return;
        }
        Crate crate = args.length >= 2 ? plugin.crates().crate(args[1].toLowerCase(Locale.ROOT)) : null;
        if (crate == null) {
            unknownCrate(sender);
            return;
        }
        player.openInventory(new CratePreviewGui(plugin, crate).getInventory());
    }

    private void set(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        CrateManager manager = plugin.crates();

        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        Crate crate = args.length >= 2 ? manager.crate(args[1].toLowerCase(Locale.ROOT)) : null;
        if (crate == null) {
            unknownCrate(sender);
            return;
        }
        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType().isAir()) {
            messages.send(player, "look-at-block");
            return;
        }
        if (manager.crateAt(target) != null) {
            messages.send(player, "already-crate");
            return;
        }
        manager.addLocation(target, crate.id());
        messages.send(player, "crate-set",
                Placeholder.component("crate", manager.component(crate.displayName())));
    }

    private void remove(CommandSender sender) {
        Messages messages = plugin.messages();

        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return;
        }
        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType().isAir()) {
            messages.send(player, "look-at-block");
            return;
        }
        if (plugin.crates().removeLocation(target)) {
            messages.send(player, "crate-removed");
        } else {
            messages.send(player, "not-a-crate");
        }
    }

    /** /crate givekey <player> <crate> [amount] */
    private void giveKey(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        CrateManager manager = plugin.crates();

        if (args.length < 3) {
            messages.send(sender, "usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        Crate crate = manager.crate(args[2].toLowerCase(Locale.ROOT));
        if (crate == null) {
            unknownCrate(sender);
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException ex) {
                amount = -1;
            }
            if (amount < 1 || amount > 2304) {
                messages.send(sender, "invalid-amount");
                return;
            }
        }

        int remaining = amount;
        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64);
            Map<Integer, ItemStack> leftover = target.getInventory().addItem(manager.createKey(crate, stackSize));
            for (ItemStack extra : leftover.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), extra);
            }
            remaining -= stackSize;
        }

        Component keyName = manager.component(crate.keyName());
        messages.send(sender, "key-given",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.component("key", keyName),
                Placeholder.unparsed("player", target.getName()));
        messages.send(target, "key-received",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.component("key", keyName));
    }

    private void list(CommandSender sender) {
        plugin.messages().send(sender, "crate-list",
                Placeholder.unparsed("list", String.join(", ", plugin.crates().ids())));
    }

    private void reload(CommandSender sender) {
        plugin.reloadConfig();
        plugin.crates().reload();
        plugin.messages().send(sender, "reloaded",
                Placeholder.unparsed("count", String.valueOf(plugin.crates().all().size())));
    }

    private void unknownCrate(CommandSender sender) {
        plugin.messages().send(sender, "unknown-crate",
                Placeholder.unparsed("list", String.join(", ", plugin.crates().ids())));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("cratecore.admin");

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            subs.add("preview");
            if (admin) {
                subs.addAll(ADMIN_SUBCOMMANDS);
            }
            return filter(subs, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            if (sub.equals("preview") || (admin && sub.equals("set"))) {
                return filter(new ArrayList<>(plugin.crates().ids()), args[1]);
            }
            if (admin && sub.equals("givekey")) {
                List<String> names = new ArrayList<>();
                for (Player online : Bukkit.getOnlinePlayers()) {
                    names.add(online.getName());
                }
                return filter(names, args[1]);
            }
        }

        if (args.length == 3 && admin && sub.equals("givekey")) {
            return filter(new ArrayList<>(plugin.crates().ids()), args[2]);
        }

        return new ArrayList<>();
    }

    private static List<String> filter(List<String> options, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
