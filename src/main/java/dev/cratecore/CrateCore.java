package dev.cratecore;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class CrateCore extends JavaPlugin {

    private Messages messages;
    private Effects effects;
    private CrateManager crates;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        messages = new Messages(this);
        effects = new Effects(this);
        crates = new CrateManager(this);

        getServer().getPluginManager().registerEvents(new CrateListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        CrateCommand command = new CrateCommand(this);
        getCommand("crate").setExecutor(command);
        getCommand("crate").setTabCompleter(command);

        getLogger().info("Loaded " + crates.all().size() + " crate(s).");
    }

    @Override
    public void onDisable() {
        // Anyone mid-spin gets their reward now instead of losing the key.
        if (crates != null) {
            crates.finishAllSpins();
        }
    }

    /** Looked up on every use so load order between plugins doesn't matter. Null if none is registered. */
    Economy economy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    Messages messages() {
        return messages;
    }

    Effects effects() {
        return effects;
    }

    CrateManager crates() {
        return crates;
    }
}
