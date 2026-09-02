package dev.rookiemines.mine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenerationRulesTest {
    @Test
    void mapsThemeBoundaries() {
        assertEquals(MineTheme.EARTH, GenerationRules.themeForFloor(1));
        assertEquals(MineTheme.EARTH, GenerationRules.themeForFloor(39));
        assertEquals(MineTheme.FROST, GenerationRules.themeForFloor(40));
        assertEquals(MineTheme.FROST, GenerationRules.themeForFloor(79));
        assertEquals(MineTheme.LAVA, GenerationRules.themeForFloor(80));
        assertEquals(MineTheme.LAVA, GenerationRules.themeForFloor(120));
        assertEquals(MineTheme.LOBBY, GenerationRules.themeForFloor(121));
        assertEquals(MineTheme.SKULL, GenerationRules.themeForFloor(122));
        assertThrows(IllegalArgumentException.class, () -> GenerationRules.themeForFloor(0));
    }

    @Test
    void floorSeedIsStableAndDaySensitive() {
        long first = GenerationRules.floorSeed(1234L, 77L, 40);
        assertEquals(first, GenerationRules.floorSeed(1234L, 77L, 40));
        assertNotEquals(first, GenerationRules.floorSeed(1234L, 78L, 40));
        assertNotEquals(first, GenerationRules.floorSeed(1234L, 77L, 41));
    }

    @Test
    void ladderChanceMatchesRulesAndClamps() {
        double chance = GenerationRules.ladderChance(0.02, 10, 2, 0.05, true, 0.04);
        assertEquals(0.19, chance, 1.0e-9);
        assertEquals(1.0, GenerationRules.ladderChance(0.02, 0, 0, -0.1, false, 0.04));
        assertEquals(0.0, GenerationRules.ladderChance(0.0, 1000, 0, -10.0, false, 0.0));
    }
}
