package dev.rookiemines.mine;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;
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
                0.15, 0.30, 0.0, 0, false, 0.0, 0.0,
                2, 3, 0.42, 0.018, 0.0, 0.0
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
        for (int coordinate = roomStart; coordinate <= roomEnd; coordinate++) {
            for (int y = 0; y < plan.descriptor().height(); y++) {
                assertEquals(Material.BEDROCK, plan.getLocal(roomStart, y, coordinate));
                assertEquals(Material.BEDROCK, plan.getLocal(roomEnd, y, coordinate));
                assertEquals(Material.BEDROCK, plan.getLocal(coordinate, y, roomStart));
                assertEquals(Material.BEDROCK, plan.getLocal(coordinate, y, roomEnd));
            }
        }
        for (int x = roomStart; x <= roomEnd; x++) {
            for (int z = roomStart; z <= roomEnd; z++) {
                assertEquals(Material.BEDROCK, plan.getLocal(x, 0, z));
                assertEquals(Material.BEDROCK, plan.getLocal(x, plan.descriptor().height() - 1, z));
            }
        }
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
    void cobwebDecorationsFollowThemeAndPreserveThreeBlockPathClearance() {
        FloorPlanGenerator cobwebGenerator = new FloorPlanGenerator(new GeneratorSettings(
                64, 64, 14, 40, 80,
                0.15, 0.30, 0.0, 0, false, 0.0, 1.0,
                2, 3, 0.42, 0.018, 1.0, 1.0
        ));
        FloorDescriptor earthDescriptor = cobwebGenerator.describe(221L, 4L, 1);
        FloorPlan earth = cobwebGenerator.generate(earthDescriptor);
        FloorPlan repeatedEarth = cobwebGenerator.generate(earthDescriptor);
        FloorPlan skull = cobwebGenerator.generate(cobwebGenerator.describe(221L, 4L, 122));
        FloorPlan frost = cobwebGenerator.generate(cobwebGenerator.describe(221L, 4L, 40));
        FloorPlan lava = cobwebGenerator.generate(cobwebGenerator.describe(221L, 4L, 80));
        FloorPlan reward = cobwebGenerator.generate(cobwebGenerator.describe(221L, 4L, 120));
        FloorPlan lobby = cobwebGenerator.generate(cobwebGenerator.describe(221L, 4L, 121));

        int earthCobwebs = countMaterial(earth, Material.COBWEB);
        int skullCobwebs = countMaterial(skull, Material.COBWEB);
        assertTrue(earthCobwebs > 0, "Earth caves should receive sparse cobweb decoration");
        assertTrue(skullCobwebs > earthCobwebs, "Skull caves should receive denser cobweb decoration");
        assertEquals(earth.layoutHash(), repeatedEarth.layoutHash(), "Cobweb positions must be deterministic");
        assertEquals(0, countMaterial(frost, Material.COBWEB));
        assertEquals(0, countMaterial(lava, Material.COBWEB));
        assertEquals(0, countMaterial(reward, Material.COBWEB));
        assertEquals(0, countMaterial(lobby, Material.COBWEB));
        assertCobwebSafety(earth);
        assertCobwebSafety(skull);
    }

    @Test
    void zeroCobwebDensityDisablesCobwebDecoration() {
        FloorPlan earth = generator.generate(generator.describe(221L, 4L, 1));
        FloorPlan skull = generator.generate(generator.describe(221L, 4L, 122));
        assertEquals(0, countMaterial(earth, Material.COBWEB));
        assertEquals(0, countMaterial(skull, Material.COBWEB));
    }

    @Test
    void compactE2eFloorAlwaysHasCobwebCandidatesAtFullDensity() {
        FloorPlanGenerator compactGenerator = new FloorPlanGenerator(new GeneratorSettings(
                32, 40, 12, 40, 64,
                0.15, 0.30, 0.0, 0, false, 0.0, 0.0,
                2, 3, 0.42, 0.018, 1.0, 1.0
        ));
        FloorPlan earth = compactGenerator.generate(
                compactGenerator.describe(731992431237L, 1001L, 1)
        );
        assertTrue(countMaterial(earth, Material.COBWEB) > 0,
                "The compact Mineflayer fixture must expose generated cobwebs");
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
                60, 60, 14, 40, 80,
                0.15, 0.30, 0.0, 0, false, 0.0, 1.0,
                4, 3, 0.42, 0.018, 0.0, 0.0
        ));
        for (long seed = 1; seed <= 32; seed++) {
            FloorPlan skull = specialGenerator.generate(specialGenerator.describe(seed, 5L, 122));
            ChestSpec treasure = skull.chests().stream()
                    .filter(chest -> chest.kind() == ChestSpec.ChestKind.SKULL_TREASURE)
                    .findFirst()
                    .orElseThrow();
            int localX = treasure.point().x() - skull.descriptor().originX();
            int localY = treasure.point().y() - skull.descriptor().baseY();
            int localZ = treasure.point().z() - skull.descriptor().originZ();
            assertNotEquals(Material.AIR, skull.getLocal(localX, localY - 1, localZ));
            assertEquals(Material.AIR, skull.getLocal(localX, localY + 1, localZ));
            assertTrue(canReachAdjacent(skull, localX, localZ),
                    "Special room must connect to the entrance for seed " + seed);
        }
    }

    @Test
    void themePoolIsSupportedAndKeepsHeadroomAcrossRelief() {
        FloorPlanGenerator poolGenerator = new FloorPlanGenerator(new GeneratorSettings(
                60, 60, 14, 40, 80,
                0.15, 0.30, 0.0, 0, false, 1.0, 0.0,
                2, 3, 0.42, 0.08, 0.0, 0.0
        ));
        FloorPlan plan = poolGenerator.generate(poolGenerator.describe(818L, 6L, 1));
        int liquidBlocks = 0;
        for (int x = 0; x < plan.descriptor().canvasSize(); x++) {
            for (int z = 0; z < plan.descriptor().canvasSize(); z++) {
                for (int y = 1; y < plan.descriptor().height() - 1; y++) {
                    if (plan.getLocal(x, y, z) != Material.WATER) continue;
                    liquidBlocks++;
                    assertNotEquals(Material.AIR, plan.getLocal(x, y - 1, z));
                    assertEquals(Material.AIR, plan.getLocal(x, y + 1, z));
                }
            }
        }
        assertTrue(liquidBlocks >= 5, "Configured pool should contain a visible liquid surface");
    }

    @Test
    void naturalCaveProfileHasWalkableFloorAndCeilingRelief() {
        FloorPlanGenerator reliefGenerator = new FloorPlanGenerator(new GeneratorSettings(
                40, 40, 14, 40, 64,
                0.15, 0.30, 0.0, 0, false, 0.0, 0.0,
                2, 3, 0.0, 0.0, 0.0, 0.0
        ));
        FloorPlan plan = reliefGenerator.generate(reliefGenerator.describe(441L, 9L, 18));
        int[][] surfaces = new int[plan.descriptor().canvasSize()][plan.descriptor().canvasSize()];
        Set<Integer> floorLevels = new HashSet<>();
        Set<Integer> ceilingLevels = new HashSet<>();
        int center = plan.descriptor().canvasSize() / 2;
        int walkableColumns = 0;

        for (int[] row : surfaces) java.util.Arrays.fill(row, -1);
        for (int x = 1; x < plan.descriptor().canvasSize() - 1; x++) {
            for (int z = 1; z < plan.descriptor().canvasSize() - 1; z++) {
                int floorY = findWalkableSurface(plan, x, z);
                surfaces[x][z] = floorY;
                if (floorY < 0) continue;
                int ceilingY = findCeiling(plan, x, z, floorY);
                assertTrue(ceilingY - floorY >= 4, "Every walkable column keeps three air blocks");
                assertTrue(ceilingY <= plan.descriptor().height() - 2, "Ceiling stays inside the shell");
                walkableColumns++;
                if (distanceSquared(x, z, center, center) > 7 * 7) {
                    floorLevels.add(floorY);
                    ceilingLevels.add(ceilingY);
                }
            }
        }

        assertTrue(walkableColumns > 150, "Cave should expose a useful walkable area");
        assertTrue(floorLevels.size() >= 2, "Floor should use multiple elevations");
        assertTrue(ceilingLevels.size() >= 2, "Ceiling should use multiple elevations");
        for (int x = 1; x < surfaces.length - 1; x++) {
            for (int z = 1; z < surfaces.length - 1; z++) {
                if (surfaces[x][z] < 0) continue;
                assertStepHeight(surfaces[x][z], surfaces[x + 1][z]);
                assertStepHeight(surfaces[x][z], surfaces[x][z + 1]);
            }
        }
        assertEquals(1, surfaces[center][center], "Safe-zone floor remains fixed");
        assertTrue(reachableRatio(surfaces, center, center) >= 0.95,
                "At least 95% of standable columns stay connected to the entrance");
    }

    @Test
    void oddRoomSizeKeepsEntrancePlatformStepable() {
        FloorPlanGenerator oddGenerator = new FloorPlanGenerator(new GeneratorSettings(
                39, 40, 14, 40, 64,
                0.15, 0.30, 0.0, 0, false, 0.0, 0.0,
                4, 3, 0.0, 0.0, 0.0, 0.0
        ));
        boolean exercisedOddRoom = false;
        for (long seed = 1; seed <= 32; seed++) {
            FloorPlan plan = oddGenerator.generate(oddGenerator.describe(seed, 9L, 18));
            if (plan.descriptor().size() % 2 == 0) continue;
            exercisedOddRoom = true;
            int center = plan.descriptor().canvasSize() / 2;
            for (int x = center - 5; x <= center + 5; x++) {
                for (int z = center - 5; z <= center + 5; z++) {
                    int floorY = findWalkableSurface(plan, x, z);
                    assertTrue(floorY >= 0, "Entrance perimeter must stay walkable");
                    if (x < center + 5) assertStepHeight(floorY, findWalkableSurface(plan, x + 1, z));
                    if (z < center + 5) assertStepHeight(floorY, findWalkableSurface(plan, x, z + 1));
                }
            }
        }
        assertTrue(exercisedOddRoom, "Seed sample should cover an odd-sized cave inside an even canvas");
    }

    @Test
    void zeroReliefSettingsRetainFlatCaveSurfaces() {
        FloorPlanGenerator flatGenerator = new FloorPlanGenerator(new GeneratorSettings(
                40, 40, 14, 40, 64,
                0.15, 0.30, 0.0, 0, false, 0.0, 0.0,
                0, 0, 0.0, 0.0, 0.0, 0.0
        ));
        FloorPlan plan = flatGenerator.generate(flatGenerator.describe(441L, 9L, 18));
        Set<Integer> floorLevels = new HashSet<>();
        Set<Integer> ceilingLevels = new HashSet<>();
        for (int x = 1; x < plan.descriptor().canvasSize() - 1; x++) {
            for (int z = 1; z < plan.descriptor().canvasSize() - 1; z++) {
                int floorY = findWalkableSurface(plan, x, z);
                if (floorY < 0) continue;
                floorLevels.add(floorY);
                ceilingLevels.add(findCeiling(plan, x, z, floorY));
            }
        }
        assertEquals(Set.of(1), floorLevels);
        assertEquals(Set.of(8), ceilingLevels);
    }

    @Test
    void wallRoughnessCarvesSeededThreeDimensionalRecesses() {
        GeneratorSettings flatSettings = new GeneratorSettings(
                40, 40, 14, 40, 64,
                0.15, 0.30, 0.0, 0, false, 0.0, 0.0,
                0, 0, 0.0, 0.0, 0.0, 0.0
        );
        GeneratorSettings roughSettings = new GeneratorSettings(
                40, 40, 14, 40, 64,
                0.15, 0.30, 0.0, 0, false, 0.0, 0.0,
                0, 0, 1.0, 0.0, 0.0, 0.0
        );
        FloorDescriptor descriptor = new FloorPlanGenerator(flatSettings).describe(715L, 4L, 33);
        FloorPlan flat = new FloorPlanGenerator(flatSettings).generate(descriptor);
        FloorPlan rough = new FloorPlanGenerator(roughSettings).generate(descriptor);
        int carved = 0;
        for (int x = 1; x < descriptor.canvasSize() - 1; x++) {
            for (int z = 1; z < descriptor.canvasSize() - 1; z++) {
                for (int y = 2; y < descriptor.height() - 2; y++) {
                    if (flat.getLocal(x, y, z) != Material.AIR
                            && rough.getLocal(x, y, z) == Material.AIR) {
                        carved++;
                    }
                }
            }
        }
        assertTrue(carved > 20, "Full wall roughness should create visible recessed wall blocks");
    }

    @Test
    void formationDensityAddsVisibleCaveProtrusions() {
        GeneratorSettings bareSettings = new GeneratorSettings(
                48, 48, 14, 40, 64,
                0.15, 0.30, 0.0, 0, false, 0.0, 0.0,
                2, 3, 0.0, 0.0, 0.0, 0.0
        );
        GeneratorSettings formationSettings = new GeneratorSettings(
                48, 48, 14, 40, 64,
                0.15, 0.30, 0.0, 0, false, 0.0, 0.0,
                2, 3, 0.0, 1.0, 0.0, 0.0
        );
        FloorDescriptor descriptor = new FloorPlanGenerator(bareSettings).describe(992L, 11L, 1);
        FloorPlan bare = new FloorPlanGenerator(bareSettings).generate(descriptor);
        FloorPlan formations = new FloorPlanGenerator(formationSettings).generate(descriptor);
        int protrusions = 0;
        for (int x = 1; x < descriptor.canvasSize() - 1; x++) {
            for (int z = 1; z < descriptor.canvasSize() - 1; z++) {
                for (int y = 2; y < descriptor.height() - 2; y++) {
                    if (bare.getLocal(x, y, z) == Material.AIR
                            && formations.getLocal(x, y, z) != Material.AIR) {
                        protrusions++;
                    }
                }
            }
        }
        assertTrue(protrusions > 40, "Full formation density should add visible floor or ceiling rock");
    }

    private int findWalkableSurface(FloorPlan plan, int x, int z) {
        for (int y = 1; y < plan.descriptor().height() - 3; y++) {
            if (isSupport(plan.getLocal(x, y, z))
                    && isPassable(plan.getLocal(x, y + 1, z))
                    && isPassable(plan.getLocal(x, y + 2, z))
                    && isPassable(plan.getLocal(x, y + 3, z))) {
                return y;
            }
        }
        return -1;
    }

    private int findCeiling(FloorPlan plan, int x, int z, int floorY) {
        for (int y = floorY + 4; y < plan.descriptor().height(); y++) {
            if (!isPassable(plan.getLocal(x, y, z))) return y;
        }
        return plan.descriptor().height();
    }

    private boolean isSupport(Material material) {
        return switch (material) {
            case AIR, COBWEB, TORCH, SOUL_TORCH, WATER, LAVA, CHEST, BARREL, LANTERN, SOUL_LANTERN -> false;
            default -> true;
        };
    }

    private boolean isPassable(Material material) {
        return material == Material.AIR || material == Material.TORCH || material == Material.SOUL_TORCH;
    }

    private void assertStepHeight(int first, int second) {
        if (second >= 0) assertTrue(Math.abs(first - second) <= 1, "Adjacent floors must remain stepable");
    }

    private double reachableRatio(int[][] surfaces, int startX, int startZ) {
        boolean[][] visited = new boolean[surfaces.length][surfaces.length];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startZ});
        visited[startX][startZ] = true;
        int reached = 0;
        int total = 0;
        for (int[] row : surfaces) {
            for (int surface : row) {
                if (surface >= 0) total++;
            }
        }
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] current = queue.removeFirst();
            reached++;
            for (int[] direction : directions) {
                int x = current[0] + direction[0];
                int z = current[1] + direction[1];
                if (x < 0 || x >= surfaces.length || z < 0 || z >= surfaces.length
                        || visited[x][z] || surfaces[x][z] < 0
                        || Math.abs(surfaces[current[0]][current[1]] - surfaces[x][z]) > 1) {
                    continue;
                }
                visited[x][z] = true;
                queue.addLast(new int[]{x, z});
            }
        }
        return total == 0 ? 0.0 : reached / (double) total;
    }

    private boolean canReachAdjacent(FloorPlan plan, int targetX, int targetZ) {
        int size = plan.descriptor().canvasSize();
        int[][] surfaces = new int[size][size];
        for (int x = 0; x < size; x++) {
            java.util.Arrays.fill(surfaces[x], -1);
            for (int z = 0; z < size; z++) {
                surfaces[x][z] = findWalkableSurface(plan, x, z);
            }
        }
        int center = size / 2;
        boolean[][] visited = new boolean[size][size];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{center, center});
        visited[center][center] = true;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] current = queue.removeFirst();
            if (Math.abs(current[0] - targetX) + Math.abs(current[1] - targetZ) == 1) return true;
            for (int[] direction : directions) {
                int x = current[0] + direction[0];
                int z = current[1] + direction[1];
                if (x < 0 || x >= size || z < 0 || z >= size
                        || visited[x][z] || surfaces[x][z] < 0
                        || Math.abs(surfaces[current[0]][current[1]] - surfaces[x][z]) > 1) {
                    continue;
                }
                visited[x][z] = true;
                queue.addLast(new int[]{x, z});
            }
        }
        return false;
    }

    private int distanceSquared(int x1, int z1, int x2, int z2) {
        int dx = x1 - x2;
        int dz = z1 - z2;
        return dx * dx + dz * dz;
    }

    private boolean containsAny(FloorPlan plan, Set<Material> materials) {
        for (int i = 0; i < plan.volume(); i++) {
            if (materials.contains(plan.materialAtIndex(i))) return true;
        }
        return false;
    }

    private int countMaterial(FloorPlan plan, Material material) {
        int count = 0;
        for (int index = 0; index < plan.volume(); index++) {
            if (plan.materialAtIndex(index) == material) count++;
        }
        return count;
    }

    private void assertCobwebSafety(FloorPlan plan) {
        int center = plan.descriptor().canvasSize() / 2;
        int cobwebs = 0;
        for (int x = 1; x < plan.descriptor().canvasSize() - 1; x++) {
            for (int z = 1; z < plan.descriptor().canvasSize() - 1; z++) {
                for (int y = 2; y < plan.descriptor().height() - 1; y++) {
                    if (plan.getLocal(x, y, z) != Material.COBWEB) continue;
                    cobwebs++;
                    assertTrue(distanceSquared(x, z, center, center) >= 12 * 12,
                            "Cobwebs must stay outside the entrance safe zone");

                    int supportY = y - 1;
                    while (supportY >= 1 && !isSupport(plan.getLocal(x, supportY, z))) {
                        supportY--;
                    }
                    assertTrue(supportY >= 1, "Cobweb column must have a floor");
                    assertTrue(y - supportY >= 4, "Cobweb must leave three clear blocks over the floor");
                    for (int clearY = supportY + 1; clearY <= supportY + 3; clearY++) {
                        assertEquals(Material.AIR, plan.getLocal(x, clearY, z),
                                "Cobweb must not occupy walking or jumping clearance");
                    }

                    int solidNeighbors = 0;
                    int airNeighbors = 0;
                    for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        Material neighbor = plan.getLocal(x + direction[0], y, z + direction[1]);
                        if (isSupport(neighbor)) solidNeighbors++;
                        if (neighbor == Material.AIR) airNeighbors++;
                    }
                    assertTrue(solidNeighbors >= 1, "Cobweb must touch a solid wall");
                    assertTrue(airNeighbors >= 1, "Cobweb must remain visible from open cave space");
                    for (ChestSpec chest : plan.chests()) {
                        int chestX = chest.point().x() - plan.descriptor().originX();
                        int chestZ = chest.point().z() - plan.descriptor().originZ();
                        assertTrue(Math.max(Math.abs(chestX - x), Math.abs(chestZ - z)) > 2,
                                "Cobweb must stay away from containers");
                    }
                }
            }
        }
        assertTrue(cobwebs > 0, "Safety assertions require at least one cobweb");
    }
}
