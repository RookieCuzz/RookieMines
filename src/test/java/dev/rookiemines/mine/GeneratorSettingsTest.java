package dev.rookiemines.mine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratorSettingsTest {
    @Test
    void rejectsUnsafeReliefRanges() {
        assertThrows(IllegalArgumentException.class, () -> settings(5, 3, 0.42, 0.018, 14));
        assertThrows(IllegalArgumentException.class, () -> settings(2, 7, 0.42, 0.018, 14));
        assertThrows(IllegalArgumentException.class, () -> settings(2, 3, Double.NaN, 0.018, 14));
        assertThrows(IllegalArgumentException.class, () -> settings(2, 3, 0.42, Double.POSITIVE_INFINITY, 14));
        assertThrows(IllegalArgumentException.class, () -> settings(4, 3, 0.42, 0.018, 10));
        assertThrows(IllegalArgumentException.class, () -> settings(2, 3, 0.42, 0.018, 14, -0.01, 0.0));
        assertThrows(IllegalArgumentException.class, () -> settings(2, 3, 0.42, 0.018, 14, 0.0, Double.NaN));
    }

    @Test
    void shapingChangesInvalidateTheGenerationSeed() {
        GeneratorSettings flat = settings(0, 0, 0.0, 0.0, 14);
        long flatSeed = new FloorPlanGenerator(flat).describe(91L, 7L, 1).seed();
        List<GeneratorSettings> individualChanges = List.of(
                settings(2, 0, 0.0, 0.0, 14),
                settings(0, 3, 0.0, 0.0, 14),
                settings(0, 0, 0.42, 0.0, 14),
                settings(0, 0, 0.0, 0.018, 14),
                settings(0, 0, 0.0, 0.0, 14, 0.1, 0.0),
                settings(0, 0, 0.0, 0.0, 14, 0.0, 0.1)
        );
        for (GeneratorSettings changed : individualChanges) {
            long changedSeed = new FloorPlanGenerator(changed).describe(91L, 7L, 1).seed();
            assertNotEquals(flatSeed, changedSeed);
        }
    }

    private GeneratorSettings settings(
            int floorVariation,
            int ceilingVariation,
            double wallRoughness,
            double formationDensity,
            int height
    ) {
        return settings(floorVariation, ceilingVariation, wallRoughness, formationDensity, height, 0.0, 0.0);
    }

    private GeneratorSettings settings(
            int floorVariation,
            int ceilingVariation,
            double wallRoughness,
            double formationDensity,
            int height,
            double earthCobwebDensity,
            double skullCobwebDensity
    ) {
        return new GeneratorSettings(
                32, 40, height, 40, 64,
                0.15, 0.30, 0.0, 0, false, 0.0, 0.0,
                floorVariation, ceilingVariation, wallRoughness, formationDensity,
                earthCobwebDensity, skullCobwebDensity
        );
    }
}
