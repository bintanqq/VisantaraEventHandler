package me.bintanq.command;

import me.bintanq.VisantaraEventHandler;
import me.bintanq.dummy.DummyEntity;
import me.bintanq.manager.DummyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DummyCommand implements CommandExecutor, TabCompleter {

    private static final Component PREFIX = LegacyComponentSerializer.legacyAmpersand()
            .deserialize("&8[&6VEH&8] ");

    private final VisantaraEventHandler plugin;

    public DummyCommand(VisantaraEventHandler plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("visantara.dummy")) {
            player.sendMessage(PREFIX.append(
                    Component.text("You do not have permission to use this command.", NamedTextColor.RED)
            ));
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn" -> handleSpawn(player, args);
            case "remove" -> handleRemove(player);
            case "reload" -> handleReload(player);
            default -> sendUsage(player);
        }

        return true;
    }

    private void handleSpawn(Player player, String[] args) {
        if (!player.hasPermission("visantara.dummy.spawn")) {
            player.sendMessage(PREFIX.append(
                    Component.text("You do not have permission to spawn dummies.", NamedTextColor.RED)
            ));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(PREFIX.append(
                    Component.text("Usage: /vdummy spawn <mob|player>", NamedTextColor.YELLOW)
            ));
            return;
        }

        DummyManager manager = plugin.getDummyManager();

        switch (args[1].toLowerCase()) {
            case "mob" -> {
                DummyEntity dummy = manager.spawnMobDummy(player.getLocation());
                player.sendMessage(PREFIX.append(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(
                                "&aSpawned a &eMob Dummy&a at your location. &7UUID: &f" + dummy.getUuid()
                        )
                ));
            }
            case "player" -> {
                DummyEntity dummy = manager.spawnPlayerDummy(player.getLocation());
                player.sendMessage(PREFIX.append(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(
                                "&aSpawned a &bPlayer Dummy&a at your location. &7UUID: &f" + dummy.getUuid()
                        )
                ));
            }
            default -> player.sendMessage(PREFIX.append(
                    Component.text("Unknown type. Use: mob or player", NamedTextColor.RED)
            ));
        }
    }

    private void handleRemove(Player player) {
        if (!player.hasPermission("visantara.dummy.remove")) {
            player.sendMessage(PREFIX.append(
                    Component.text("You do not have permission to remove dummies.", NamedTextColor.RED)
            ));
            return;
        }

        double radius = plugin.getConfig().getDouble("settings.remove-radius", 20.0);
        int removed = plugin.getDummyManager().removeDummiesInRadius(player.getLocation(), radius);

        player.sendMessage(PREFIX.append(
                LegacyComponentSerializer.legacyAmpersand().deserialize(
                        "&aRemoved &e" + removed + "&a dummy entity(s) within &e" + (int) radius + " &ablocks."
                )
        ));
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("visantara.dummy")) {
            player.sendMessage(PREFIX.append(
                    Component.text("No permission.", NamedTextColor.RED)
            ));
            return;
        }
        plugin.reload();
        player.sendMessage(PREFIX.append(
                Component.text("Configuration reloaded.", NamedTextColor.GREEN)
        ));
    }

    private void sendUsage(Player player) {
        player.sendMessage(PREFIX.append(
                LegacyComponentSerializer.legacyAmpersand().deserialize(
                        "&eCommands:\n" +
                                " &f/vdummy spawn <mob|player>\n" +
                                " &f/vdummy remove\n" +
                                " &f/vdummy reload"
                )
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("spawn", "remove", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) {
            return Arrays.asList("mob", "player");
        }
        return Collections.emptyList();
    }
}