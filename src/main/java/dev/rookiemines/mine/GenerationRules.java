package dev.rookiemines.mine;

import java.util.Set;

public final class GenerationRules {
    public static final int GENERATION_VERSION = 1;
    public static final int SKULL_LOBBY_FLOOR = 121;
    public static final int FIRST_SKULL_FLOOR = 122;
    private static final Set<Integer> REWARD_FLOORS = Set.of(
            10, 20, 40, 50, 60, 70, 80, 90, 100, 110, 120
    );

    private GenerationRules() {
    }

    public static MineTheme themeForFloor(int floor) {
        if (floor <= 0) {
            throw new IllegalArgumentException("Floor must be positive");
        }
        if (floor < 40) {
            return MineTheme.EARTH;
        }
        if (floor < 80) {
            return MineTheme.FROST;
        }
        if (floor <= 120) {
            return MineTheme.LAVA;
        }
        if (floor == SKULL_LOBBY_FLOOR) {
            return MineTheme.LOBBY;
        }
        return MineTheme.SKULL;
    }

    public static boolean isNormalMine(int floor) {
        return floor >= 1 && floor <= 120;
    }

    public static boolean isSkullFloor(int floor) {
        return floor >= FIRST_SKULL_FLOOR;
    }

    public static boolean isRewardFloor(int floor) {
        return REWARD_FLOORS.contains(floor);
    }

    public static long floorSeed(long worldSeed, long generationKey, int floor) {
        long value = worldSeed;
        value ^= mix64(generationKey + 0x9E3779B97F4A7C15L);
        value ^= mix64(((long) floor << 32) ^ GENERATION_VERSION);
        return mix64(value);
    }

    public static double dailyLuck(long worldSeed, long day) {
        long bits = mix64(worldSeed ^ (day * 0xD1B54A32D192ED03L));
        double unit = (bits >>> 11) * 0x1.0p-53;
        return -0.10 + unit * 0.20;
    }

    public static double ladderChance(
            double baseChance,
            int remainingStones,
            int luckAmplifier,
            double dailyLuck,
            boolean enemiesCleared,
            double noEnemiesBonus
    ) {
        if (remainingStones <= 0) {
            return 1.0;
        }
        double chance = baseChance
                + 1.0 / Math.max(1, remainingStones)
                + Math.max(0, luckAmplifier) / 100.0
                + dailyLuck / 5.0;
        if (enemiesCleared) {
            chance += noEnemiesBonus;
        }
        return Math.max(0.0, Math.min(1.0, chance));
    }

    public static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
