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
        double specialRoomChance,
        int floorVariation,
        int ceilingVariation,
        double wallRoughness,
        double formationDensity,
        double earthCobwebDensity,
        double skullCobwebDensity
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
        if (floorVariation < 0 || floorVariation > 4) {
            throw new IllegalArgumentException("Floor variation must be between 0 and 4 blocks");
        }
        if (ceilingVariation < 0 || ceilingVariation > 6) {
            throw new IllegalArgumentException("Ceiling variation must be between 0 and 6 blocks");
        }
        if (height < floorVariation + 7) {
            throw new IllegalArgumentException("Height must be at least floor variation + 7 blocks");
        }
        if (!isProbability(wallRoughness)) {
            throw new IllegalArgumentException("Wall roughness must be between 0.0 and 1.0");
        }
        if (!isProbability(formationDensity)) {
            throw new IllegalArgumentException("Formation density must be between 0.0 and 1.0");
        }
        if (!isProbability(earthCobwebDensity)) {
            throw new IllegalArgumentException("Earth cobweb density must be between 0.0 and 1.0");
        }
        if (!isProbability(skullCobwebDensity)) {
            throw new IllegalArgumentException("Skull cobweb density must be between 0.0 and 1.0");
        }
    }

    private static boolean isProbability(double value) {
        return Double.isFinite(value) && value >= 0.0 && value <= 1.0;
    }

    public long generationSalt() {
        long value = 0x4D494E4553484150L;
        value = mix(value, minSize);
        value = mix(value, maxSize);
        value = mix(value, height);
        value = mix(value, baseY);
        value = mix(value, floorSpacing);
        value = mix(value, Double.doubleToLongBits(normalDarkChance));
        value = mix(value, Double.doubleToLongBits(skullDarkChance));
        value = mix(value, Double.doubleToLongBits(monsterDensity));
        value = mix(value, maxMonsters);
        value = mix(value, generateMonsters ? 1 : 0);
        value = mix(value, Double.doubleToLongBits(fishingPoolChance));
        value = mix(value, Double.doubleToLongBits(specialRoomChance));
        value = mix(value, floorVariation);
        value = mix(value, ceilingVariation);
        value = mix(value, Double.doubleToLongBits(wallRoughness));
        value = mix(value, Double.doubleToLongBits(formationDensity));
        value = mix(value, Double.doubleToLongBits(earthCobwebDensity));
        return mix(value, Double.doubleToLongBits(skullCobwebDensity));
    }

    private static long mix(long current, long next) {
        return GenerationRules.mix64(current ^ GenerationRules.mix64(next));
    }
}
