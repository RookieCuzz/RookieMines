package dev.rookiemines.mine;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class FloorPlanGenerator {
    private final GeneratorSettings settings;

    public FloorPlanGenerator(GeneratorSettings settings) {
        this.settings = settings;
    }

    public FloorDescriptor describe(long worldSeed, long generationKey, int floor) {
        long seed = GenerationRules.floorSeed(worldSeed, generationKey, floor);
        Random random = new Random(seed);
        MineTheme theme = GenerationRules.themeForFloor(floor);
        double darkChance = theme == MineTheme.SKULL
                ? settings.skullDarkChance()
                : settings.normalDarkChance();
        boolean dark = theme != MineTheme.LOBBY && random.nextDouble() < darkChance;
        int size = theme == MineTheme.LOBBY
                ? Math.min(28, settings.maxSize())
                : settings.minSize() + random.nextInt(settings.maxSize() - settings.minSize() + 1);
        return new FloorDescriptor(
                floor,
                generationKey,
                seed,
                theme,
                dark,
                size,
                settings.maxSize(),
                settings.height(),
                settings.baseY(),
                floor * settings.floorSpacing(),
                GenerationRules.dailyLuck(worldSeed, generationKey)
        );
    }

    public FloorPlan generate(FloorDescriptor descriptor) {
        if (descriptor.floor() == 120) {
            return generateTerminalRewardRoom(descriptor);
        }
        if (descriptor.theme() == MineTheme.LOBBY) {
            return generateLobby(descriptor);
        }

        Random random = new Random(descriptor.seed());
        FloorPlan plan = new FloorPlan(descriptor);
        int canvas = descriptor.canvasSize();
        int room = descriptor.size();
        int roomStart = (canvas - room) / 2;
        int roomEnd = roomStart + room - 1;
        boolean[][] open = carveConnectedCaves(room, random);
        Material mainStone = mainStone(descriptor.theme());
        Material darkStone = darkStone(descriptor.theme());
        Material accent = accentStone(descriptor.theme());
        int clearHeight = Math.min(7, descriptor.height() - 3);

        for (int x = roomStart; x <= roomEnd; x++) {
            for (int z = roomStart; z <= roomEnd; z++) {
                int rx = x - roomStart;
                int rz = z - roomStart;
                boolean boundary = rx == 0 || rz == 0 || rx == room - 1 || rz == room - 1;
                for (int y = 0; y < descriptor.height(); y++) {
                    Material material;
                    if (boundary || y == 0 || y == descriptor.height() - 1) {
                        material = Material.BEDROCK;
                    } else if (open[rx][rz] && y >= 2 && y <= clearHeight) {
                        material = Material.AIR;
                    } else if (y == 1 && open[rx][rz]) {
                        material = random.nextDouble() < 0.08 ? accent : mainStone;
                    } else {
                        double accentChance = y <= clearHeight + 1 ? 0.055 : 0.025;
                        material = random.nextDouble() < accentChance
                                ? accent
                                : descriptor.dark() && random.nextDouble() < 0.22 ? darkStone : mainStone;
                    }
                    plan.setLocal(x, y, z, material);
                }
            }
        }

        int center = canvas / 2;
        clearSafeZone(plan, center, clearHeight);
        List<int[]> openTiles = collectOpenTiles(open, roomStart);
        placeThemePool(plan, openTiles, center, random);
        placeSpecialRoom(plan, openTiles, center, random);
        placeOres(plan, open, roomStart, random);
        placeLighting(plan, openTiles, center, random);
        placeContainers(plan, openTiles, center, random);
        placeRewardChest(plan, center);
        placeMonsters(plan, openTiles, center, random);

        int stones = 0;
        for (int i = 0; i < plan.volume(); i++) {
            if (isCountedStone(plan.materialAtIndex(i))) {
                stones++;
            }
        }
        plan.setInitialStones(Math.max(1, Math.min(stones, openTiles.size() * 2)));
        return plan;
    }

    private FloorPlan generateTerminalRewardRoom(FloorDescriptor descriptor) {
        FloorPlan plan = new FloorPlan(descriptor);
        int canvas = descriptor.canvasSize();
        int center = canvas / 2;
        int halfX = Math.min(12, canvas / 2 - 2);
        int halfZ = Math.min(8, canvas / 2 - 2);
        int ceiling = Math.min(9, descriptor.height() - 1);

        for (int x = center - halfX; x <= center + halfX; x++) {
            for (int z = center - halfZ; z <= center + halfZ; z++) {
                for (int y = 0; y <= ceiling; y++) {
                    boolean outerShell = x == center - halfX || x == center + halfX
                            || z == center - halfZ || z == center + halfZ
                            || y == 0 || y == ceiling;
                    boolean innerWall = x == center - halfX + 1 || x == center + halfX - 1
                            || z == center - halfZ + 1 || z == center + halfZ - 1
                            || y == ceiling - 1;
                    Material material = outerShell ? Material.BEDROCK
                            : innerWall ? Material.POLISHED_BLACKSTONE_BRICKS
                            : y == 1 ? Material.POLISHED_BLACKSTONE : Material.AIR;
                    plan.setLocal(x, y, z, material);
                }
            }
        }

        for (int x = center - 8; x <= center + 8; x++) {
            plan.setLocal(x, 1, center, x % 2 == 0 ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE);
        }
        for (int x : new int[]{center - 7, center + 7}) {
            for (int z : new int[]{center - 5, center + 5}) {
                plan.setLocal(x, 2, z, Material.CRYING_OBSIDIAN);
                plan.setLocal(x, 3, z, Material.SOUL_LANTERN);
            }
        }

        int chestX = center + 3;
        plan.setLocal(chestX, 2, center, Material.CHEST);
        plan.chests().add(new ChestSpec(
                worldPoint(plan, chestX, 2, center),
                ChestSpec.ChestKind.REWARD
        ));
        plan.setInitialStones(1);
        return plan;
    }

    private FloorPlan generateLobby(FloorDescriptor descriptor) {
        FloorPlan plan = new FloorPlan(descriptor);
        int canvas = descriptor.canvasSize();
        int center = canvas / 2;
        int halfX = Math.min(10, canvas / 2 - 2);
        int halfZ = Math.min(6, canvas / 2 - 2);
        for (int x = center - halfX; x <= center + halfX; x++) {
            for (int z = center - halfZ; z <= center + halfZ; z++) {
                for (int y = 0; y <= 8; y++) {
                    boolean wall = x == center - halfX || x == center + halfX
                            || z == center - halfZ || z == center + halfZ
                            || y == 0 || y == 8;
                    boolean floor = y == 1;
                    plan.setLocal(x, y, z, wall || floor ? Material.CUT_SANDSTONE : Material.AIR);
                }
            }
        }
        for (int x = center - halfX + 2; x <= center + halfX - 2; x += 4) {
            plan.setLocal(x, 2, center - halfZ + 1, Material.SOUL_LANTERN);
            plan.setLocal(x, 2, center + halfZ - 1, Material.SOUL_LANTERN);
        }
        int ladderX = center + halfX - 2;
        int ladderZ = center;
        plan.setLocal(ladderX, 1, ladderZ, Material.CUT_SANDSTONE);
        plan.setLocal(ladderX, 2, ladderZ, Material.OAK_TRAPDOOR);
        BlockPoint ladder = new BlockPoint(
                descriptor.originX() + ladderX,
                descriptor.baseY() + 2,
                descriptor.originZ() + ladderZ
        );
        plan.setPrebuiltLadder(ladder);
        plan.setInitialStones(1);
        return plan;
    }

    private boolean[][] carveConnectedCaves(int size, Random random) {
        boolean[][] open = new boolean[size][size];
        int center = size / 2;
        carveCircle(open, center, center, 6);
        int walkers = Math.max(8, size / 8);
        int margin = 4;
        for (int walker = 0; walker < walkers; walker++) {
            int x = center;
            int z = center;
            int dx = random.nextBoolean() ? 1 : -1;
            int dz = 0;
            int steps = size * 4;
            for (int step = 0; step < steps; step++) {
                if (random.nextDouble() < 0.30) {
                    int direction = random.nextInt(4);
                    dx = direction == 0 ? 1 : direction == 1 ? -1 : 0;
                    dz = direction == 2 ? 1 : direction == 3 ? -1 : 0;
                }
                x = clamp(x + dx, margin, size - margin - 1);
                z = clamp(z + dz, margin, size - margin - 1);
                carveCircle(open, x, z, 2 + random.nextInt(3));
                if (random.nextDouble() < 0.035) {
                    carveEllipse(open, x, z, 5 + random.nextInt(6), 4 + random.nextInt(5));
                }
            }
        }
        carveCircle(open, center, center, 8);
        return open;
    }

    private void carveCircle(boolean[][] open, int cx, int cz, int radius) {
        carveEllipse(open, cx, cz, radius, radius);
    }

    private void carveEllipse(boolean[][] open, int cx, int cz, int radiusX, int radiusZ) {
        for (int x = Math.max(1, cx - radiusX); x <= Math.min(open.length - 2, cx + radiusX); x++) {
            for (int z = Math.max(1, cz - radiusZ); z <= Math.min(open.length - 2, cz + radiusZ); z++) {
                double nx = (x - cx) / (double) radiusX;
                double nz = (z - cz) / (double) radiusZ;
                if (nx * nx + nz * nz <= 1.0) {
                    open[x][z] = true;
                }
            }
        }
    }

    private void clearSafeZone(FloorPlan plan, int center, int clearHeight) {
        for (int x = center - 4; x <= center + 4; x++) {
            for (int z = center - 4; z <= center + 4; z++) {
                plan.setLocal(x, 1, z, Material.SMOOTH_STONE);
                for (int y = 2; y <= clearHeight; y++) {
                    plan.setLocal(x, y, z, Material.AIR);
                }
            }
        }
        plan.setLocal(center - 3, 2, center - 3, Material.TORCH);
        plan.setLocal(center + 3, 2, center - 3, Material.TORCH);
        plan.setLocal(center - 3, 2, center + 3, Material.TORCH);
        plan.setLocal(center + 3, 2, center + 3, Material.TORCH);
    }

    private List<int[]> collectOpenTiles(boolean[][] open, int offset) {
        List<int[]> result = new ArrayList<>();
        for (int x = 1; x < open.length - 1; x++) {
            for (int z = 1; z < open.length - 1; z++) {
                if (open[x][z]) {
                    result.add(new int[]{x + offset, z + offset});
                }
            }
        }
        return result;
    }

    private void placeThemePool(FloorPlan plan, List<int[]> openTiles, int center, Random random) {
        if (openTiles.isEmpty() || random.nextDouble() >= settings.fishingPoolChance()) {
            return;
        }
        List<int[]> shuffled = new ArrayList<>(openTiles);
        Collections.shuffle(shuffled, random);
        for (int[] tile : shuffled) {
            if (distanceSquared(tile[0], tile[1], center, center) < 18 * 18) {
                continue;
            }
            Material liquid = plan.descriptor().theme() == MineTheme.LAVA ? Material.LAVA : Material.WATER;
            int radius = 2 + random.nextInt(2);
            for (int x = tile[0] - radius; x <= tile[0] + radius; x++) {
                for (int z = tile[1] - radius; z <= tile[1] + radius; z++) {
                    if (distanceSquared(x, z, tile[0], tile[1]) <= radius * radius
                            && plan.getLocal(x, 2, z) == Material.AIR) {
                        plan.setLocal(x, 1, z, plan.descriptor().theme() == MineTheme.FROST
                                ? Material.PACKED_ICE : Material.SMOOTH_STONE);
                        plan.setLocal(x, 2, z, liquid);
                    }
                }
            }
            return;
        }
    }

    private void placeSpecialRoom(FloorPlan plan, List<int[]> openTiles, int center, Random random) {
        if (openTiles.isEmpty() || random.nextDouble() >= settings.specialRoomChance()) {
            return;
        }
        List<int[]> shuffled = new ArrayList<>(openTiles);
        Collections.shuffle(shuffled, random);
        for (int[] tile : shuffled) {
            if (distanceSquared(tile[0], tile[1], center, center) < 18 * 18) {
                continue;
            }
            boolean clear = true;
            for (int x = tile[0] - 3; x <= tile[0] + 3 && clear; x++) {
                for (int z = tile[1] - 3; z <= tile[1] + 3; z++) {
                    if (plan.getLocal(x, 2, z) != Material.AIR) {
                        clear = false;
                        break;
                    }
                }
            }
            if (!clear) {
                continue;
            }

            Material floor = switch (plan.descriptor().theme()) {
                case EARTH -> Material.MOSSY_COBBLESTONE;
                case FROST -> Material.BLUE_ICE;
                case LAVA -> Material.POLISHED_BLACKSTONE;
                case SKULL, LOBBY -> Material.CUT_SANDSTONE;
            };
            for (int x = tile[0] - 3; x <= tile[0] + 3; x++) {
                for (int z = tile[1] - 3; z <= tile[1] + 3; z++) {
                    plan.setLocal(x, 1, z, floor);
                }
            }
            for (int dx : new int[]{-3, 3}) {
                for (int dz : new int[]{-3, 3}) {
                    plan.setLocal(tile[0] + dx, 2, tile[1] + dz, accentStone(plan.descriptor().theme()));
                    plan.setLocal(tile[0] + dx, 3, tile[1] + dz,
                            plan.descriptor().theme() == MineTheme.SKULL ? Material.SOUL_LANTERN : Material.LANTERN);
                }
            }
            Material container = plan.descriptor().theme() == MineTheme.SKULL ? Material.CHEST : Material.BARREL;
            ChestSpec.ChestKind kind = plan.descriptor().theme() == MineTheme.SKULL
                    ? ChestSpec.ChestKind.SKULL_TREASURE
                    : ChestSpec.ChestKind.BARREL;
            plan.setLocal(tile[0], 2, tile[1], container);
            plan.chests().add(new ChestSpec(worldPoint(plan, tile[0], 2, tile[1]), kind));
            return;
        }
    }

    private void placeOres(FloorPlan plan, boolean[][] open, int offset, Random random) {
        List<int[]> walls = new ArrayList<>();
        List<int[]> floorTiles = new ArrayList<>();
        for (int x = 2; x < open.length - 2; x++) {
            for (int z = 2; z < open.length - 2; z++) {
                if (open[x][z]) {
                    floorTiles.add(new int[]{x + offset, z + offset});
                }
                if (!open[x][z] && (open[x + 1][z] || open[x - 1][z] || open[x][z + 1] || open[x][z - 1])) {
                    walls.add(new int[]{x + offset, z + offset});
                }
            }
        }
        Collections.shuffle(walls, random);
        int target = Math.min(walls.size(), Math.max(18, plan.descriptor().size() / 2));
        int placed = 0;
        for (int i = 0; i < target; i++) {
            int[] wall = walls.get(i);
            Material ore = chooseOre(plan.descriptor().theme(), plan.descriptor().floor(), random);
            int y = 2 + random.nextInt(Math.max(1, Math.min(5, plan.descriptor().height() - 4)));
            plan.setLocal(wall[0], y, wall[1], ore);
            placed++;
            if (random.nextDouble() < 0.45) {
                plan.setLocal(wall[0], Math.min(y + 1, plan.descriptor().height() - 2), wall[1], ore);
                placed++;
            }
        }
        // Very open cave seeds can have few wall cells. Keep the resource loop reliable by
        // embedding a minimum number of visible nodes in the walkable floor, outside the safe zone.
        Collections.shuffle(floorTiles, random);
        int center = plan.descriptor().canvasSize() / 2;
        int minimum = Math.max(12, plan.descriptor().size() / 5);
        for (int[] tile : floorTiles) {
            if (placed >= minimum) break;
            if (distanceSquared(tile[0], tile[1], center, center) < 10 * 10) continue;
            plan.setLocal(tile[0], 1, tile[1], chooseOre(plan.descriptor().theme(), plan.descriptor().floor(), random));
            placed++;
        }
    }

    private void placeLighting(FloorPlan plan, List<int[]> openTiles, int center, Random random) {
        int spacing = plan.descriptor().dark() ? 18 : plan.descriptor().theme() == MineTheme.SKULL ? 12 : 9;
        for (int[] tile : openTiles) {
            if (distanceSquared(tile[0], tile[1], center, center) >= 7 * 7
                    && Math.floorMod(tile[0] - center, spacing) == 0
                    && Math.floorMod(tile[1] - center, spacing) == 0
                    && plan.getLocal(tile[0], 2, tile[1]) == Material.AIR
                    && random.nextDouble() < 0.70) {
                plan.setLocal(tile[0], 2, tile[1], plan.descriptor().theme() == MineTheme.SKULL
                        ? Material.SOUL_TORCH : Material.TORCH);
            }
        }
    }

    private void placeContainers(FloorPlan plan, List<int[]> openTiles, int center, Random random) {
        if (openTiles.isEmpty()) {
            return;
        }
        List<int[]> shuffled = new ArrayList<>(openTiles);
        Collections.shuffle(shuffled, random);
        int count = 1 + random.nextInt(3);
        for (int[] tile : shuffled) {
            if (count <= 0) {
                return;
            }
            if (distanceSquared(tile[0], tile[1], center, center) < 12 * 12
                    || plan.getLocal(tile[0], 2, tile[1]) != Material.AIR) {
                continue;
            }
            plan.setLocal(tile[0], 2, tile[1], Material.BARREL);
            plan.chests().add(new ChestSpec(worldPoint(plan, tile[0], 2, tile[1]), ChestSpec.ChestKind.BARREL));
            count--;
        }
    }

    private void placeRewardChest(FloorPlan plan, int center) {
        if (!GenerationRules.isRewardFloor(plan.descriptor().floor())) {
            return;
        }
        int x = center + 2;
        int z = center;
        plan.setLocal(x, 2, z, Material.CHEST);
        plan.chests().add(new ChestSpec(worldPoint(plan, x, 2, z), ChestSpec.ChestKind.REWARD));
    }

    private void placeMonsters(FloorPlan plan, List<int[]> openTiles, int center, Random random) {
        if (!settings.generateMonsters() || GenerationRules.isRewardFloor(plan.descriptor().floor())) {
            return;
        }
        List<int[]> candidates = new ArrayList<>();
        for (int[] tile : openTiles) {
            if (distanceSquared(tile[0], tile[1], center, center) >= 14 * 14
                    && plan.getLocal(tile[0], 2, tile[1]) == Material.AIR) {
                candidates.add(tile);
            }
        }
        Collections.shuffle(candidates, random);
        int count = Math.min(settings.maxMonsters(), (int) Math.round(openTiles.size() * settings.monsterDensity()));
        for (int i = 0; i < count && i < candidates.size(); i++) {
            int[] tile = candidates.get(i);
            plan.mobs().add(new MobSpec(worldPoint(plan, tile[0], 2, tile[1]), chooseMob(plan.descriptor().theme(), random)));
        }
    }

    private BlockPoint worldPoint(FloorPlan plan, int localX, int localY, int localZ) {
        return new BlockPoint(
                plan.descriptor().originX() + localX,
                plan.descriptor().baseY() + localY,
                plan.descriptor().originZ() + localZ
        );
    }

    private Material mainStone(MineTheme theme) {
        return switch (theme) {
            case EARTH -> Material.STONE;
            case FROST -> Material.DEEPSLATE;
            case LAVA -> Material.BLACKSTONE;
            case SKULL -> Material.SANDSTONE;
            case LOBBY -> Material.CUT_SANDSTONE;
        };
    }

    private Material darkStone(MineTheme theme) {
        return switch (theme) {
            case EARTH -> Material.TUFF;
            case FROST -> Material.COBBLED_DEEPSLATE;
            case LAVA -> Material.BASALT;
            case SKULL, LOBBY -> Material.SMOOTH_SANDSTONE;
        };
    }

    private Material accentStone(MineTheme theme) {
        return switch (theme) {
            case EARTH -> Material.MOSSY_COBBLESTONE;
            case FROST -> Material.PACKED_ICE;
            case LAVA -> Material.MAGMA_BLOCK;
            case SKULL, LOBBY -> Material.CHISELED_SANDSTONE;
        };
    }

    private Material chooseOre(MineTheme theme, int floor, Random random) {
        double roll = random.nextDouble();
        return switch (theme) {
            case EARTH -> roll < 0.58 ? Material.COPPER_ORE
                    : roll < 0.86 ? Material.COAL_ORE
                    : Material.AMETHYST_BLOCK;
            case FROST -> roll < 0.58 ? Material.IRON_ORE
                    : roll < 0.82 ? Material.LAPIS_ORE
                    : Material.DIAMOND_ORE;
            case LAVA -> roll < 0.56 ? Material.GOLD_ORE
                    : roll < 0.82 ? Material.REDSTONE_ORE
                    : Material.DIAMOND_ORE;
            case SKULL -> {
                double depthBonus = Math.min(0.30, Math.max(0, floor - 121) / 1000.0);
                if (roll < 0.12 + depthBonus) yield Material.ANCIENT_DEBRIS;
                if (roll < 0.44) yield Material.DIAMOND_ORE;
                if (roll < 0.76) yield Material.GOLD_ORE;
                yield Material.EMERALD_ORE;
            }
            case LOBBY -> Material.GOLD_ORE;
        };
    }

    private EntityType chooseMob(MineTheme theme, Random random) {
        return switch (theme) {
            case EARTH -> random.nextDouble() < 0.65 ? EntityType.SLIME : EntityType.ZOMBIE;
            case FROST -> random.nextDouble() < 0.55 ? EntityType.STRAY : EntityType.SLIME;
            case LAVA -> random.nextDouble() < 0.55 ? EntityType.MAGMA_CUBE : EntityType.SKELETON;
            case SKULL -> random.nextDouble() < 0.45 ? EntityType.HUSK
                    : random.nextDouble() < 0.60 ? EntityType.MAGMA_CUBE : EntityType.SKELETON;
            case LOBBY -> EntityType.SLIME;
        };
    }

    public static boolean isCountedStone(Material material) {
        return switch (material) {
            case STONE, TUFF, DEEPSLATE, COBBLED_DEEPSLATE, BLACKSTONE, BASALT,
                    SANDSTONE, SMOOTH_SANDSTONE, COAL_ORE, COPPER_ORE, IRON_ORE,
                    GOLD_ORE, REDSTONE_ORE, LAPIS_ORE, DIAMOND_ORE, EMERALD_ORE,
                    ANCIENT_DEBRIS, AMETHYST_BLOCK -> true;
            default -> false;
        };
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private int distanceSquared(int x1, int z1, int x2, int z2) {
        int dx = x1 - x2;
        int dz = z1 - z2;
        return dx * dx + dz * dz;
    }
}
