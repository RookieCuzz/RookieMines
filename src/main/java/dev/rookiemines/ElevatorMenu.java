package dev.rookiemines;

import dev.rookiemines.mine.FloorManager;
import dev.rookiemines.mine.GenerationRules;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ElevatorMenu {
    private static final String PREFIX = "[菜鸟矿洞] ";
    private static final int INVENTORY_SIZE = 45;
    private static final int INFO_SLOT = 4;
    private static final int CLOSE_SLOT = 44;
    private static final int[] DESTINATION_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40
    };
    private static final List<Integer> ALL_DESTINATIONS = createAllDestinations();
    private static final Map<Integer, Integer> DESTINATIONS_BY_SLOT = createDestinationLayout();

    private final RookieMinesPlugin plugin;
    private final FloorManager floors;
    private final Map<UUID, ElevatorHolder> activeMenus = new HashMap<>();

    public ElevatorMenu(RookieMinesPlugin plugin, FloorManager floors) {
        this.plugin = plugin;
        this.floors = floors;
    }

    public void open(Player player) {
        if (!player.hasPermission("rookiemines.use")) {
            message(player, "你没有使用菜鸟矿洞的权限。", NamedTextColor.RED);
            return;
        }
        Integer currentFloor = floors.currentFloor(player);
        if (!isInsideNormalMine(player, currentFloor)) {
            message(player, "电梯 GUI 只能在普通矿洞 1–120 层内打开。", NamedTextColor.RED);
            return;
        }

        int deepest = floors.deepestElevator(player);
        ElevatorHolder holder = new ElevatorHolder(player.getUniqueId(), DESTINATIONS_BY_SLOT);
        Inventory inventory = Bukkit.createInventory(
                holder,
                INVENTORY_SIZE,
                plain("菜鸟矿洞 · 电梯", NamedTextColor.DARK_AQUA)
        );
        holder.attach(inventory);

        ItemStack filler = simpleItem(Material.BLACK_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        for (Map.Entry<Integer, Integer> entry : DESTINATIONS_BY_SLOT.entrySet()) {
            int floor = entry.getValue();
            boolean unlocked = isUnlocked(floor, deepest);
            inventory.setItem(entry.getKey(), destinationItem(floor, currentFloor, unlocked));
        }
        inventory.setItem(INFO_SLOT, informationItem(currentFloor, deepest));
        inventory.setItem(CLOSE_SLOT, simpleItem(Material.BARRIER, "关闭电梯", NamedTextColor.RED));

        InventoryView view = player.openInventory(inventory);
        if (view != null && view.getTopInventory() == inventory) {
            activeMenus.put(player.getUniqueId(), holder);
        } else {
            holder.invalidate();
        }
    }

    public boolean handleClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ElevatorHolder holder)) {
            return false;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer().equals(player.getUniqueId())
                || activeMenus.get(player.getUniqueId()) != holder
                || event.getClick() != ClickType.LEFT) {
            return true;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return true;
        }
        if (rawSlot == CLOSE_SLOT) {
            if (holder.claim()) {
                Bukkit.getScheduler().runTask(plugin, () -> closeIfCurrent(player, holder));
            }
            return true;
        }

        Integer destination = holder.destination(rawSlot);
        if (destination == null) {
            return true;
        }
        int deepest = floors.deepestElevator(player);
        if (!isUnlocked(destination, deepest)) {
            message(player, "第 " + destination + " 层尚未解锁；先亲自到达该检查点。", NamedTextColor.RED);
            return true;
        }
        if (!holder.claim()) {
            return true;
        }

        Bukkit.getScheduler().runTask(plugin, () -> enterIfCurrent(player, holder, destination));
        return true;
    }

    public boolean handleDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ElevatorHolder)) {
            return false;
        }
        event.setCancelled(true);
        return true;
    }

    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ElevatorHolder holder)) {
            return;
        }
        activeMenus.remove(holder.viewer(), holder);
        holder.invalidate();
    }

    public void handleQuit(Player player) {
        ElevatorHolder holder = activeMenus.remove(player.getUniqueId());
        if (holder != null) {
            holder.invalidate();
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, ElevatorHolder> entry : List.copyOf(activeMenus.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && isViewing(player, entry.getValue())) {
                player.closeInventory();
            }
            entry.getValue().invalidate();
        }
        activeMenus.clear();
    }

    static List<Integer> destinationsFor(int deepestElevator) {
        int deepest = Math.max(0, Math.min(120, deepestElevator));
        List<Integer> destinations = new ArrayList<>();
        destinations.add(1);
        for (int floor = 5; floor <= deepest; floor += 5) {
            destinations.add(floor);
        }
        return List.copyOf(destinations);
    }

    static List<Integer> allDestinations() {
        return ALL_DESTINATIONS;
    }

    static int slotForDestination(int floor) {
        for (Map.Entry<Integer, Integer> entry : DESTINATIONS_BY_SLOT.entrySet()) {
            if (entry.getValue() == floor) {
                return entry.getKey();
            }
        }
        return -1;
    }

    private void enterIfCurrent(Player player, ElevatorHolder holder, int destination) {
        if (!player.isOnline()
                || activeMenus.get(player.getUniqueId()) != holder
                || !holder.isClaimed()
                || !isViewing(player, holder)) {
            return;
        }
        if (!player.hasPermission("rookiemines.use")) {
            closeIfCurrent(player, holder);
            message(player, "你的矿洞权限已失效，电梯操作已取消。", NamedTextColor.RED);
            return;
        }
        Integer currentFloor = floors.currentFloor(player);
        if (!isInsideNormalMine(player, currentFloor)) {
            closeIfCurrent(player, holder);
            message(player, "你已离开普通矿洞，电梯操作已取消。", NamedTextColor.RED);
            return;
        }

        activeMenus.remove(player.getUniqueId(), holder);
        player.closeInventory();
        floors.enter(player, destination, false);
    }

    private void closeIfCurrent(Player player, ElevatorHolder holder) {
        if (activeMenus.get(player.getUniqueId()) != holder || !isViewing(player, holder)) {
            return;
        }
        activeMenus.remove(player.getUniqueId(), holder);
        holder.invalidate();
        player.closeInventory();
    }

    private boolean isViewing(Player player, ElevatorHolder holder) {
        return player.getOpenInventory().getTopInventory().getHolder() == holder;
    }

    private boolean isInsideNormalMine(Player player, Integer currentFloor) {
        return player.getWorld().equals(floors.world())
                && currentFloor != null
                && GenerationRules.isNormalMine(currentFloor);
    }

    private static boolean isUnlocked(int floor, int deepest) {
        return floor == 1 || (floor >= 5 && floor <= Math.max(0, Math.min(120, deepest)));
    }

    private ItemStack destinationItem(int floor, int currentFloor, boolean unlocked) {
        Material icon;
        NamedTextColor color;
        String state;
        if (floor == currentFloor) {
            icon = Material.MINECART;
            color = NamedTextColor.GREEN;
            state = "当前楼层 · 点击回到本层出生点";
        } else if (unlocked) {
            icon = switch (regionFor(floor)) {
                case "泥土矿区" -> Material.COPPER_ORE;
                case "冰雪矿区" -> Material.IRON_ORE;
                case "熔岩矿区" -> Material.GOLD_ORE;
                default -> Material.RAIL;
            };
            color = NamedTextColor.GOLD;
            state = "已解锁 · 点击前往";
        } else {
            icon = Material.GRAY_STAINED_GLASS_PANE;
            color = NamedTextColor.DARK_GRAY;
            state = "尚未解锁 · 到达该层后开放";
        }

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        String name = floor == 1 ? "第 1 层 · 矿洞入口" : "第 " + floor + " 层 · 电梯站";
        meta.displayName(plain(name, color));
        meta.lore(List.of(
                plain(regionFor(floor), NamedTextColor.GRAY),
                plain(state, unlocked || floor == currentFloor ? NamedTextColor.AQUA : NamedTextColor.RED)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack informationItem(int currentFloor, int deepest) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("电梯状态", NamedTextColor.YELLOW));
        int clampedDeepest = Math.max(0, Math.min(120, deepest));
        String deepestLine = clampedDeepest >= 5
                ? "最深检查点：第 " + clampedDeepest + " 层"
                : "尚未解锁第 5 层检查点";
        meta.lore(List.of(
                plain("当前位置：第 " + currentFloor + " 层", NamedTextColor.GRAY),
                plain(deepestLine, NamedTextColor.GRAY),
                plain("绿色：当前 · 矿石：可达 · 灰色：锁定", NamedTextColor.DARK_GRAY)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack simpleItem(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain(name, color));
        item.setItemMeta(meta);
        return item;
    }

    private String regionFor(int floor) {
        if (floor < 40) return "泥土矿区";
        if (floor < 80) return "冰雪矿区";
        return "熔岩矿区";
    }

    private Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private void message(Player player, String text, NamedTextColor color) {
        player.sendMessage(Component.text(PREFIX + text, color));
    }

    private static List<Integer> createAllDestinations() {
        List<Integer> destinations = new ArrayList<>();
        destinations.add(1);
        for (int floor = 5; floor <= 120; floor += 5) {
            destinations.add(floor);
        }
        return List.copyOf(destinations);
    }

    private static Map<Integer, Integer> createDestinationLayout() {
        if (DESTINATION_SLOTS.length != ALL_DESTINATIONS.size()) {
            throw new IllegalStateException("Elevator layout must have one slot per destination");
        }
        Map<Integer, Integer> destinations = new HashMap<>();
        for (int index = 0; index < DESTINATION_SLOTS.length; index++) {
            destinations.put(DESTINATION_SLOTS[index], ALL_DESTINATIONS.get(index));
        }
        return Map.copyOf(destinations);
    }

    private static final class ElevatorHolder implements InventoryHolder {
        private final UUID viewer;
        private final Map<Integer, Integer> destinations;
        private Inventory inventory;
        private boolean claimed;
        private boolean valid = true;

        private ElevatorHolder(UUID viewer, Map<Integer, Integer> destinations) {
            this.viewer = viewer;
            this.destinations = destinations;
        }

        private void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        private UUID viewer() {
            return viewer;
        }

        private Integer destination(int slot) {
            return destinations.get(slot);
        }

        private boolean claim() {
            if (!valid || claimed) return false;
            claimed = true;
            return true;
        }

        private boolean isClaimed() {
            return valid && claimed;
        }

        private void invalidate() {
            valid = false;
        }

        @Override
        public Inventory getInventory() {
            if (inventory == null) {
                throw new IllegalStateException("Elevator inventory is not attached yet");
            }
            return inventory;
        }
    }
}
