package dev.rookiemines.mine;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class FloorPlanGenerator {
    private static final int CAVE_SHELL_MARGIN = 4;
    private static final double TARGET_OPEN_RATIO = 0.50;
    private final GeneratorSettings settings;

    public FloorPlanGenerator(GeneratorSettings settings) {
        this.settings = settings;
    }

    public FloorDescriptor describe(long worldSeed, long generationKey, int floor) {
        long seed = GenerationRules.mix64(
                GenerationRules.floorSeed(worldSeed, generationKey, floor) ^ settings.generationSalt()
        );
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
        int caveCenter = canvas / 2 - roomStart;
        boolean[][] open = carveConnectedCaves(room, caveCenter, random);
        CaveShape cave = shapeCave(open, roomStart, descriptor, random);
        Material mainStone = mainStone(descriptor.theme());
        Material darkStone = darkStone(descriptor.theme());
        Material accent = accentStone(descriptor.theme());
        int clearHeight = Math.min(7, descriptor.height() - 3);

        for (int x = roomStart; x <= roomEnd; x++) {
            for (int z = roomStart; z <= roomEnd; z++) {
                int rx = x - roomStart;
                int rz = z - roomStart;
                boolean boundary = rx == 0 || rz == 0 || rx == room - 1 || rz == room - 1;
                int floorY = cave.floorAt(rx, rz);
                int ceilingY = cave.ceilingAt(rx, rz);
                for (int y = 0; y < descriptor.height(); y++) {
                    Material material;
                    if (boundary || y == 0 || y == descriptor.height() - 1) {
                        material = Material.BEDROCK;
                    } else if (cave.isOpen(rx, rz) && y > floorY && y < ceilingY) {
                        material = Material.AIR;
                    } else if (cave.isOpen(rx, rz) && y == floorY) {
                        double surfaceRoll = random.nextDouble();
                        material = surfaceRoll < 0.10 ? accent
                                : surfaceRoll < 0.22 ? darkStone : mainStone;
                    } else {
                        double accentChance = y <= ceilingY + 1 ? 0.055 : 0.025;
                        material = random.nextDouble() < accentChance
                                ? accent
                                : descriptor.dark() && random.nextDouble() < 0.22 ? darkStone : mainStone;
                    }
                    plan.setLocal(x, y, z, material);
                }
            }
        }

        int center = canvas / 2;
        roughenWalls(plan, cave, random);
        clearSafeZone(plan, center, clearHeight);
        List<CaveTile> openTiles = collectOpenTiles(cave);
        placeRockFormations(plan, openTiles, center, random);
        PoolPlacement pool = placeThemePool(plan, openTiles, cave, center, random);
        placeOres(plan, cave, pool, random);
        placeLighting(plan, openTiles, center, pool, random);
        placeContainers(plan, openTiles, center, pool, random);
        SpecialRoomPlacement specialRoom = placeSpecialRoom(plan, openTiles, cave, center, pool, random);
        placeRewardChest(plan, center);
        placeCobwebs(plan, openTiles, center, pool, specialRoom, random);
        placeMonsters(plan, openTiles, center, pool, specialRoom, random);

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

    private boolean[][] carveConnectedCaves(int size, int center, Random random) {
        boolean[][] open = new boolean[size][size];
        int carved = carveCircle(open, center, center, 6);
        int targetOpen = (int) Math.round(size * size * TARGET_OPEN_RATIO);
        int walkers = Math.max(8, size / 8);
        for (int walker = 0; walker < walkers && carved < targetOpen; walker++) {
            int x = center;
            int z = center;
            int dx = random.nextBoolean() ? 1 : -1;
            int dz = 0;
            int steps = size * 4;
            for (int step = 0; step < steps && carved < targetOpen; step++) {
                if (random.nextDouble() < 0.30) {
                    int direction = random.nextInt(4);
                    dx = direction == 0 ? 1 : direction == 1 ? -1 : 0;
                    dz = direction == 2 ? 1 : direction == 3 ? -1 : 0;
                }
                x = clamp(x + dx, CAVE_SHELL_MARGIN, size - CAVE_SHELL_MARGIN - 1);
                z = clamp(z + dz, CAVE_SHELL_MARGIN, size - CAVE_SHELL_MARGIN - 1);
                carved += carveCircle(open, x, z, 2 + random.nextInt(3));
                if (random.nextDouble() < 0.035) {
                    carved += carveEllipse(open, x, z, 5 + random.nextInt(6), 4 + random.nextInt(5));
                }
            }
        }
        carveCircle(open, center, center, 8);
        return open;
    }

    private int carveCircle(boolean[][] open, int cx, int cz, int radius) {
        return carveEllipse(open, cx, cz, radius, radius);
    }

    private int carveEllipse(boolean[][] open, int cx, int cz, int radiusX, int radiusZ) {
        int carved = 0;
        for (int x = Math.max(CAVE_SHELL_MARGIN, cx - radiusX);
             x <= Math.min(open.length - CAVE_SHELL_MARGIN - 1, cx + radiusX); x++) {
            for (int z = Math.max(CAVE_SHELL_MARGIN, cz - radiusZ);
                 z <= Math.min(open.length - CAVE_SHELL_MARGIN - 1, cz + radiusZ); z++) {
                double nx = (x - cx) / (double) radiusX;
                double nz = (z - cz) / (double) radiusZ;
                if (nx * nx + nz * nz <= 1.0 && !open[x][z]) {
                    open[x][z] = true;
                    carved++;
                }
            }
        }
        return carved;
    }

    private CaveShape shapeCave(
            boolean[][] open,
            int roomStart,
            FloorDescriptor descriptor,
            Random random
    ) {
        int size = open.length;
        int[][] floors = new int[size][size];
        int[][] ceilings = new int[size][size];
        int center = descriptor.canvasSize() / 2 - roomStart;
        int safeRadiusSquared = 6 * 6;
        int baseCeiling = Math.min(8, descriptor.height() - 2);
        long floorNoiseSeed = random.nextLong();
        long ceilingNoiseSeed = random.nextLong();

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                floors[x][z] = 1;
                ceilings[x][z] = baseCeiling;
                if (!open[x][z]) continue;

                double floorNoise = fractalNoise(floorNoiseSeed, x, z);
                double normalized = Math.max(0.0, Math.min(1.0, 0.5 + floorNoise * 0.68));
                floors[x][z] = 1 + (int) Math.round(normalized * settings.floorVariation());
                if (distanceSquared(x, z, center, center) <= safeRadiusSquared) {
                    floors[x][z] = 1;
                }
            }
        }
        limitFloorSlopes(open, floors);

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (!open[x][z]) continue;
                int domeLift = Math.min(3, Math.max(0, (distanceToWall(open, x, z, 7) - 1) / 2));
                int domeAdjustment = settings.ceilingVariation() == 0 ? 1 : domeLift;
                int ceiling = baseCeiling - 1 + domeAdjustment + (int) Math.round(
                        fractalNoise(ceilingNoiseSeed, x, z) * settings.ceilingVariation()
                );
                ceilings[x][z] = clamp(ceiling, floors[x][z] + 4, descriptor.height() - 2);
                if (distanceSquared(x, z, center, center) <= safeRadiusSquared) {
                    ceilings[x][z] = Math.max(ceilings[x][z], baseCeiling);
                }
            }
        }
        return new CaveShape(open, floors, ceilings, roomStart);
    }

    private int distanceToWall(boolean[][] open, int centerX, int centerZ, int maximum) {
        for (int radius = 1; radius <= maximum; radius++) {
            int minimumX = centerX - radius;
            int maximumX = centerX + radius;
            int minimumZ = centerZ - radius;
            int maximumZ = centerZ + radius;
            for (int x = minimumX; x <= maximumX; x++) {
                if (!isOpen(open, x, minimumZ) || !isOpen(open, x, maximumZ)) return radius;
            }
            for (int z = minimumZ + 1; z < maximumZ; z++) {
                if (!isOpen(open, minimumX, z) || !isOpen(open, maximumX, z)) return radius;
            }
        }
        return maximum + 1;
    }

    private boolean isOpen(boolean[][] open, int x, int z) {
        return x >= 0 && x < open.length && z >= 0 && z < open.length && open[x][z];
    }

    private void limitFloorSlopes(boolean[][] open, int[][] floors) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int pass = 0; pass < open.length; pass++) {
            boolean changed = false;
            for (int x = 1; x < open.length - 1; x++) {
                for (int z = 1; z < open.length - 1; z++) {
                    if (!open[x][z]) continue;
                    int maximum = floors[x][z];
                    for (int[] direction : directions) {
                        int nx = x + direction[0];
                        int nz = z + direction[1];
                        if (open[nx][nz]) {
                            maximum = Math.min(maximum, floors[nx][nz] + 1);
                        }
                    }
                    if (maximum < floors[x][z]) {
                        floors[x][z] = maximum;
                        changed = true;
                    }
                }
            }
            if (!changed) return;
        }
    }

    private double fractalNoise(long seed, int x, int z) {
        double coarse = valueNoise(seed, x, z, 18.0);
        double medium = valueNoise(seed ^ 0x632BE59BD9B4E019L, x, z, 9.0);
        double fine = valueNoise(seed ^ 0xC6BC279692B5CC83L, x, z, 5.0);
        return coarse * 0.55 + medium * 0.30 + fine * 0.15;
    }

    private double valueNoise(long seed, int x, int z, double scale) {
        double scaledX = x / scale;
        double scaledZ = z / scale;
        int x0 = (int) Math.floor(scaledX);
        int z0 = (int) Math.floor(scaledZ);
        double tx = smoothStep(scaledX - x0);
        double tz = smoothStep(scaledZ - z0);
        double top = lerp(latticeNoise(seed, x0, z0), latticeNoise(seed, x0 + 1, z0), tx);
        double bottom = lerp(latticeNoise(seed, x0, z0 + 1), latticeNoise(seed, x0 + 1, z0 + 1), tx);
        return lerp(top, bottom, tz);
    }

    private double latticeNoise(long seed, int x, int z) {
        long mixed = GenerationRules.mix64(seed
                ^ ((long) x * 0x9E3779B97F4A7C15L)
                ^ ((long) z * 0xD1B54A32D192ED03L));
        return ((mixed >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private double lerp(double first, double second, double amount) {
        return first + (second - first) * amount;
    }

    private void roughenWalls(FloorPlan plan, CaveShape cave, Random random) {
        if (settings.wallRoughness() <= 0.0) return;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = 2; x < cave.size() - 2; x++) {
            for (int z = 2; z < cave.size() - 2; z++) {
                if (cave.isOpen(x, z) || random.nextDouble() >= settings.wallRoughness()) continue;
                List<CaveTile> neighbors = new ArrayList<>(4);
                for (int[] direction : directions) {
                    int nx = x + direction[0];
                    int nz = z + direction[1];
                    if (cave.isOpen(nx, nz)) neighbors.add(cave.tileAt(nx, nz));
                }
                if (neighbors.isEmpty()) continue;

                CaveTile neighbor = neighbors.get(random.nextInt(neighbors.size()));
                int bottomTrim = random.nextDouble() < 0.30 ? 1 : 0;
                int topTrim = random.nextInt(Math.min(3, Math.max(1, neighbor.headroom() - 1)));
                int bottom = neighbor.floorY() + 1 + bottomTrim;
                int top = neighbor.ceilingY() - 1 - topTrim;
                if (top - bottom < 1) continue;
                for (int y = bottom; y <= top; y++) {
                    plan.setLocal(cave.toLocal(x), y, cave.toLocal(z), Material.AIR);
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

    private List<CaveTile> collectOpenTiles(CaveShape cave) {
        List<CaveTile> result = new ArrayList<>();
        for (int x = 1; x < cave.size() - 1; x++) {
            for (int z = 1; z < cave.size() - 1; z++) {
                if (cave.isOpen(x, z)) result.add(cave.tileAt(x, z));
            }
        }
        return result;
    }

    private void placeRockFormations(FloorPlan plan, List<CaveTile> openTiles, int center, Random random) {
        int target = Math.min(140, (int) Math.round(openTiles.size() * settings.formationDensity()));
        if (target <= 0) return;
        List<CaveTile> shuffled = new ArrayList<>(openTiles);
        Collections.shuffle(shuffled, random);
        int placed = 0;
        for (CaveTile tile : shuffled) {
            if (placed >= target) return;
            if (distanceSquared(tile.x(), tile.z(), center, center) < 10 * 10 || tile.headroom() < 5) continue;
            if (plan.getLocal(tile.x(), tile.standingY(), tile.z()) != Material.AIR) continue;

            int maximumLength = Math.min(3, tile.headroom() - 3);
            int length = 1 + random.nextInt(Math.max(1, maximumLength));
            Material material = formationStone(plan.descriptor().theme(), random);
            if (random.nextBoolean()) {
                for (int step = 1; step <= length; step++) {
                    plan.setLocal(tile.x(), tile.ceilingY() - step, tile.z(), material);
                }
            } else {
                for (int step = 1; step <= length; step++) {
                    plan.setLocal(tile.x(), tile.floorY() + step, tile.z(), material);
                }
            }
            placed++;
        }
    }

    private PoolPlacement placeThemePool(
            FloorPlan plan,
            List<CaveTile> openTiles,
            CaveShape cave,
            int center,
            Random random
    ) {
        if (openTiles.isEmpty() || random.nextDouble() >= settings.fishingPoolChance()) {
            return null;
        }
        List<CaveTile> shuffled = new ArrayList<>(openTiles);
        Collections.shuffle(shuffled, random);
        for (CaveTile tile : shuffled) {
            if (distanceSquared(tile.x(), tile.z(), center, center) < 18 * 18) {
                continue;
            }
            Material liquid = plan.descriptor().theme() == MineTheme.LAVA ? Material.LAVA : Material.WATER;
            int radius = 2 + random.nextInt(2);
            int floorY = tile.floorY();
            for (int x = tile.x() - radius; x <= tile.x() + radius; x++) {
                for (int z = tile.z() - radius; z <= tile.z() + radius; z++) {
                    CaveTile nearby = cave.tileAtLocal(x, z);
                    if (nearby != null
                            && Math.abs(nearby.floorY() - floorY) <= 1
                            && nearby.ceilingY() >= floorY + 4
                            && distanceSquared(x, z, tile.x(), tile.z()) <= radius * radius) {
                        plan.setLocal(x, floorY, z, plan.descriptor().theme() == MineTheme.FROST
                                ? Material.PACKED_ICE : Material.SMOOTH_STONE);
                        plan.setLocal(x, floorY + 1, z, liquid);
                        for (int y = floorY + 2; y < nearby.ceilingY(); y++) {
                            plan.setLocal(x, y, z, Material.AIR);
                        }
                    }
                }
            }
            return new PoolPlacement(tile.x(), tile.z(), radius);
        }
        return null;
    }

    private SpecialRoomPlacement placeSpecialRoom(
            FloorPlan plan,
            List<CaveTile> openTiles,
            CaveShape cave,
            int center,
            PoolPlacement pool,
            Random random
    ) {
        if (openTiles.isEmpty() || random.nextDouble() >= settings.specialRoomChance()) {
            return null;
        }
        List<CaveTile> shuffled = new ArrayList<>(openTiles);
        Collections.shuffle(shuffled, random);
        for (CaveTile tile : shuffled) {
            if (distanceSquared(tile.x(), tile.z(), center, center) < 18 * 18) {
                continue;
            }
            if (!cave.hasRoomFor(tile.x(), tile.z(), 3)) continue;
            if (pool != null && pool.intersectsSquare(tile.x(), tile.z(), 3)) continue;
            CaveTile entrance = findRoomEntrance(plan, cave, tile.x(), tile.z(), 3);
            if (entrance == null) continue;

            Material floor = switch (plan.descriptor().theme()) {
                case EARTH -> Material.MOSSY_COBBLESTONE;
                case FROST -> Material.BLUE_ICE;
                case LAVA -> Material.POLISHED_BLACKSTONE;
                case SKULL, LOBBY -> Material.CUT_SANDSTONE;
            };
            // Match the platform to an adjacent open cave tile so the flattened
            // room never creates an unstepable cliff at its only entrance.
            int floorY = entrance.floorY();
            for (int x = tile.x() - 3; x <= tile.x() + 3; x++) {
                for (int z = tile.z() - 3; z <= tile.z() + 3; z++) {
                    for (int y = 1; y <= floorY; y++) {
                        plan.setLocal(x, y, z, floor);
                    }
                    for (int y = floorY + 1; y <= floorY + 4; y++) {
                        plan.setLocal(x, y, z, Material.AIR);
                    }
                }
            }
            for (int dx : new int[]{-3, 3}) {
                for (int dz : new int[]{-3, 3}) {
                    plan.setLocal(tile.x() + dx, floorY + 1, tile.z() + dz,
                            accentStone(plan.descriptor().theme()));
                    plan.setLocal(tile.x() + dx, floorY + 2, tile.z() + dz,
                            plan.descriptor().theme() == MineTheme.SKULL ? Material.SOUL_LANTERN : Material.LANTERN);
                }
            }
            Material container = plan.descriptor().theme() == MineTheme.SKULL ? Material.CHEST : Material.BARREL;
            ChestSpec.ChestKind kind = plan.descriptor().theme() == MineTheme.SKULL
                    ? ChestSpec.ChestKind.SKULL_TREASURE
                    : ChestSpec.ChestKind.BARREL;
            plan.setLocal(tile.x(), floorY + 1, tile.z(), container);
            plan.chests().removeIf(chest -> {
                int chestX = chest.point().x() - plan.descriptor().originX();
                int chestZ = chest.point().z() - plan.descriptor().originZ();
                return Math.abs(chestX - tile.x()) <= 3 && Math.abs(chestZ - tile.z()) <= 3;
            });
            plan.chests().add(new ChestSpec(worldPoint(plan, tile.x(), floorY + 1, tile.z()), kind));
            return new SpecialRoomPlacement(tile.x(), tile.z(), entrance);
        }
        return null;
    }

    private CaveTile findRoomEntrance(
            FloorPlan plan,
            CaveShape cave,
            int centerX,
            int centerZ,
            int radius
    ) {
        int outside = radius + 1;
        for (int offset = -radius + 1; offset <= radius - 1; offset++) {
            CaveTile north = cave.tileAtLocal(centerX + offset, centerZ - outside);
            if (isClearRoomEntrance(plan, north)) return north;
            CaveTile south = cave.tileAtLocal(centerX + offset, centerZ + outside);
            if (isClearRoomEntrance(plan, south)) return south;
            CaveTile west = cave.tileAtLocal(centerX - outside, centerZ + offset);
            if (isClearRoomEntrance(plan, west)) return west;
            CaveTile east = cave.tileAtLocal(centerX + outside, centerZ + offset);
            if (isClearRoomEntrance(plan, east)) return east;
        }
        return null;
    }

    private boolean isClearRoomEntrance(FloorPlan plan, CaveTile tile) {
        return tile != null
                && tile.headroom() >= 3
                && isDrySupport(plan.getLocal(tile.x(), tile.floorY(), tile.z()))
                && isWalkableAir(plan.getLocal(tile.x(), tile.standingY(), tile.z()))
                && isWalkableAir(plan.getLocal(tile.x(), tile.standingY() + 1, tile.z()));
    }

    private boolean isDrySupport(Material material) {
        return material != Material.AIR && material != Material.WATER && material != Material.LAVA;
    }

    private boolean isWalkableAir(Material material) {
        return material == Material.AIR || material == Material.TORCH || material == Material.SOUL_TORCH;
    }

    private void placeOres(FloorPlan plan, CaveShape cave, PoolPlacement pool, Random random) {
        List<WallFace> walls = new ArrayList<>();
        List<CaveTile> floorTiles = new ArrayList<>();
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int x = 2; x < cave.size() - 2; x++) {
            for (int z = 2; z < cave.size() - 2; z++) {
                if (cave.isOpen(x, z)) {
                    floorTiles.add(cave.tileAt(x, z));
                    continue;
                }
                for (int[] direction : directions) {
                    int nx = x + direction[0];
                    int nz = z + direction[1];
                    if (cave.isOpen(nx, nz)) {
                        CaveTile neighbor = cave.tileAt(nx, nz);
                        walls.add(new WallFace(
                                cave.toLocal(x), cave.toLocal(z),
                                neighbor.floorY(), neighbor.ceilingY()
                        ));
                        break;
                    }
                }
            }
        }
        Collections.shuffle(walls, random);
        int target = Math.min(walls.size(), Math.max(18, plan.descriptor().size() / 2));
        int placed = 0;
        for (WallFace wall : walls) {
            if (placed >= target) break;
            if (pool != null && pool.contains(wall.x(), wall.z())) continue;
            int minimumY = wall.floorY() + 1;
            int maximumY = wall.ceilingY() - 1;
            if (maximumY < minimumY) continue;
            int y = minimumY + random.nextInt(maximumY - minimumY + 1);
            if (plan.getLocal(wall.x(), y, wall.z()) == Material.AIR) continue;
            Material ore = chooseOre(plan.descriptor().theme(), plan.descriptor().floor(), random);
            plan.setLocal(wall.x(), y, wall.z(), ore);
            placed++;
            if (y + 1 <= maximumY
                    && plan.getLocal(wall.x(), y + 1, wall.z()) != Material.AIR
                    && random.nextDouble() < 0.45) {
                plan.setLocal(wall.x(), y + 1, wall.z(), ore);
                placed++;
            }
        }
        // Very open cave seeds can have few wall cells. Keep the resource loop reliable by
        // embedding a minimum number of visible nodes in the walkable floor, outside the safe zone.
        Collections.shuffle(floorTiles, random);
        int center = plan.descriptor().canvasSize() / 2;
        int minimum = Math.max(12, plan.descriptor().size() / 5);
        for (CaveTile tile : floorTiles) {
            if (placed >= minimum) break;
            if (distanceSquared(tile.x(), tile.z(), center, center) < 10 * 10) continue;
            if (pool != null && pool.contains(tile.x(), tile.z())) continue;
            plan.setLocal(tile.x(), tile.floorY(), tile.z(),
                    chooseOre(plan.descriptor().theme(), plan.descriptor().floor(), random));
            placed++;
        }
    }

    private void placeLighting(
            FloorPlan plan,
            List<CaveTile> openTiles,
            int center,
            PoolPlacement pool,
            Random random
    ) {
        int spacing = plan.descriptor().dark() ? 18 : plan.descriptor().theme() == MineTheme.SKULL ? 12 : 9;
        for (CaveTile tile : openTiles) {
            if (distanceSquared(tile.x(), tile.z(), center, center) >= 7 * 7
                    && (pool == null || !pool.contains(tile.x(), tile.z()))
                    && Math.floorMod(tile.x() - center, spacing) == 0
                    && Math.floorMod(tile.z() - center, spacing) == 0
                    && plan.getLocal(tile.x(), tile.standingY(), tile.z()) == Material.AIR
                    && random.nextDouble() < 0.70) {
                plan.setLocal(tile.x(), tile.standingY(), tile.z(), plan.descriptor().theme() == MineTheme.SKULL
                        ? Material.SOUL_TORCH : Material.TORCH);
            }
        }
    }

    private void placeContainers(
            FloorPlan plan,
            List<CaveTile> openTiles,
            int center,
            PoolPlacement pool,
            Random random
    ) {
        if (openTiles.isEmpty()) {
            return;
        }
        List<CaveTile> shuffled = new ArrayList<>(openTiles);
        Collections.shuffle(shuffled, random);
        int count = 1 + random.nextInt(3);
        for (CaveTile tile : shuffled) {
            if (count <= 0) {
                return;
            }
            if (distanceSquared(tile.x(), tile.z(), center, center) < 12 * 12
                    || pool != null && pool.contains(tile.x(), tile.z())
                    || plan.getLocal(tile.x(), tile.standingY(), tile.z()) != Material.AIR
                    || plan.getLocal(tile.x(), tile.standingY() + 1, tile.z()) != Material.AIR) {
                continue;
            }
            plan.setLocal(tile.x(), tile.standingY(), tile.z(), Material.BARREL);
            plan.chests().add(new ChestSpec(
                    worldPoint(plan, tile.x(), tile.standingY(), tile.z()),
                    ChestSpec.ChestKind.BARREL
            ));
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

    private void placeCobwebs(
            FloorPlan plan,
            List<CaveTile> openTiles,
            int center,
            PoolPlacement pool,
            SpecialRoomPlacement specialRoom,
            Random random
    ) {
        double density = switch (plan.descriptor().theme()) {
            case EARTH -> settings.earthCobwebDensity();
            case SKULL -> settings.skullCobwebDensity();
            case FROST, LAVA, LOBBY -> 0.0;
        };
        if (density <= 0.0 || openTiles.isEmpty()) return;

        List<CobwebCandidate> roomEdges = new ArrayList<>();
        List<CobwebCandidate> wallEdges = new ArrayList<>();
        for (CaveTile tile : openTiles) {
            if (distanceSquared(tile.x(), tile.z(), center, center) < 12 * 12) continue;
            if (pool != null && pool.intersectsSquare(tile.x(), tile.z(), 1)) continue;
            if (specialRoom != null && specialRoom.nearEntrance(tile.x(), tile.z(), 1)) continue;
            if (nearContainer(plan, tile.x(), tile.z(), 2)
                    || nearPrebuiltLadder(plan, tile.x(), tile.z(), 3)) {
                continue;
            }

            int floorY = findDecorationFloor(plan, tile.x(), tile.z());
            if (floorY < 0) continue;
            int cobwebY = findWallFringeHeight(plan, tile.x(), tile.z(), floorY);
            if (cobwebY < 0) continue;
            CobwebCandidate candidate = new CobwebCandidate(tile.x(), cobwebY, tile.z());
            if (specialRoom != null && specialRoom.decorativeEdge(tile.x(), tile.z())) {
                roomEdges.add(candidate);
            } else {
                wallEdges.add(candidate);
            }
        }

        Collections.shuffle(roomEdges, random);
        Collections.shuffle(wallEdges, random);
        roomEdges.addAll(wallEdges);
        int cap = plan.descriptor().theme() == MineTheme.SKULL ? 48 : 12;
        int target = Math.min(cap, Math.max(1, (int) Math.round(openTiles.size() * density)));
        List<CobwebCandidate> placed = new ArrayList<>(target);
        for (CobwebCandidate candidate : roomEdges) {
            if (placed.size() >= target) return;
            if (placed.stream().anyMatch(existing ->
                    distanceSquared(existing.x(), existing.z(), candidate.x(), candidate.z()) < 3 * 3)) {
                continue;
            }
            if (plan.getLocal(candidate.x(), candidate.y(), candidate.z()) != Material.AIR) continue;
            plan.setLocal(candidate.x(), candidate.y(), candidate.z(), Material.COBWEB);
            placed.add(candidate);
        }
    }

    private int findDecorationFloor(FloorPlan plan, int x, int z) {
        for (int y = 1; y <= plan.descriptor().height() - 5; y++) {
            if (!isDecorationAnchor(plan.getLocal(x, y, z))) continue;
            if (plan.getLocal(x, y + 1, z) == Material.AIR
                    && plan.getLocal(x, y + 2, z) == Material.AIR
                    && plan.getLocal(x, y + 3, z) == Material.AIR) {
                return y;
            }
        }
        return -1;
    }

    private int findWallFringeHeight(FloorPlan plan, int x, int z, int floorY) {
        for (int y = plan.descriptor().height() - 2; y >= floorY + 4; y--) {
            if (plan.getLocal(x, y, z) != Material.AIR) continue;
            boolean solidNeighbor = false;
            boolean airNeighbor = false;
            for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Material neighbor = plan.getLocal(x + direction[0], y, z + direction[1]);
                solidNeighbor |= isDecorationAnchor(neighbor);
                airNeighbor |= neighbor == Material.AIR;
            }
            if (solidNeighbor && airNeighbor) return y;
        }
        return -1;
    }

    private boolean nearContainer(FloorPlan plan, int x, int z, int radius) {
        for (ChestSpec chest : plan.chests()) {
            int chestX = chest.point().x() - plan.descriptor().originX();
            int chestZ = chest.point().z() - plan.descriptor().originZ();
            if (Math.max(Math.abs(chestX - x), Math.abs(chestZ - z)) <= radius) {
                return true;
            }
        }
        return false;
    }

    private boolean nearPrebuiltLadder(FloorPlan plan, int x, int z, int radius) {
        BlockPoint ladder = plan.prebuiltLadder();
        if (ladder == null) return false;
        int ladderX = ladder.x() - plan.descriptor().originX();
        int ladderZ = ladder.z() - plan.descriptor().originZ();
        return Math.max(Math.abs(ladderX - x), Math.abs(ladderZ - z)) <= radius;
    }

    private boolean isDecorationAnchor(Material material) {
        return switch (material) {
            case AIR, WATER, LAVA, COBWEB, TORCH, SOUL_TORCH, LANTERN, SOUL_LANTERN,
                    CHEST, BARREL, OAK_TRAPDOOR -> false;
            default -> true;
        };
    }

    private void placeMonsters(
            FloorPlan plan,
            List<CaveTile> openTiles,
            int center,
            PoolPlacement pool,
            SpecialRoomPlacement specialRoom,
            Random random
    ) {
        if (!settings.generateMonsters() || GenerationRules.isRewardFloor(plan.descriptor().floor())) {
            return;
        }
        List<CaveTile> candidates = new ArrayList<>();
        for (CaveTile tile : openTiles) {
            if (distanceSquared(tile.x(), tile.z(), center, center) >= 14 * 14
                    && (pool == null || !pool.contains(tile.x(), tile.z()))
                    && (specialRoom == null || !specialRoom.blocks(tile.x(), tile.z()))
                    && plan.getLocal(tile.x(), tile.standingY(), tile.z()) == Material.AIR
                    && plan.getLocal(tile.x(), tile.standingY() + 1, tile.z()) == Material.AIR) {
                candidates.add(tile);
            }
        }
        Collections.shuffle(candidates, random);
        int count = Math.min(settings.maxMonsters(), (int) Math.round(openTiles.size() * settings.monsterDensity()));
        for (int i = 0; i < count && i < candidates.size(); i++) {
            CaveTile tile = candidates.get(i);
            plan.mobs().add(new MobSpec(
                    worldPoint(plan, tile.x(), tile.standingY(), tile.z()),
                    chooseMob(plan.descriptor().theme(), random)
            ));
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

    private Material formationStone(MineTheme theme, Random random) {
        return switch (theme) {
            case EARTH -> random.nextBoolean() ? Material.DRIPSTONE_BLOCK : Material.TUFF;
            case FROST -> random.nextBoolean() ? Material.PACKED_ICE : Material.CALCITE;
            case LAVA -> random.nextDouble() < 0.30 ? Material.MAGMA_BLOCK : Material.BASALT;
            case SKULL -> random.nextBoolean() ? Material.SMOOTH_SANDSTONE : Material.CHISELED_SANDSTONE;
            case LOBBY -> Material.CUT_SANDSTONE;
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

    private record CaveShape(
            boolean[][] open,
            int[][] floors,
            int[][] ceilings,
            int roomStart
    ) {
        int size() {
            return open.length;
        }

        boolean isOpen(int x, int z) {
            return x >= 0 && x < size() && z >= 0 && z < size() && open[x][z];
        }

        int floorAt(int x, int z) {
            return floors[x][z];
        }

        int ceilingAt(int x, int z) {
            return ceilings[x][z];
        }

        int toLocal(int roomCoordinate) {
            return roomStart + roomCoordinate;
        }

        CaveTile tileAt(int x, int z) {
            if (!isOpen(x, z)) return null;
            return new CaveTile(toLocal(x), toLocal(z), floors[x][z], ceilings[x][z]);
        }

        CaveTile tileAtLocal(int localX, int localZ) {
            return tileAt(localX - roomStart, localZ - roomStart);
        }

        boolean hasRoomFor(int localX, int localZ, int radius) {
            int x = localX - roomStart;
            int z = localZ - roomStart;
            return x - radius >= 1 && x + radius < size() - 1
                    && z - radius >= 1 && z + radius < size() - 1;
        }
    }

    private record CaveTile(int x, int z, int floorY, int ceilingY) {
        int standingY() {
            return floorY + 1;
        }

        int headroom() {
            return ceilingY - floorY - 1;
        }
    }

    private record WallFace(int x, int z, int floorY, int ceilingY) {
    }

    private record CobwebCandidate(int x, int y, int z) {
    }

    private record SpecialRoomPlacement(int centerX, int centerZ, CaveTile entrance) {
        boolean blocks(int x, int z) {
            return Math.abs(x - centerX) <= 3 && Math.abs(z - centerZ) <= 3
                    || x == entrance.x() && z == entrance.z();
        }

        boolean decorativeEdge(int x, int z) {
            int dx = Math.abs(x - centerX);
            int dz = Math.abs(z - centerZ);
            return dx <= 3 && dz <= 3 && (dx >= 2 || dz >= 2);
        }

        boolean nearEntrance(int x, int z, int radius) {
            return Math.max(Math.abs(x - entrance.x()), Math.abs(z - entrance.z())) <= radius;
        }
    }

    private record PoolPlacement(int centerX, int centerZ, int radius) {
        boolean contains(int x, int z) {
            return distanceSquared(x, z, centerX, centerZ) <= radius * radius;
        }

        boolean intersectsSquare(int squareCenterX, int squareCenterZ, int halfSize) {
            int closestX = Math.max(squareCenterX - halfSize, Math.min(centerX, squareCenterX + halfSize));
            int closestZ = Math.max(squareCenterZ - halfSize, Math.min(centerZ, squareCenterZ + halfSize));
            return contains(closestX, closestZ);
        }

        private int distanceSquared(int x1, int z1, int x2, int z2) {
            int dx = x1 - x2;
            int dz = z1 - z2;
            return dx * dx + dz * dz;
        }
    }
}
