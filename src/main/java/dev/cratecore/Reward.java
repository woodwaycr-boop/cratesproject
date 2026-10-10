package dev.cratecore;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * One possible prize in a crate. {@code weight} is relative to the other rewards in the same crate.
 * {@code greatWin} is null when config.yml doesn't say, so the chance threshold decides.
 */
record Reward(String id,
              double weight,
              String displayName,
              Material icon,
              double money,
              List<ItemStack> items,
              List<String> commands,
              boolean broadcast,
              Boolean greatWin) {
}
