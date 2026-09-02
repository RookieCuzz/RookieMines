package dev.rookiemines.mine;

import dev.rookiemines.RookieMinesPlugin;
import dev.rookiemines.storage.FloorStateStore;
import dev.rookiemines.storage.PlayerProgressStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class FloorManager {
    private static final String PREFIX = "[菜鸟矿洞] ";
    private static final String FLOOR_TAG_PREFIX = "rookie_mines_floor_";

    private final RookieMinesPlugin plugin;
    private final World world;
    private final GeneratorSettings settings;
    private final FloorPlanGenerator generator;
    private final FloorStateStore floorStore;
    private final PlayerProgressStore progressStore;
    private final NamespacedKey floorKey;
    private final NamespacedKey skullKey;
    private final int blocksPerTick;
    private final double ladderBaseChance;
    private final double noEnemiesBonus;
    private final double shaftChance;
    private final Map<Integer, GenerationJob> jobs = new HashMap<>();
    private final Map<Integer, Set<UUID>> waitingPlayers = new HashMap<>();
    private final Map<BlockKey, LadderTarget> ladders = new HashMap<>();
    private final Map<BlockKey, Integer> rewardChests = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    public FloorManager(
            RookieMinesPlugin plugin,
            World world,
            GeneratorSettings settings,
            FloorStateStore floorStore,
            PlayerProgressStore progressStore
    ) {
        this.plugin = plugin;
        this.world = world;
        this.settings = settings;
        this.generator = new FloorPlanGenerator(settings);
        this.floorStore = floorStore;
        this.progressStore = progressStore;
        this.floorKey = new NamespacedKey(plugin, "current_floor");
        this.skullKey = new NamespacedKey(plugin, "skull_key");
        this.blocksPerTick = Math.max(500, plugin.getConfig().getInt("generation.blocks-per-tick", 18000));
        this.ladderBaseChance = plugin.getConfig().getDouble("ladder.base-chance", 0.02);
        this.noEnemiesBonus = plugin.getConfig().getDouble("ladder.no-enemies-bonus", 0.04);
        this.shaftChance = plugin.getConfig().getDouble("ladder.skull-shaft-chance", 0.20);
        restoreIndexes();
    }

    private void restoreIndexes() {
        for (Map.Entry<Integer, FloorState> entry : floorStore.all().entrySet()) {
            FloorState state = entry.getValue();
            if (state.ladder() != null) {
                int destination = state.ladderDestination() > 0
                        ? state.ladderDestination()
                        : entry.getKey() + 1;
                ladders.put(BlockKey.of(state.ladder()), new LadderTarget(
                        entry.getKey(), destination, state.ladderShaft(), Math.max(1, destination - entry.getKey())
                ));
            }
            if (state.rewardChest() != null) {
                rewardChests.put(BlockKey.of(state.rewardChest()), entry.getKey());
            }
        }
    }

    public World world() {
        return world;
    }

    public GeneratorSettings settings() {
        return settings;
    }

    public FloorPlanGenerator generator() {
        return generator;
    }

    public long currentDay() {
        long forced = plugin.getConfig().getLong("forced-day", -1L);
        if (forced >= 0) {
            return forced;
        }
        String sourceName = plugin.getConfig().getString("world.source-day-world", "world");
        World source = Bukkit.getWorld(sourceName);
        if (source == null) {
            source = Bukkit.getWorlds().getFirst();
        }
        return Math.floorDiv(source.getFullTime(), 24000L);
    }

    public void setForcedDay(Long day) {
        plugin.getConfig().set("forced-day", day == null ? -1L : day);
        plugin.saveConfig();
    }

    public long generationKeyFor(int floor) {
        if (floor == GenerationRules.SKULL_LOBBY_FLOOR) {
            return 0L;
        }
        if (GenerationRules.isSkullFloor(floor)) {
            return floorStore.skullSession();
        }
        return currentDay();
    }

    public FloorDescriptor describe(int floor) {
        return generator.describe(world.getSeed(), generationKeyFor(floor), floor);
    }

    public FloorState state(int floor) {
        return floorStore.get(floor);
    }

    public boolean isGenerating(int floor) {
        return jobs.containsKey(floor);
    }

    public void enter(Player player, int floor, boolean bypassProgression) {
        if (floor < 1 || floor > 10000) {
            message(player, "Floor must be between 1 and 10000.", NamedTextColor.RED);
            return;
        }
        if (!bypassProgression && !canEnter(player, floor)) {
            message(player, "That floor is not unlocked. Use floor 1 or an unlocked elevator checkpoint.", NamedTextColor.RED);
            return;
        }
        long generationKey = generationKeyFor(floor);
        FloorState existing = floorStore.get(floor);
        if (existing != null
                && existing.descriptor().generationKey() == generationKey
                && existing.descriptor().canvasSize() == settings.maxSize()
                && existing.descriptor().height() == settings.height()) {
            teleportIntoFloor(player, existing.descriptor());
            return;
        }

        waitingPlayers.computeIfAbsent(floor, ignored -> new HashSet<>()).add(player.getUniqueId());
        if (jobs.containsKey(floor)) {
            message(player, "Floor " + floor + " is already being generated; you were added to the queue.", NamedTextColor.YELLOW);
            return;
        }
        FloorDescriptor descriptor = generator.describe(world.getSeed(), generationKey, floor);
        jobs.put(floor, new GenerationJob(descriptor, JobStage.PLANNING));
        message(player, "Generating floor " + floor + " (" + descriptor.theme() + ")...", NamedTextColor.YELLOW);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                FloorPlan plan = generator.generate(descriptor);
                Bukkit.getScheduler().runTask(plugin, () -> preloadChunks(plan));
            } catch (Throwable throwable) {
                Bukkit.getScheduler().runTask(plugin, () -> failGeneration(floor, throwable));
            }
        });
    }

    private boolean canEnter(Player player, int floor) {
        if (floor == 1) return true;
        if (floor == GenerationRules.SKULL_LOBBY_FLOOR) return hasSkullKey(player);
        if (!GenerationRules.isNormalMine(floor)) return false;
        return floor % 5 == 0 && floor <= progressStore.deepestElevator(player.getUniqueId());
    }

    private void preloadChunks(FloorPlan plan) {
        GenerationJob job = jobs.get(plan.descriptor().floor());
        if (job == null) return;
        job.stage = JobStage.LOADING;
        int minChunkX = Math.floorDiv(plan.descriptor().originX(), 16);
        int maxChunkX = Math.floorDiv(plan.descriptor().originX() + plan.descriptor().canvasSize() - 1, 16);
        int minChunkZ = Math.floorDiv(plan.descriptor().originZ(), 16);
        int maxChunkZ = Math.floorDiv(plan.descriptor().originZ() + plan.descriptor().canvasSize() - 1, 16);
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                futures.add(world.getChunkAtAsync(x, z, true));
            }
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ignored, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        failGeneration(plan.descriptor().floor(), throwable);
                        return;
                    }
                    List<Chunk> chunks = futures.stream().map(CompletableFuture::join).toList();
                    for (Chunk chunk : chunks) {
                        chunk.addPluginChunkTicket(plugin);
                    }
                    applyPlan(plan, chunks);
                })
        );
    }

    private void applyPlan(FloorPlan plan, List<Chunk> chunks) {
        int floor = plan.descriptor().floor();
        GenerationJob job = jobs.get(floor);
        if (job == null) return;
        job.stage = JobStage.BUILDING;
        clearFloorEntities(plan.descriptor());
        removeIndexesForFloor(floor);

        new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                try {
                    int changed = 0;
                    long deadline = System.nanoTime() + 8_000_000L;
                    while (index < plan.volume()
                            && changed < blocksPerTick
                            && System.nanoTime() < deadline) {
                        BlockPoint point = plan.worldPointAtIndex(index);
                        Material target = plan.materialAtIndex(index);
                        Block block = world.getBlockAt(point.x(), point.y(), point.z());
                        if (block.getType() != target) {
                            block.setType(target, false);
                        }
                        index++;
                        changed++;
                    }
                    if (index >= plan.volume()) {
                        cancel();
                        finishPlan(plan, chunks);
                    }
                } catch (Throwable throwable) {
                    cancel();
                    for (Chunk chunk : chunks) {
                        chunk.removePluginChunkTicket(plugin);
                    }
                    failGeneration(floor, throwable);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void finishPlan(FloorPlan plan, List<Chunk> chunks) {
        FloorDescriptor descriptor = plan.descriptor();
        FloorState previous = floorStore.get(descriptor.floor());
        int generationCount = previous == null ? 1 : previous.generationCount() + 1;
        FloorState state = new FloorState(
                descriptor,
                plan.initialStones(),
                plan.mobs().size(),
                generationCount,
                plan.layoutHash()
        );

        for (ChestSpec chest : plan.chests()) {
            if (chest.kind() == ChestSpec.ChestKind.REWARD) {
                state.setRewardChest(chest.point());
                rewardChests.put(BlockKey.of(chest.point()), descriptor.floor());
            } else {
                fillContainer(chest, descriptor);
            }
        }
        for (MobSpec mob : plan.mobs()) {
            Entity entity = world.spawnEntity(mob.point().center(world), mob.type());
            entity.addScoreboardTag("rookie_mines");
            entity.addScoreboardTag(FLOOR_TAG_PREFIX + descriptor.floor());
            if (entity instanceof LivingEntity living) {
                living.setRemoveWhenFarAway(false);
                living.setPersistent(true);
            }
        }
        if (plan.prebuiltLadder() != null) {
            int destination = descriptor.floor() == GenerationRules.SKULL_LOBBY_FLOOR
                    ? GenerationRules.FIRST_SKULL_FLOOR
                    : descriptor.floor() + 1;
            registerLadder(state, plan.prebuiltLadder(), destination, false);
        }

        floorStore.put(descriptor.floor(), state);
        floorStore.save();
        jobs.remove(descriptor.floor());

        Set<UUID> queued = waitingPlayers.remove(descriptor.floor());
        if (queued != null) {
            for (UUID uuid : queued) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    teleportIntoFloor(player, descriptor);
                    message(player, "Floor " + descriptor.floor() + " ready: " + descriptor.theme()
                            + (descriptor.dark() ? " (dark)" : "") + ".", NamedTextColor.GREEN);
                }
            }
        }
        plugin.getLogger().info("Generated floor " + descriptor.floor() + " seed=" + descriptor.seed()
                + " hash=" + state.layoutHash() + " blocks=" + plan.volume());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Chunk chunk : chunks) {
                chunk.removePluginChunkTicket(plugin);
            }
        }, 40L);
    }

    private void fillContainer(ChestSpec chest, FloorDescriptor descriptor) {
        Block block = world.getBlockAt(chest.point().x(), chest.point().y(), chest.point().z());
        if (!(block.getState() instanceof Container container)) return;
        container.getInventory().clear();
        java.util.Random random = new java.util.Random(descriptor.seed() ^ chest.point().hashCode());
        container.getInventory().addItem(new ItemStack(Material.COAL, 1 + random.nextInt(4)));
        Material ore = switch (descriptor.theme()) {
            case EARTH -> Material.RAW_COPPER;
            case FROST -> Material.RAW_IRON;
            case LAVA -> Material.RAW_GOLD;
            case SKULL, LOBBY -> Material.DIAMOND;
        };
        if (random.nextDouble() < 0.70) {
            container.getInventory().addItem(new ItemStack(ore, 1 + random.nextInt(3)));
        }
        if (random.nextDouble() < 0.35) {
            container.getInventory().addItem(new ItemStack(Material.BREAD, 1 + random.nextInt(2)));
        }
    }

    private void clearFloorEntities(FloorDescriptor descriptor) {
        BoundingBox box = new BoundingBox(
                descriptor.originX(), descriptor.baseY() - 2, descriptor.originZ(),
                descriptor.originX() + descriptor.canvasSize(), descriptor.baseY() + descriptor.height() + 4,
                descriptor.originZ() + descriptor.canvasSize()
        );
        for (Entity entity : world.getNearbyEntities(box, entity -> entity.getScoreboardTags().contains("rookie_mines"))) {
            entity.remove();
        }
    }

    private void failGeneration(int floor, Throwable throwable) {
        jobs.remove(floor);
        plugin.getLogger().log(Level.SEVERE, "Failed to generate mine floor " + floor, throwable);
        Set<UUID> queued = waitingPlayers.remove(floor);
        if (queued != null) {
            for (UUID uuid : queued) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    message(player, "Floor generation failed; check the server log.", NamedTextColor.RED);
                }
            }
        }
    }

    private void teleportIntoFloor(Player player, FloorDescriptor descriptor) {
        if (!player.getWorld().equals(world)) {
            returnLocations.put(player.getUniqueId(), player.getLocation().clone());
        }
        Location target = descriptor.spawnPoint().center(world);
        target.setYaw(0.0f);
        target.setPitch(0.0f);
        player.teleport(target);
        player.setFallDistance(0.0f);
        player.getPersistentDataContainer().set(floorKey, PersistentDataType.INTEGER, descriptor.floor());
        progressStore.reach(player.getUniqueId(), descriptor.floor());
        progressStore.save();
    }

    public void leave(Player player) {
        Integer oldFloor = currentFloor(player);
        Location destination = returnLocations.remove(player.getUniqueId());
        if (destination == null || destination.getWorld() == null) {
            destination = Bukkit.getWorlds().getFirst().getSpawnLocation();
        }
        player.getPersistentDataContainer().remove(floorKey);
        player.teleport(destination);
        if (oldFloor != null && GenerationRules.isSkullFloor(oldFloor)) {
            Bukkit.getScheduler().runTask(plugin, this::endSkullSessionIfEmpty);
        }
    }

    public Integer currentFloor(Player player) {
        if (!player.getWorld().equals(world)) return null;
        Integer stored = player.getPersistentDataContainer().get(floorKey, PersistentDataType.INTEGER);
        if (stored != null) {
            FloorState storedState = floorStore.get(stored);
            if (storedState != null && storedState.descriptor().contains(
                    player.getLocation().getBlockX(),
                    player.getLocation().getBlockY(),
                    player.getLocation().getBlockZ()
            )) {
                return stored;
            }
        }
        int floor = (int) Math.round(player.getLocation().getZ() / settings.floorSpacing());
        FloorState state = floorStore.get(floor);
        if (state != null && state.descriptor().contains(
                player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ())) {
            return floor;
        }
        return null;
    }

    public void reconcilePlayerLocation(Player player) {
        Integer markedFloor = player.getPersistentDataContainer().get(floorKey, PersistentDataType.INTEGER);
        Integer actualFloor = currentFloor(player);
        if (actualFloor == null) {
            player.getPersistentDataContainer().remove(floorKey);
            returnLocations.remove(player.getUniqueId());
        } else if (!actualFloor.equals(markedFloor)) {
            player.getPersistentDataContainer().set(floorKey, PersistentDataType.INTEGER, actualFloor);
        }

        if (markedFloor != null
                && GenerationRules.isSkullFloor(markedFloor)
                && (actualFloor == null || !GenerationRules.isSkullFloor(actualFloor))) {
            Bukkit.getScheduler().runTask(plugin, this::endSkullSessionIfEmpty);
        }
    }

    public boolean handleStoneBreak(Player player, Block block) {
        Integer floor = currentFloor(player);
        if (floor == null || !FloorPlanGenerator.isCountedStone(block.getType())) {
            return false;
        }
        FloorState state = floorStore.get(floor);
        if (state == null || state.ladder() != null || floor == 120 || floor == 121) {
            return false;
        }
        state.decrementStone();
        PotionEffect luck = player.getPotionEffect(PotionEffectType.LUCK);
        int luckLevel = luck == null ? 0 : luck.getAmplifier() + 1;
        double chance = GenerationRules.ladderChance(
                ladderBaseChance,
                state.remainingStones(),
                luckLevel,
                state.descriptor().dailyLuck(),
                state.enemyCount() <= 0,
                noEnemiesBonus
        );
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            Bukkit.getScheduler().runTask(plugin, () -> spawnLadder(floor, player.getLocation(), false));
        }
        floorStore.save();
        return true;
    }

    public void handleMineMonsterDeath(Entity entity, Player killer) {
        Integer floor = parseFloorTag(entity);
        if (floor == null) return;
        FloorState state = floorStore.get(floor);
        if (state == null) return;
        state.decrementEnemy();
        if (state.ladder() == null && floor != 120 && floor != 121) {
            double chance = state.enemyCount() <= 0 ? 1.0 : 0.15;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                spawnLadder(floor, killer.getLocation(), false);
            }
        }
        floorStore.save();
    }

    public boolean handleLadderInteraction(Player player, Block block) {
        LadderTarget target = ladders.get(BlockKey.of(block.getX(), block.getY(), block.getZ()));
        if (target == null) return false;
        Integer current = currentFloor(player);
        if (current == null || current != target.sourceFloor()) return false;
        if (target.shaft()) {
            double newHealth = Math.max(1.0, player.getHealth() - target.jump() * 3.0);
            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) == null
                    ? 20.0 : player.getAttribute(Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.min(maxHealth, newHealth));
            message(player, "You fell " + target.jump() + " floors and took " + (target.jump() * 3) + " damage.", NamedTextColor.RED);
        }
        enter(player, target.destinationFloor(), true);
        return true;
    }

    public boolean handleRewardChest(Player player, Block block) {
        Integer floor = rewardChests.get(BlockKey.of(block.getX(), block.getY(), block.getZ()));
        if (floor == null) return false;
        if (progressStore.hasClaimed(player.getUniqueId(), floor)) {
            message(player, "You already claimed this floor reward.", NamedTextColor.YELLOW);
            return true;
        }
        ItemStack reward = rewardForFloor(floor);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(reward);
        for (ItemStack item : overflow.values()) {
            world.dropItemNaturally(player.getLocation(), item);
        }
        progressStore.claim(player.getUniqueId(), floor);
        message(player, "Claimed floor " + floor + " reward: " + reward.getType() + ".", NamedTextColor.GREEN);
        return true;
    }

    private ItemStack rewardForFloor(int floor) {
        Material material = switch (floor) {
            case 10 -> Material.LEATHER_BOOTS;
            case 20 -> Material.IRON_SWORD;
            case 40 -> Material.BOW;
            case 50 -> Material.IRON_BOOTS;
            case 60 -> Material.DIAMOND_SWORD;
            case 70 -> Material.CROSSBOW;
            case 80 -> Material.DIAMOND_BOOTS;
            case 90 -> Material.NETHERITE_SWORD;
            case 100 -> Material.ENCHANTED_GOLDEN_APPLE;
            case 110 -> Material.NETHERITE_BOOTS;
            case 120 -> Material.TRIAL_KEY;
            default -> Material.DIAMOND;
        };
        ItemStack item = new ItemStack(material);
        if (floor == 120) {
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("Skull Key", NamedTextColor.GOLD));
            meta.getPersistentDataContainer().set(skullKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean hasSkullKey(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() != Material.TRIAL_KEY || !item.hasItemMeta()) continue;
            Byte value = item.getItemMeta().getPersistentDataContainer().get(skullKey, PersistentDataType.BYTE);
            if (value != null && value == (byte) 1) return true;
        }
        return false;
    }

    public void forceLadder(Player player, Integer explicitFloor) {
        int floor = explicitFloor == null ? java.util.Objects.requireNonNullElse(currentFloor(player), 1) : explicitFloor;
        FloorState state = floorStore.get(floor);
        if (state == null) {
            message(player, "Floor " + floor + " is not generated.", NamedTextColor.RED);
            return;
        }
        state.setRemainingStones(0);
        spawnLadder(floor, player.getLocation(), true);
        floorStore.save();
    }

    public String createDigFixture(Player player) {
        Integer floor = currentFloor(player);
        if (floor == null) {
            return "{\"ok\":false,\"error\":\"not-in-mine\"}";
        }
        FloorState state = floorStore.get(floor);
        if (state == null) {
            return "{\"ok\":false,\"error\":\"missing-floor\"}";
        }
        if (state.ladder() != null) {
            world.getBlockAt(state.ladder().x(), state.ladder().y(), state.ladder().z()).setType(Material.AIR, false);
            ladders.remove(BlockKey.of(state.ladder()));
            state.clearLadder();
        }
        Location location = player.getLocation();
        int y = state.descriptor().baseY() + 3;
        int x = location.getBlockX() + 2;
        int z = location.getBlockZ();
        Block fixture = world.getBlockAt(x, y, z);
        fixture.setType(Material.STONE, false);
        state.setRemainingStones(1);
        floorStore.save();
        return "{\"ok\":true,\"floor\":" + floor + ",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}";
    }

    private void spawnLadder(int floor, Location preferred, boolean forced) {
        FloorState state = floorStore.get(floor);
        if (state == null || state.ladder() != null || floor == 120) return;
        BlockPoint point = findLadderSpot(state.descriptor(), preferred);
        Block block = world.getBlockAt(point.x(), point.y(), point.z());
        block.setType(Material.OAK_TRAPDOOR, false);
        boolean shaft = !forced && GenerationRules.isSkullFloor(floor)
                && ThreadLocalRandom.current().nextDouble() < shaftChance;
        int jump = 1;
        if (shaft) {
            jump = ThreadLocalRandom.current().nextDouble() < 0.10
                    ? ThreadLocalRandom.current().nextInt(5, 16)
                    : ThreadLocalRandom.current().nextInt(3, 9);
        }
        int destination = floor == GenerationRules.SKULL_LOBBY_FLOOR
                ? GenerationRules.FIRST_SKULL_FLOOR : floor + jump;
        registerLadder(state, point, destination, shaft);
        floorStore.save();
        for (Player online : world.getPlayers()) {
            Integer current = currentFloor(online);
            if (current != null && current == floor) {
                message(online, shaft ? "A shaft appeared!" : "A ladder appeared!", NamedTextColor.GOLD);
            }
        }
    }

    private void registerLadder(FloorState state, BlockPoint point, int destination, boolean shaft) {
        state.setLadder(point);
        state.setLadderTarget(destination, shaft);
        ladders.put(BlockKey.of(point), new LadderTarget(
                state.descriptor().floor(), destination, shaft, Math.max(1, destination - state.descriptor().floor())
        ));
    }

    private BlockPoint findLadderSpot(FloorDescriptor descriptor, Location preferred) {
        int startX = preferred.getWorld() != null && preferred.getWorld().equals(world)
                ? preferred.getBlockX() : 0;
        int startZ = preferred.getWorld() != null && preferred.getWorld().equals(world)
                ? preferred.getBlockZ() : descriptor.centerZ();
        int y = descriptor.baseY() + 2;
        for (int radius = 2; radius <= 9; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    Block candidate = world.getBlockAt(startX + dx, y, startZ + dz);
                    if (candidate.getType().isAir() && world.getBlockAt(startX + dx, y - 1, startZ + dz).getType().isSolid()) {
                        return new BlockPoint(candidate.getX(), candidate.getY(), candidate.getZ());
                    }
                }
            }
        }
        return new BlockPoint(2, y, descriptor.centerZ());
    }

    public void invalidate(int floor) {
        FloorState state = floorStore.get(floor);
        if (state != null) {
            removeIndexesForFloor(floor);
            floorStore.remove(floor);
            floorStore.save();
        }
    }

    private void removeIndexesForFloor(int floor) {
        ladders.entrySet().removeIf(entry -> entry.getValue().sourceFloor() == floor);
        rewardChests.entrySet().removeIf(entry -> entry.getValue() == floor);
    }

    public void endSkullSessionIfEmpty() {
        boolean anyInside = Bukkit.getOnlinePlayers().stream()
                .map(this::currentFloor)
                .anyMatch(floor -> floor != null && GenerationRules.isSkullFloor(floor));
        if (anyInside) return;
        long next = floorStore.nextSkullSession();
        for (Integer floor : new ArrayList<>(floorStore.all().keySet())) {
            if (GenerationRules.isSkullFloor(floor)) {
                removeIndexesForFloor(floor);
            }
        }
        plugin.getLogger().info("Skull Cavern session ended; next session=" + next);
    }

    private Integer parseFloorTag(Entity entity) {
        for (String tag : entity.getScoreboardTags()) {
            if (!tag.startsWith(FLOOR_TAG_PREFIX)) continue;
            try {
                return Integer.parseInt(tag.substring(FLOOR_TAG_PREFIX.length()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public String statusJson(Player player) {
        Integer floor = currentFloor(player);
        if (floor == null) {
            return "{\"ok\":true,\"inMine\":false}";
        }
        FloorState state = floorStore.get(floor);
        if (state == null) {
            return "{\"ok\":false,\"inMine\":true,\"floor\":" + floor + ",\"state\":\"MISSING\"}";
        }
        FloorDescriptor d = state.descriptor();
        return "{\"ok\":true,\"inMine\":true,\"floor\":" + floor
                + ",\"theme\":\"" + d.theme() + "\",\"day\":" + d.generationKey()
                + ",\"seed\":" + d.seed() + ",\"dark\":" + d.dark()
                + ",\"size\":" + d.size() + ",\"remainingStones\":" + state.remainingStones()
                + ",\"enemies\":" + state.enemyCount() + ",\"generationCount\":" + state.generationCount()
                + ",\"layoutHash\":\"" + state.layoutHash() + "\",\"ladder\":" + (state.ladder() != null) + "}";
    }

    public String describeJson(int floor) {
        FloorDescriptor d = describe(floor);
        return "{\"ok\":true,\"floor\":" + floor + ",\"theme\":\"" + d.theme()
                + "\",\"generationKey\":" + d.generationKey() + ",\"seed\":" + d.seed()
                + ",\"dark\":" + d.dark() + ",\"size\":" + d.size() + "}";
    }

    public int deepestElevator(Player player) {
        return progressStore.deepestElevator(player.getUniqueId());
    }

    public void shutdown() {
        floorStore.save();
        progressStore.save();
    }

    private void message(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(PREFIX + text, color));
    }

    private record BlockKey(int x, int y, int z) {
        private static BlockKey of(BlockPoint point) {
            return of(point.x(), point.y(), point.z());
        }

        private static BlockKey of(int x, int y, int z) {
            return new BlockKey(x, y, z);
        }
    }

    private record LadderTarget(int sourceFloor, int destinationFloor, boolean shaft, int jump) {
    }

    private static final class GenerationJob {
        private final FloorDescriptor descriptor;
        private JobStage stage;

        private GenerationJob(FloorDescriptor descriptor, JobStage stage) {
            this.descriptor = descriptor;
            this.stage = stage;
        }
    }

    private enum JobStage {
        PLANNING,
        LOADING,
        BUILDING
    }
}
