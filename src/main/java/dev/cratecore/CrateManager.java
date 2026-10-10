package dev.cratecore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Loads crates from config, remembers which blocks are crates, creates keys, rolls and pays rewards. */
final class CrateManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final CrateCore plugin;
    private final NamespacedKey keyTag;
    private final File locationsFile;
    private final Map<String, Crate> crates = new LinkedHashMap<>();
    /** "world;x;y;z" to crate id. */
    private final Map<String, String> locations = new HashMap<>();
    /** Players who are in the middle of opening a crate. Being in here is the "key lock". */
    private final Map<UUID, CrateSpin> spins = new HashMap<>();

    CrateManager(CrateCore plugin) {
        this.plugin = plugin;
        this.keyTag = new NamespacedKey(plugin, "crate_key");
        this.locationsFile = new File(plugin.getDataFolder(), "locations.yml");
        reload();
        loadLocations();
    }

    // ---------------------------------------------------------------- config

    void reload() {
        crates.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("crates");
        if (section == null) {
            return;
        }
        for (String rawId : section.getKeys(false)) {
            ConfigurationSection crateSection = section.getConfigurationSection(rawId);
            if (crateSection == null) {
                continue;
            }
            String id = rawId.toLowerCase(Locale.ROOT);
            Crate crate = parseCrate(id, crateSection);
            if (crate != null) {
                crates.put(id, crate);
            }
        }
    }

    private Crate parseCrate(String id, ConfigurationSection section) {
        String displayName = section.getString("display-name", "<white>" + id + " Crate");
        String keyName = section.getString("key.name", "<white>" + id + " Key");
        Material keyMaterial = parseMaterial(section.getString("key.material", "TRIPWIRE_HOOK"),
                Material.TRIPWIRE_HOOK, id + ".key.material");

        ConfigurationSection rewardSection = section.getConfigurationSection("rewards");
        List<Reward> rewards = new ArrayList<>();
        double total = 0.0;
        boolean hasMoney = false;

        if (rewardSection != null) {
            for (String rewardId : rewardSection.getKeys(false)) {
                ConfigurationSection r = rewardSection.getConfigurationSection(rewardId);
                if (r == null) {
                    continue;
                }
                double weight = r.getDouble("chance", 0.0);
                if (weight <= 0.0) {
                    plugin.getLogger().warning("Crate " + id + ", reward " + rewardId
                            + ": 'chance' must be above 0. Skipped.");
                    continue;
                }

                List<ItemStack> items = new ArrayList<>();
                for (String line : r.getStringList("items")) {
                    ItemStack stack = parseItem(line);
                    if (stack == null) {
                        plugin.getLogger().warning("Crate " + id + ", reward " + rewardId
                                + ": bad item '" + line + "'. Skipped.");
                    } else {
                        items.add(stack);
                    }
                }

                double money = r.getDouble("money", 0.0);
                if (money > 0.0) {
                    hasMoney = true;
                }

                // Only an explicit great-win in config.yml overrides the chance threshold.
                Boolean greatWin = null;
                if (r.contains("great-win", true)) {
                    greatWin = r.getBoolean("great-win");
                }

                rewards.add(new Reward(
                        rewardId,
                        weight,
                        r.getString("display-name", rewardId),
                        parseMaterial(r.getString("icon", "CHEST"), Material.CHEST, id + "." + rewardId + ".icon"),
                        money,
                        items,
                        r.getStringList("commands"),
                        r.getBoolean("broadcast", false),
                        greatWin));
                total += weight;
            }
        }

        if (rewards.isEmpty()) {
            plugin.getLogger().warning("Crate " + id + " has no valid rewards and was not loaded.");
            return null;
        }
        return new Crate(id, displayName, keyName, keyMaterial, rewards, total, hasMoney);
    }

    private Material parseMaterial(String name, Material fallback, String where) {
        Material material = Material.matchMaterial(name.trim());
        if (material == null || material.isAir() || !material.isItem()) {
            plugin.getLogger().warning("Unknown item '" + name + "' at " + where + ". Using " + fallback.name() + ".");
            return fallback;
        }
        return material;
    }

    /** "DIAMOND:8" or "DIAMOND" (amount 1). Returns null if the material is invalid. */
    private ItemStack parseItem(String line) {
        String name = line.trim();
        int amount = 1;
        int colon = name.lastIndexOf(':');
        if (colon > 0) {
            try {
                amount = Integer.parseInt(name.substring(colon + 1).trim());
                name = name.substring(0, colon).trim();
            } catch (NumberFormatException ex) {
                // No amount given, the colon belongs to a namespaced id like minecraft:diamond.
                amount = 1;
            }
        }
        Material material = Material.matchMaterial(name);
        if (material == null || material.isAir() || !material.isItem() || amount < 1) {
            return null;
        }
        return new ItemStack(material, amount);
    }

    Crate crate(String id) {
        return crates.get(id);
    }

    Collection<Crate> all() {
        return crates.values();
    }

    Set<String> ids() {
        return crates.keySet();
    }

    // ---------------------------------------------------------------- text helpers

    Component component(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    /** Like component(), but items don't show in italics unless the text asks for it. */
    Component italicOff(String miniMessage) {
        return MM.deserialize(miniMessage).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    // ---------------------------------------------------------------- keys

    ItemStack createKey(Crate crate, int amount) {
        ItemStack item = new ItemStack(crate.keyMaterial(), amount);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(italicOff(crate.keyName()));
        meta.lore(List.of(Component.text("Right-click the ", NamedTextColor.GRAY)
                .append(MM.deserialize(crate.displayName()))
                .append(Component.text(" to open it.", NamedTextColor.GRAY))
                .decoration(TextDecoration.ITALIC, false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, crate.id());

        item.setItemMeta(meta);
        return item;
    }

    /** The crate id a key item opens, or null if the item isn't a crate key. */
    String crateIdOfKey(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.STRING);
    }

    // ---------------------------------------------------------------- crate blocks

    private static String blockKey(Block block) {
        return block.getWorld().getName() + ";" + block.getX() + ";" + block.getY() + ";" + block.getZ();
    }

    /** The crate id placed on this block, or null. */
    String crateAt(Block block) {
        return locations.get(blockKey(block));
    }

    void addLocation(Block block, String crateId) {
        locations.put(blockKey(block), crateId);
        saveLocations();
    }

    boolean removeLocation(Block block) {
        boolean removed = locations.remove(blockKey(block)) != null;
        if (removed) {
            saveLocations();
        }
        return removed;
    }

    private void loadLocations() {
        locations.clear();
        if (!locationsFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(locationsFile);
        for (String line : yaml.getStringList("crates")) {
            int split = line.lastIndexOf(';');
            if (split > 0) {
                locations.put(line.substring(0, split), line.substring(split + 1));
            }
        }
    }

    private void saveLocations() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : locations.entrySet()) {
            lines.add(entry.getKey() + ";" + entry.getValue());
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("crates", lines);
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(locationsFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save locations.yml: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- spins (the key lock)

    boolean isSpinning(Player player) {
        return spins.containsKey(player.getUniqueId());
    }

    CrateSpin spinOf(Player player) {
        return spins.get(player.getUniqueId());
    }

    void addSpin(Player player, CrateSpin spin) {
        spins.put(player.getUniqueId(), spin);
    }

    void removeSpin(UUID id) {
        spins.remove(id);
    }

    /** On shutdown every running spin pays out immediately, so no key is lost. */
    void finishAllSpins() {
        for (CrateSpin spin : new ArrayList<>(spins.values())) {
            spin.forceFinish();
        }
        spins.clear();
    }

    // ---------------------------------------------------------------- rolling and paying

    /** Weighted random pick, as an index into crate.rewards(). */
    int rollIndex(Crate crate) {
        List<Reward> rewards = crate.rewards();
        double pick = ThreadLocalRandom.current().nextDouble() * crate.totalWeight();
        double running = 0.0;
        for (int i = 0; i < rewards.size(); i++) {
            running += rewards.get(i).weight();
            if (pick < running) {
                return i;
            }
        }
        return rewards.size() - 1;
    }

    /** Great wins get fireworks and extra sounds: explicit great-win in config, else a low enough chance. */
    boolean isGreatWin(Crate crate, Reward reward) {
        if (reward.greatWin() != null) {
            return reward.greatWin();
        }
        double threshold = plugin.getConfig().getDouble("animation.great-win-chance-percent", 5.0);
        return reward.weight() / crate.totalWeight() * 100.0 <= threshold;
    }

    /** The menu icon for a reward: name, chance and a star for great wins. */
    ItemStack rewardIcon(Crate crate, Reward reward) {
        ItemStack item = new ItemStack(reward.icon());
        ItemMeta meta = item.getItemMeta();

        double percent = reward.weight() / crate.totalWeight() * 100.0;

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Chance: ", NamedTextColor.GRAY)
                .append(Component.text(String.format(Locale.US, "%.1f%%", percent), NamedTextColor.YELLOW))
                .decoration(TextDecoration.ITALIC, false));
        if (isGreatWin(crate, reward)) {
            lore.add(Component.text("★ Great win", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.displayName(italicOff(reward.displayName()));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    void giveReward(Player player, Reward reward, Economy economy) {
        if (reward.money() > 0.0) {
            if (economy != null) {
                economy.depositPlayer(player, reward.money());
            } else {
                plugin.getLogger().severe("Could not pay " + reward.money() + " to " + player.getName()
                        + " (crate reward '" + reward.id() + "'): no economy is loaded. Pay them manually.");
            }
        }

        for (ItemStack stack : reward.items()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack.clone());
            for (ItemStack extra : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }

        for (String command : reward.commands()) {
            String run = command.replace("%player%", player.getName()).trim();
            if (run.startsWith("/")) {
                run = run.substring(1);
            }
            if (!run.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), run);
            }
        }
    }

    /**
     * Tells the player what they won and, if {@code fancy}, adds the title, sounds, particles and
     * fireworks. Anything scheduled is skipped when fancy is false (used on quit and shutdown).
     */
    void announce(Player player, Crate crate, Reward reward, Location center, boolean great, boolean fancy) {
        Messages messages = plugin.messages();
        Component crateName = component(crate.displayName());
        Component rewardName = component(reward.displayName());

        if (player.isOnline()) {
            messages.send(player, "won",
                    Placeholder.component("reward", rewardName),
                    Placeholder.component("crate", crateName));

            if (fancy) {
                Component heading = great ? messages.plain("great-win-title") : crateName;
                player.showTitle(Title.title(heading, rewardName, Title.Times.times(
                        Duration.ofMillis(200), Duration.ofMillis(2800), Duration.ofMillis(600))));
                plugin.effects().win(player, center, great);
            }
        }

        if (reward.broadcast()) {
            Bukkit.broadcast(messages.get("broadcast-win",
                    Placeholder.unparsed("player", player.getName()),
                    Placeholder.component("reward", rewardName),
                    Placeholder.component("crate", crateName)));
        }
    }
}
