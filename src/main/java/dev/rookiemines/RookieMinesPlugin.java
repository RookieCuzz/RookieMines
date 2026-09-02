package dev.rookiemines;

import dev.rookiemines.mine.FloorManager;
import dev.rookiemines.mine.GeneratorSettings;
import dev.rookiemines.storage.FloorStateStore;
import dev.rookiemines.storage.PlayerProgressStore;
import dev.rookiemines.world.MineWorldService;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RookieMinesPlugin extends JavaPlugin {
    private FloorManager floorManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        GeneratorSettings settings;
        try {
            settings = loadSettings();
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Invalid generation settings: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        FloorStateStore floorStore = new FloorStateStore(this);
        PlayerProgressStore progressStore = new PlayerProgressStore(this);
        floorStore.load();
        progressStore.load();
        World mineWorld;
        try {
            mineWorld = new MineWorldService(this).createOrLoad();
        } catch (RuntimeException exception) {
            getLogger().severe("Could not initialize the mine world: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        floorManager = new FloorManager(this, mineWorld, settings, floorStore, progressStore);
        MineCommand commandHandler = new MineCommand(this, floorManager);
        PluginCommand mineCommand = getCommand("rmine");
        if (mineCommand == null) {
            throw new IllegalStateException("plugin.yml did not register /rmine");
        }
        mineCommand.setExecutor(commandHandler);
        mineCommand.setTabCompleter(commandHandler);
        getServer().getPluginManager().registerEvents(new MineListener(this, floorManager), this);
        getLogger().info("RookieMines (菜鸟矿洞) enabled for Paper 1.21.4; world=" + mineWorld.getName());
    }

    @Override
    public void onDisable() {
        if (floorManager != null) {
            floorManager.shutdown();
        }
    }

    public FloorManager floorManager() {
        return floorManager;
    }

    private GeneratorSettings loadSettings() {
        return new GeneratorSettings(
                getConfig().getInt("generation.min-size", 80),
                getConfig().getInt("generation.max-size", 120),
                getConfig().getInt("generation.height", 18),
                getConfig().getInt("world.base-y", 40),
                getConfig().getInt("world.floor-spacing", 160),
                getConfig().getDouble("generation.normal-dark-chance", 0.15),
                getConfig().getDouble("generation.skull-dark-chance", 0.30),
                getConfig().getDouble("generation.monster-density", 0.0035),
                getConfig().getInt("generation.max-monsters", 24),
                getConfig().getBoolean("generation.generate-monsters", true),
                getConfig().getDouble("generation.fishing-pool-chance", 0.35),
                getConfig().getDouble("generation.special-room-chance", 0.28)
        );
    }
}
