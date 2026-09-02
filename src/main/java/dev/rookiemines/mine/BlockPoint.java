package dev.rookiemines.mine;

import org.bukkit.Location;
import org.bukkit.World;

public record BlockPoint(int x, int y, int z) {
    public Location center(World world) {
        return new Location(world, x + 0.5, y, z + 0.5);
    }
}
