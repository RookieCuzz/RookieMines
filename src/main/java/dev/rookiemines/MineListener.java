package dev.rookiemines;

import dev.rookiemines.mine.FloorManager;
import dev.rookiemines.mine.GenerationRules;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class MineListener implements Listener {
    private final RookieMinesPlugin plugin;
    private final FloorManager floors;

    public MineListener(RookieMinesPlugin plugin, FloorManager floors) {
        this.plugin = plugin;
        this.floors = floors;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.getBlock().getWorld().equals(floors.world())) return;
        floors.handleStoneBreak(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !event.getClickedBlock().getWorld().equals(floors.world())) return;
        Player player = event.getPlayer();
        if (floors.handleLadderInteraction(player, event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        if (floors.handleRewardChest(player, event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !event.getEntity().getWorld().equals(floors.world())) return;
        floors.handleMineMonsterDeath(event.getEntity(), killer);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Integer floor = floors.currentFloor(event.getPlayer());
        if (floor != null && GenerationRules.isSkullFloor(floor)) {
            Bukkit.getScheduler().runTask(plugin, floors::endSkullSessionIfEmpty);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        reconcileNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        reconcileNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        reconcileNextTick(event.getPlayer());
    }

    private void reconcileNextTick(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                floors.reconcilePlayerLocation(player);
            }
        });
    }
}
