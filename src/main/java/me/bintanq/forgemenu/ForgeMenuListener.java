package me.bintanq.forgemenu;

import me.bintanq.VisantaraEventHandler;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.*;
import java.util.stream.Collectors;

public class ForgeMenuListener implements Listener {

    private final VisantaraEventHandler plugin;
    private final ForgeMenuConfig menuConfig;
    private final ForgeMenuGUI gui;

    private final Set<UUID> cooldown = Collections.synchronizedSet(new HashSet<>());

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public ForgeMenuListener(VisantaraEventHandler plugin, ForgeMenuConfig menuConfig, ForgeMenuGUI gui) {
        this.plugin = plugin;
        this.menuConfig = menuConfig;
        this.gui = gui;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!menuConfig.isTriggerBlock(event.getClickedBlock().getLocation())) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (cooldown.contains(uuid)) return;
        cooldown.add(uuid);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> cooldown.remove(uuid), 4L);

        plugin.getServer().getScheduler().runTask(plugin, () -> handleForgeOpen(player));
    }

    private void handleForgeOpen(Player player) {
        String rankName = resolvePlayerRank(player);

        if (rankName == null) {
            player.sendMessage(LEGACY.deserialize(menuConfig.getNoRankMessage()));
            return;
        }

        ForgeMenuConfig.RankData rankData = menuConfig.getRankData(rankName);
        if (rankData == null) {
            player.sendMessage(LEGACY.deserialize(menuConfig.getNoRankMessage()));
            return;
        }

        ForgeMenuConfig.ResolvedMenu menu = menuConfig.resolve(rankData);
        if (menu == null) {
            plugin.getLogger().severe("[ForgeMenu] Template belum di-load, cek forge-menu.yml!");
            return;
        }

        gui.openForPlayer(player, menu);
    }

    private String resolvePlayerRank(Player player) {
        LuckPerms lp;
        try {
            lp = LuckPermsProvider.get();
        } catch (IllegalStateException e) {
            plugin.getLogger().warning("[ForgeMenu] LuckPerms tidak tersedia: " + e.getMessage());
            return menuConfig.getRankData("default") != null ? "default" : null;
        }

        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return menuConfig.getRankData("default") != null ? "default" : null;
        }

        Set<String> registered = menuConfig.getRegisteredRanks();

        List<String> playerGroups = user.getInheritedGroups(QueryOptions.nonContextual())
                .stream()
                .sorted(Comparator.comparingInt((Group g) -> g.getWeight().orElse(0)).reversed())
                .map(g -> g.getName().toLowerCase())
                .collect(Collectors.toList());

        for (String group : playerGroups) {
            if (registered.contains(group)) return group;
        }

        return registered.contains("default") ? "default" : null;
    }
}