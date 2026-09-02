package dev.rookiemines.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class PlayerProgressStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Integer> deepest = new HashMap<>();
    private final Map<UUID, Set<Integer>> claimedRewards = new HashMap<>();

    public PlayerProgressStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public void load() {
        deepest.clear();
        claimedRewards.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                deepest.put(uuid, players.getInt(key + ".deepest", 0));
                claimedRewards.put(uuid, new HashSet<>(players.getIntegerList(key + ".claimed-rewards")));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Skipping invalid player progress " + key, exception);
            }
        }
    }

    public int deepest(UUID player) {
        return deepest.getOrDefault(player, 0);
    }

    public void reach(UUID player, int floor) {
        if (floor >= 1 && floor <= 120) {
            deepest.merge(player, floor, Math::max);
        }
    }

    public int deepestElevator(UUID player) {
        return (deepest(player) / 5) * 5;
    }

    public boolean hasClaimed(UUID player, int floor) {
        return claimedRewards.getOrDefault(player, Set.of()).contains(floor);
    }

    public void claim(UUID player, int floor) {
        claimedRewards.computeIfAbsent(player, ignored -> new HashSet<>()).add(floor);
        save();
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        Set<UUID> players = new HashSet<>(deepest.keySet());
        players.addAll(claimedRewards.keySet());
        for (UUID uuid : players) {
            String path = "players." + uuid;
            yaml.set(path + ".deepest", deepest(uuid));
            yaml.set(path + ".claimed-rewards", claimedRewards.getOrDefault(uuid, Set.of()).stream().sorted().toList());
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create plugin data folder");
            }
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player progress", exception);
        }
    }
}
