package dev.rookiemines.storage;

import dev.rookiemines.mine.BlockPoint;
import dev.rookiemines.mine.FloorDescriptor;
import dev.rookiemines.mine.FloorState;
import dev.rookiemines.mine.MineTheme;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public final class FloorStateStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<Integer, FloorState> states = new HashMap<>();
    private long skullSession = 1L;

    public FloorStateStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "floors.yml");
    }

    public void load() {
        states.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        skullSession = Math.max(1L, yaml.getLong("skull-session", 1L));
        ConfigurationSection floors = yaml.getConfigurationSection("floors");
        if (floors == null) {
            return;
        }
        for (String key : floors.getKeys(false)) {
            try {
                int floor = Integer.parseInt(key);
                ConfigurationSection section = floors.getConfigurationSection(key);
                if (section == null) continue;
                FloorDescriptor descriptor = new FloorDescriptor(
                        floor,
                        section.getLong("generation-key"),
                        section.getLong("seed"),
                        MineTheme.valueOf(section.getString("theme", "EARTH")),
                        section.getBoolean("dark"),
                        section.getInt("size"),
                        section.getInt("canvas-size"),
                        section.getInt("height"),
                        section.getInt("base-y"),
                        section.getInt("center-z"),
                        section.getDouble("daily-luck")
                );
                FloorState state = new FloorState(
                        descriptor,
                        section.getInt("remaining-stones"),
                        section.getInt("enemy-count"),
                        section.getInt("generation-count", 1),
                        section.getString("layout-hash", "unknown")
                );
                state.setLadder(readPoint(section.getConfigurationSection("ladder")));
                state.setLadderTarget(
                        section.getInt("ladder-destination", floor + 1),
                        section.getBoolean("ladder-shaft", false)
                );
                state.setRewardChest(readPoint(section.getConfigurationSection("reward-chest")));
                states.put(floor, state);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid saved mine floor " + key, exception);
            }
        }
    }

    public FloorState get(int floor) {
        return states.get(floor);
    }

    public Map<Integer, FloorState> all() {
        return Map.copyOf(states);
    }

    public void put(int floor, FloorState state) {
        states.put(floor, state);
    }

    public void remove(int floor) {
        states.remove(floor);
    }

    public long skullSession() {
        return skullSession;
    }

    public long nextSkullSession() {
        skullSession++;
        save();
        return skullSession;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("skull-session", skullSession);
        for (Map.Entry<Integer, FloorState> entry : states.entrySet()) {
            String path = "floors." + entry.getKey();
            FloorState state = entry.getValue();
            FloorDescriptor descriptor = state.descriptor();
            yaml.set(path + ".generation-key", descriptor.generationKey());
            yaml.set(path + ".seed", descriptor.seed());
            yaml.set(path + ".theme", descriptor.theme().name());
            yaml.set(path + ".dark", descriptor.dark());
            yaml.set(path + ".size", descriptor.size());
            yaml.set(path + ".canvas-size", descriptor.canvasSize());
            yaml.set(path + ".height", descriptor.height());
            yaml.set(path + ".base-y", descriptor.baseY());
            yaml.set(path + ".center-z", descriptor.centerZ());
            yaml.set(path + ".daily-luck", descriptor.dailyLuck());
            yaml.set(path + ".remaining-stones", state.remainingStones());
            yaml.set(path + ".enemy-count", state.enemyCount());
            yaml.set(path + ".generation-count", state.generationCount());
            yaml.set(path + ".layout-hash", state.layoutHash());
            writePoint(yaml, path + ".ladder", state.ladder());
            yaml.set(path + ".ladder-destination", state.ladderDestination());
            yaml.set(path + ".ladder-shaft", state.ladderShaft());
            writePoint(yaml, path + ".reward-chest", state.rewardChest());
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data folder");
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save mine floor state", exception);
        }
    }

    private void writePoint(YamlConfiguration yaml, String path, BlockPoint point) {
        if (point == null) {
            yaml.set(path, null);
            return;
        }
        yaml.set(path + ".x", point.x());
        yaml.set(path + ".y", point.y());
        yaml.set(path + ".z", point.z());
    }

    private BlockPoint readPoint(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new BlockPoint(section.getInt("x"), section.getInt("y"), section.getInt("z"));
    }
}
