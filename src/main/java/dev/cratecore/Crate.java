package dev.cratecore;

import org.bukkit.Material;

import java.util.List;

/** One crate type from config.yml. */
record Crate(String id,
             String displayName,
             String keyName,
             Material keyMaterial,
             List<Reward> rewards,
             double totalWeight,
             boolean hasMoneyReward) {
}
