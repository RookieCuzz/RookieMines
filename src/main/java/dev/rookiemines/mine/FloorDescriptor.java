package dev.rookiemines.mine;

public record FloorDescriptor(
        int floor,
        long generationKey,
        long seed,
        MineTheme theme,
        boolean dark,
        int size,
        int canvasSize,
        int height,
        int baseY,
        int centerZ,
        double dailyLuck
) {
    public int originX() {
        return -(canvasSize / 2);
    }

    public int originZ() {
        return centerZ - (canvasSize / 2);
    }

    public BlockPoint spawnPoint() {
        return new BlockPoint(0, baseY + 2, centerZ);
    }

    public boolean contains(int x, int y, int z) {
        return x >= originX() && x < originX() + canvasSize
                && z >= originZ() && z < originZ() + canvasSize
                && y >= baseY && y < baseY + height;
    }
}
