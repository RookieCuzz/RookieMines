package dev.rookiemines.mine;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloorPlanGeneratorTest {
    private FloorPlanGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new FloorPlanGenerator(new GeneratorSettings(
                32, 40, 12, 40, 64,
                0.15, 0.30, 0.0, 0, false, 0.0, 0.0
        ));
    }

    @Test
    void generationIsDeterministicForSameFloorAndDay() {
        FloorDescriptor firstDescriptor = generator.describe(991L, 12L, 40);
        FloorDescriptor secondDescriptor = generator.describe(991L, 12L, 40);
        FloorPlan first = generator.generate(firstDescriptor);
        FloorPlan second = generator.generate(secondDescriptor);
        assertEquals(firstDescriptor, secondDescriptor);
        assertEquals(first.layoutHash(), second.layoutHash());
        assertEquals(first.initialStones(), second.initialStones());
    }

    @Test
    void newDayProducesDifferentLayout() {
        FloorPlan first = generator.generate(generator.describe(991L, 12L, 40));
        FloorPlan second = generator.generate(generator.describe(991L, 13L, 40));
        assertNotEquals(first.layoutHash(), second.layoutHash());
    }

    @Test
    void entranceIsSafeAndShellIsSealed() {
        FloorPlan plan = generator.generate(generator.describe(17L, 2L, 1));
        int center = plan.descriptor().canvasSize() / 2;
        assertEquals(Material.SMOOTH_STONE, plan.getLocal(center, 1, center));
        assertEquals(Material.AIR, plan.getLocal(center, 2, center));
        assertEquals(Material.AIR, plan.getLocal(center, 3, center));

        int roomStart = (plan.descriptor().canvasSize() - plan.descriptor().size()) / 2;
        int roomEnd = roomStart + plan.descriptor().size() - 1;
        assertEquals(Material.BEDROCK, plan.getLocal(roomStart, 2, roomStart));
        assertEquals(Material.BEDROCK, plan.getLocal(roomEnd, 2, roomEnd));
        assertTrue(plan.initialStones() > 0);
    }

    @Test
    void orePalettesFollowTheme() {
        FloorPlan earth = generator.generate(generator.describe(22L, 1L, 1));
        FloorPlan frost = generator.generate(generator.describe(22L, 1L, 40));
        FloorPlan lava = generator.generate(generator.describe(22L, 1L, 80));

        assertTrue(containsAny(earth, EnumSet.of(Material.COPPER_ORE, Material.COAL_ORE, Material.AMETHYST_BLOCK)));
        assertFalse(containsAny(earth, EnumSet.of(Material.IRON_ORE, Material.GOLD_ORE)));
        assertTrue(containsAny(frost, EnumSet.of(Material.IRON_ORE, Material.LAPIS_ORE, Material.DIAMOND_ORE)));
        assertTrue(containsAny(lava, EnumSet.of(Material.GOLD_ORE, Material.REDSTONE_ORE, Material.DIAMOND_ORE)));
    }

    @Test
    void terminalRewardAndLobbyFloorsHaveFixedFixtures() {
        FloorPlan reward = generator.generate(generator.describe(7L, 3L, 120));
        assertTrue(reward.chests().stream().anyMatch(chest -> chest.kind() == ChestSpec.ChestKind.REWARD));
        assertTrue(reward.mobs().isEmpty());
        int rewardCenter = reward.descriptor().canvasSize() / 2;
        assertEquals(Material.GILDED_BLACKSTONE, reward.getLocal(rewardCenter, 1, rewardCenter));

        FloorPlan lobby = generator.generate(generator.describe(7L, 0L, 121));
        assertEquals(MineTheme.LOBBY, lobby.descriptor().theme());
        assertTrue(lobby.prebuiltLadder() != null);
        int center = lobby.descriptor().canvasSize() / 2;
        assertEquals(Material.CUT_SANDSTONE, lobby.getLocal(center, 1, center));
        assertEquals(Material.AIR, lobby.getLocal(center, 2, center));
    }

    @Test
    void configuredSpecialRoomCreatesSkullTreasure() {
        FloorPlanGenerator specialGenerator = new FloorPlanGenerator(new GeneratorSettings(
                32, 40, 12, 40, 64,
                0.15, 0.30, 0.0, 0, false, 0.0, 1.0
        ));
        FloorPlan skull = specialGenerator.generate(specialGenerator.describe(77L, 5L, 122));
        assertTrue(skull.chests().stream()
                .anyMatch(chest -> chest.kind() == ChestSpec.ChestKind.SKULL_TREASURE));
    }

    private boolean containsAny(FloorPlan plan, Set<Material> materials) {
        for (int i = 0; i < plan.volume(); i++) {
            if (materials.contains(plan.materialAtIndex(i))) return true;
        }
        return false;
    }
}
