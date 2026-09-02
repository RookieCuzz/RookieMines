package dev.rookiemines.world;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

public final class MineWorldService {
    private final JavaPlugin plugin;

    public MineWorldService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public World createOrLoad() {
        String name = plugin.getConfig().getString("world.name", "rookie_mines");
        long seed = plugin.getConfig().getLong("world.seed", 731992431237L);
        WorldCreator creator = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .seed(seed)
                .generateStructures(false)
                .generator(new VoidChunkGenerator());
        World world = creator.createWorld();
        if (world == null) {
            throw new IllegalStateException("Paper could not create mine world " + name);
        }
        world.setAutoSave(true);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setTime(18000L);
        return world;
    }
}
