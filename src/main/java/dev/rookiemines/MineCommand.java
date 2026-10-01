package dev.rookiemines;

import dev.rookiemines.mine.FloorManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MineCommand implements CommandExecutor, TabCompleter {
    private final RookieMinesPlugin plugin;
    private final FloorManager floors;
    private final ElevatorMenu elevatorMenu;

    public MineCommand(RookieMinesPlugin plugin, FloorManager floors, ElevatorMenu elevatorMenu) {
        this.plugin = plugin;
        this.floors = floors;
        this.elevatorMenu = elevatorMenu;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command currently requires a player target.");
            return true;
        }
        if (!player.hasPermission("rookiemines.use")) {
            player.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "enter" -> {
                Integer floor = args.length >= 2 ? parseFloor(player, args[1]) : 1;
                if (floor != null) floors.enter(player, floor, false);
            }
            case "leave" -> floors.leave(player);
            case "status" -> sendMachine(player, args.length >= 2 ? args[1] : null, floors.statusJson(player));
            case "elevator" -> {
                if (args.length < 2) {
                    elevatorMenu.open(player);
                    return true;
                }
                Integer floor = parseFloor(player, args[1]);
                if (floor != null) floors.enter(player, floor, false);
            }
            case "skull" -> floors.enter(player, 121, false);
            case "admin" -> admin(player, args);
            default -> help(player);
        }
        return true;
    }

    private void admin(Player player, String[] args) {
        if (!player.hasPermission("rookiemines.admin")) {
            player.sendMessage(Component.text("Admin permission required.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("/rmine admin <day|goto|regenerate|force-ladder|describe|fixture>", NamedTextColor.YELLOW));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "day" -> {
                if (args.length < 3) {
                    player.sendMessage(Component.text("Current mine day: " + floors.currentDay(), NamedTextColor.YELLOW));
                    return;
                }
                if (args[2].equalsIgnoreCase("auto")) {
                    floors.setForcedDay(null);
                    player.sendMessage(Component.text("Mine day now follows the source world.", NamedTextColor.GREEN));
                    return;
                }
                try {
                    long day = Long.parseLong(args[2]);
                    if (day < 0) throw new NumberFormatException();
                    floors.setForcedDay(day);
                    player.sendMessage(Component.text("Forced mine day set to " + day + ".", NamedTextColor.GREEN));
                } catch (NumberFormatException exception) {
                    player.sendMessage(Component.text("Day must be a non-negative integer or auto.", NamedTextColor.RED));
                }
            }
            case "goto" -> {
                if (args.length < 3) return;
                Integer floor = parseFloor(player, args[2]);
                if (floor != null) floors.enter(player, floor, true);
            }
            case "regenerate" -> {
                if (args.length < 3) return;
                Integer floor = parseFloor(player, args[2]);
                if (floor != null) {
                    floors.invalidate(floor);
                    floors.enter(player, floor, true);
                }
            }
            case "force-ladder" -> {
                Integer floor = args.length >= 3 ? parseFloor(player, args[2]) : null;
                if (args.length < 3 || floor != null) floors.forceLadder(player, floor);
            }
            case "describe" -> {
                if (args.length < 3) return;
                Integer floor = parseFloor(player, args[2]);
                if (floor != null) {
                    sendMachine(player, args.length >= 4 ? args[3] : null, floors.describeJson(floor));
                }
            }
            case "fixture" -> {
                boolean testMode = plugin.getConfig().getBoolean("test-mode", false)
                        || Boolean.getBoolean("rookieMines.e2e");
                if (!testMode) {
                    player.sendMessage(Component.text("The dig fixture is available only in test mode.", NamedTextColor.RED));
                    return;
                }
                sendMachine(player, args.length >= 3 ? args[2] : "fixture", floors.createDigFixture(player));
            }
            default -> player.sendMessage(Component.text("Unknown admin action.", NamedTextColor.RED));
        }
    }

    private Integer parseFloor(Player player, String value) {
        try {
            int floor = Integer.parseInt(value);
            if (floor < 1 || floor > 10000) throw new NumberFormatException();
            return floor;
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Floor must be an integer from 1 to 10000.", NamedTextColor.RED));
            return null;
        }
    }

    private void sendMachine(Player player, String correlation, String json) {
        boolean testMode = plugin.getConfig().getBoolean("test-mode", false)
                || Boolean.getBoolean("rookieMines.e2e");
        if (testMode && correlation != null && correlation.matches("[A-Za-z0-9_-]{1,40}")) {
            player.sendMessage(Component.text("[MINE_E2E:" + correlation + "]" + json, NamedTextColor.GRAY));
        } else {
            player.sendMessage(Component.text(json, NamedTextColor.GRAY));
        }
    }

    private void help(Player player) {
        player.sendMessage(Component.text("/rmine enter [floor] | leave | status | elevator [floor] | skull", NamedTextColor.AQUA));
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            return filter(List.of("enter", "leave", "status", "elevator", "skull", "admin"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return filter(List.of("day", "goto", "regenerate", "force-ladder", "describe", "fixture"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("elevator") && sender instanceof Player player) {
            List<String> floors = ElevatorMenu.destinationsFor(this.floors.deepestElevator(player))
                    .stream()
                    .map(String::valueOf)
                    .toList();
            return filter(floors, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> candidates, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.startsWith(prefix)) result.add(candidate);
        }
        return result;
    }
}
