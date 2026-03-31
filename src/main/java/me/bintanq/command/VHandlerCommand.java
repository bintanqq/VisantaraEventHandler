package me.bintanq.command;

import me.bintanq.VisantaraEventHandler;
import me.bintanq.dummy.DummyEntity;
import me.bintanq.manager.DummyManager;
import me.bintanq.naturaldrops.NaturalDropManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

    private static final Component PREFIX = LegacyComponentSerializer.legacyAmpersand()
            .deserialize("&8[&6VEH&8] ");

    private final VisantaraEventHandler plugin;

    public VHandlerCommand(VisantaraEventHandler plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("visantara.use")) {
            player.sendMessage(PREFIX.append(Component.text("No permission.", NamedTextColor.RED)));
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
            default -> sendUsage(player);
        }

        return true;
    }

    private void handleDummy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX.append(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&eUsage: /vhandler dummy <spawn <mob|player>|remove>")
            ));
            return;
        }

        DummyManager manager = plugin.getDummyManager();

        switch (args[1].toLowerCase()) {
            case "spawn" -> {
                if (!player.hasPermission("visantara.dummy.spawn")) {
                    player.sendMessage(PREFIX.append(Component.text("No permission.", NamedTextColor.RED)));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(PREFIX.append(
                            LegacyComponentSerializer.legacyAmpersand().deserialize("&eUsage: /vhandler dummy spawn <mob|player>")
                    ));
                    return;
                }
                switch (args[2].toLowerCase()) {
                    case "mob" -> {
                        DummyEntity dummy = manager.spawnMobDummy(player.getLocation());
                        player.sendMessage(PREFIX.append(
                                LegacyComponentSerializer.legacyAmpersand().deserialize(
                                        "&aSpawned &eMob Dummy&a. &7UUID: &f" + dummy.getUuid()
                                )
                        ));
                    }
                    case "player" -> {
                        DummyEntity dummy = manager.spawnPlayerDummy(player.getLocation());
                        player.sendMessage(PREFIX.append(
                                LegacyComponentSerializer.legacyAmpersand().deserialize(
                                        "&aSpawned &bPlayer Dummy&a. &7UUID: &f" + dummy.getUuid()
                                )
                        ));
                    }
                    default -> player.sendMessage(PREFIX.append(
                            Component.text("Unknown type. Use: mob or player", NamedTextColor.RED)
                    ));
                }
            }
            case "remove" -> {
                if (!player.hasPermission("visantara.dummy.remove")) {
                    player.sendMessage(PREFIX.append(Component.text("No permission.", NamedTextColor.RED)));
                    return;
                }
                double radius = plugin.getConfig().getDouble("settings.remove-radius", 20.0);
                int removed = manager.removeDummiesInRadius(player.getLocation(), radius);
                player.sendMessage(PREFIX.append(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(
                                "&aRemoved &e" + removed + "&a dummy(s) within &e" + (int) radius + "&a blocks."
                        )
                ));
            }
            default -> player.sendMessage(PREFIX.append(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&eUsage: /vhandler dummy <spawn <mob|player>|remove>")
            ));
        }
    }

    private void handleDrop(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(PREFIX.append(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&eUsage: /vhandler drop <check|mark>")
            ));
            return;
        }

        NaturalDropManager dropManager = plugin.getNaturalDropManager();

        switch (args[1].toLowerCase()) {
            case "check" -> {
                if (!player.hasPermission("visantara.drop.check")) {
                    player.sendMessage(PREFIX.append(Component.text("No permission.", NamedTextColor.RED)));
                    return;
                }
                Block target = player.getTargetBlockExact(8);
                if (target == null) {
                    player.sendMessage(PREFIX.append(Component.text("No block in sight (max 8 blocks).", NamedTextColor.RED)));
                    return;
                }
                boolean placed = dropManager.isPlayerPlaced(target.getLocation());
                String status = placed ? "&cPlayer-Placed &7(no MMOItems drop)" : "&aNatural &7(MMOItems drop eligible)";
                player.sendMessage(PREFIX.append(
                        LegacyComponentSerializer.legacyAmpersand().deserialize(
                                "&7Block: &f" + target.getType().name()
                                        + " &8| &7Status: " + status
                                        + " &8| &7Pos: &f" + target.getX() + "," + target.getY() + "," + target.getZ()
                        )
                ));
            }
            case "mark" -> {
                if (!player.hasPermission("visantara.drop.mark")) {
                    player.sendMessage(PREFIX.append(Component.text("No permission.", NamedTextColor.RED)));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(PREFIX.append(
                            LegacyComponentSerializer.legacyAmpersand().deserialize("&eUsage: /vhandler drop mark <placed|natural>")
                    ));
                    return;
                }
                Block target = player.getTargetBlockExact(8);
                if (target == null) {
                    player.sendMessage(PREFIX.append(Component.text("No block in sight (max 8 blocks).", NamedTextColor.RED)));
                    return;
                }
                switch (args[2].toLowerCase()) {
                    case "placed" -> {
                        dropManager.markPlayerPlaced(target.getLocation());
                        player.sendMessage(PREFIX.append(
                                LegacyComponentSerializer.legacyAmpersand().deserialize(
                                        "&7Block &f" + target.getType().name() + " &7at &f"
                                                + target.getX() + "," + target.getY() + "," + target.getZ()
                                                + " &7marked as &cPlayer-Placed&7."
                                )
                        ));
                    }
                    case "natural" -> {
                        dropManager.forceMarkNatural(target.getLocation());
                        player.sendMessage(PREFIX.append(
                                LegacyComponentSerializer.legacyAmpersand().deserialize(
                                        "&7Block &f" + target.getType().name() + " &7at &f"
                                                + target.getX() + "," + target.getY() + "," + target.getZ()
                                                + " &7marked as &aNatural&7."
                                )
                        ));
                    }
                    default -> player.sendMessage(PREFIX.append(
                            Component.text("Unknown mark type. Use: placed or natural", NamedTextColor.RED)
                    ));
                }
            }
            default -> player.sendMessage(PREFIX.append(
                    LegacyComponentSerializer.legacyAmpersand().deserialize("&eUsage: /vhandler drop <check|mark>")
            ));
        }
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("visantara.use")) {
            player.sendMessage(PREFIX.append(Component.text("No permission.", NamedTextColor.RED)));
            return;
        }
        plugin.reload();
        player.sendMessage(PREFIX.append(Component.text("Configuration reloaded.", NamedTextColor.GREEN)));
    }

    private void sendUsage(Player player) {
        player.sendMessage(PREFIX.append(
                LegacyComponentSerializer.legacyAmpersand().deserialize(
                        "&eCommands:\n" +
                                " &f/vhandler dummy spawn <mob|player>\n" +
                                " &f/vhandler dummy remove\n" +
                                " &f/vhandler drop check\n" +
                                " &f/vhandler drop mark <placed|natural>\n" +
                                " &f/vhandler reload"
                )
        ));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("dummy", "drop", "reload");
        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "dummy" -> Arrays.asList("spawn", "remove");
                case "drop" -> Arrays.asList("check", "mark");
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