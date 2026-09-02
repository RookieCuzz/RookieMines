package dev.rookiemines.mine;

public record GeneratorSettings(
        int minSize,
        int maxSize,
        int height,
        int baseY,
        int floorSpacing,
        double normalDarkChance,
        double skullDarkChance,
        double monsterDensity,
        int maxMonsters,
        boolean generateMonsters,
        double fishingPoolChance,
        double specialRoomChance
) {
    public GeneratorSettings {
        if (minSize < 24 || maxSize < minSize) {
            throw new IllegalArgumentException("Invalid floor size range");
        }
        if (height < 10 || height > 48) {
            throw new IllegalArgumentException("Height must be between 10 and 48");
        }
        if (floorSpacing <= maxSize + 8) {
            throw new IllegalArgumentException("Floor spacing must exceed max size by at least 8 blocks");
        }
    }
}
