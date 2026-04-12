package me.bintanq.command;

import me.bintanq.VisantaraEventHandler;
import me.bintanq.dummy.DummyEntity;
import me.bintanq.manager.DummyManager;
import me.bintanq.manager.MessageManager;
import me.bintanq.naturaldrops.NaturalDropManager;
import me.bintanq.cinematic.ForgeCinematicListener;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class VHandlerCommand implements CommandExecutor, TabCompleter {

    private final VisantaraEventHandler plugin;

    public VHandlerCommand(VisantaraEventHandler plugin) {
        this.plugin = plugin;
    }

    private MessageManager msg() {
        return plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg().getNoPrefix("players-only"));
            return true;
        }

        if (!player.hasPermission("visantara.use")) {
            player.sendMessage(msg().get("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "dummy" -> handleDummy(player, args);
            case "drop" -> handleDrop(player, args);
            case "reload" -> handleReload(player);
            case "cinematic" -> handleCinematic(player, args);
            default -> sendUsage(player);
        }

        return true;
    }

    private void handleDummy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(msg().get("dummy.usage"));
            return;
        }

        DummyManager manager = plugin.getDummyManager();

        switch (args[1].toLowerCase()) {
            case "spawn" -> {
                if (!player.hasPermission("visantara.dummy.spawn")) {
                    player.sendMessage(msg().get("no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(msg().get("dummy.spawn.usage"));
                    return;
                }
                switch (args[2].toLowerCase()) {
                    case "mob" -> {
                        DummyEntity dummy = manager.spawnMobDummy(player.getLocation());
                        player.sendMessage(msg().get("dummy.spawn.mob",
                                MessageManager.of("uuid", dummy.getUuid().toString())));
                    }
                    case "player" -> {
                        DummyEntity dummy = manager.spawnPlayerDummy(player.getLocation());
                        player.sendMessage(msg().get("dummy.spawn.player",
                                MessageManager.of("uuid", dummy.getUuid().toString())));
                    }
                    default -> player.sendMessage(msg().get("dummy.spawn.unknown-type"));
                }
            }
            case "remove" -> {
                if (!player.hasPermission("visantara.dummy.remove")) {
                    player.sendMessage(msg().get("no-permission"));
                    return;
                }
                double radius = plugin.getConfig().getDouble("settings.remove-radius", 20.0);
                int removed = manager.removeDummiesInRadius(player.getLocation(), radius);
                player.sendMessage(msg().get("dummy.remove.success",
                        MessageManager.of("count", String.valueOf(removed), "radius", String.valueOf((int) radius))));
            }
            default -> player.sendMessage(msg().get("dummy.usage"));
        }
    }

    private void handleDrop(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(msg().get("drop.usage"));
            return;
        }

        NaturalDropManager dropManager = plugin.getNaturalDropManager();

        switch (args[1].toLowerCase()) {
            case "check" -> {
                if (!player.hasPermission("visantara.drop.check")) {
                    player.sendMessage(msg().get("no-permission"));
                    return;
                }
                Block target = player.getTargetBlockExact(8);
                if (target == null) {
                    player.sendMessage(msg().get("drop.no-block"));
                    return;
                }
                boolean placed = dropManager.isPlayerPlaced(target.getLocation());
                String status = msg().getRaw(placed ? "drop.check.status-placed" : "drop.check.status-natural");
                player.sendMessage(msg().get("drop.check.result", MessageManager.of(
                        "block", target.getType().name(),
                        "status", status,
                        "x", String.valueOf(target.getX()),
                        "y", String.valueOf(target.getY()),
                        "z", String.valueOf(target.getZ())
                )));
            }
            case "mark" -> {
                if (!player.hasPermission("visantara.drop.mark")) {
                    player.sendMessage(msg().get("no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(msg().get("drop.mark.usage"));
                    return;
                }
                Block target = player.getTargetBlockExact(8);
                if (target == null) {
                    player.sendMessage(msg().get("drop.no-block"));
                    return;
                }
                switch (args[2].toLowerCase()) {
                    case "placed" -> {
                        dropManager.markPlayerPlaced(target.getLocation());
                        player.sendMessage(msg().get("drop.mark.placed", MessageManager.of(
                                "block", target.getType().name(),
                                "x", String.valueOf(target.getX()),
                                "y", String.valueOf(target.getY()),
                                "z", String.valueOf(target.getZ())
                        )));
                    }
                    case "natural" -> {
                        dropManager.forceMarkNatural(target.getLocation());
                        player.sendMessage(msg().get("drop.mark.natural", MessageManager.of(
                                "block", target.getType().name(),
                                "x", String.valueOf(target.getX()),
                                "y", String.valueOf(target.getY()),
                                "z", String.valueOf(target.getZ())
                        )));
                    }
                    default -> player.sendMessage(msg().get("drop.mark.unknown-type"));
                }
            }
            default -> player.sendMessage(msg().get("drop.usage"));
        }
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("visantara.use")) {
            player.sendMessage(msg().get("no-permission"));
            return;
        }
        plugin.reload();
        player.sendMessage(msg().get("reload.success"));
    }

    private void handleCinematic(Player player, String[] args) {
        if (!player.hasPermission("visantara.cinematic.test")) {
            player.sendMessage(msg().get("no-permission"));
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("test")) {
            player.sendMessage("§eUsage: /vhandler cinematic test <typewriter-page-id>");
            return;
        }
        plugin.getForgeCinematicListener().triggerTest(player, args[2]);
    }

    private void sendUsage(Player player) {
        player.sendMessage(msg().get("usage"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("dummy", "drop", "reload", "cinematic");
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "dummy" -> Arrays.asList("spawn", "remove");
                case "drop" -> Arrays.asList("check", "mark");
                case "cinematic" -> Arrays.asList("test");
                default -> Collections.emptyList();
            };
        }
        if (args.length == 3) {
            return switch (args[0].toLowerCase()) {
                case "dummy" -> args[1].equalsIgnoreCase("spawn") ? Arrays.asList("mob", "player") : Collections.emptyList();
                case "drop" -> args[1].equalsIgnoreCase("mark") ? Arrays.asList("placed", "natural") : Collections.emptyList();
                default -> Collections.emptyList();
            };
        }
        return Collections.emptyList();
    }
}