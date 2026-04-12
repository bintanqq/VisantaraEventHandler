package me.bintanq.cinematic;

import net.Indyuce.mmoitems.api.event.PlayerUseCraftingStationEvent;
import me.bintanq.VisantaraEventHandler;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.sql.*;
import java.util.*;

public class ForgeCinematicListener implements Listener {

    private final VisantaraEventHandler plugin;
    private final Set<String> seenOnce = new HashSet<>();

    private static final NamespacedKey MMO_ID_KEY = new NamespacedKey("mmoitems", "item-id");

    public ForgeCinematicListener(VisantaraEventHandler plugin) {
        this.plugin = plugin;
        initSeenTable();
        loadSeenFromDb();
    }

    // ══════════════════════════════════════════════════════════════════
    // EVENT TRIGGER
    // ══════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftingQueue(PlayerUseCraftingStationEvent event) {
        if (!plugin.getConfig().getBoolean("forge-cinematic.enabled", true)) return;
        if (event.getInteraction() != PlayerUseCraftingStationEvent.StationAction.CRAFTING_QUEUE) return;

        Player player = event.getPlayerData().getPlayer();
        if (player == null || !player.isOnline()) return;

        if (!event.hasResult()) return;
        ItemStack result = event.getResult();

        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;

        String itemId = meta.getPersistentDataContainer()
                .get(MMO_ID_KEY, PersistentDataType.STRING);
        if (itemId == null || itemId.isEmpty()) return;

        ConfigurationSection items = plugin.getConfig()
                .getConfigurationSection("forge-cinematic.items");
        if (items == null || !items.contains(itemId)) return;

        String pageId = items.getString(itemId + ".cinematic-page");
        if (pageId == null || pageId.isEmpty()) {
            plugin.getLogger().warning("[ForgeCinematic] Item '" + itemId + "' ga punya cinematic-page di config!");
            return;
        }

        // Cek only-once
        boolean onlyOnce = items.getBoolean(itemId + ".only-once", false);
        if (onlyOnce) {
            String key = player.getUniqueId() + ":" + itemId;
            if (seenOnce.contains(key)) return;
            seenOnce.add(key);
            saveSeenToDb(player.getUniqueId(), itemId);
        }

        // Trigger Typewriter cinematic
        final String finalPage = pageId;
        final String playerName = player.getName();
        plugin.getServer().getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "tw cinematic start " + finalPage + " " + playerName));
    }

    // ══════════════════════════════════════════════════════════════════
    // PUBLIC API — test command
    // ══════════════════════════════════════════════════════════════════

    public void triggerTest(Player player, String pageId) {
        if (pageId == null || pageId.isEmpty()) {
            player.sendMessage("§cUsage: /vhandler cinematic test <pageId>");
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "tw cinematic start " + pageId + " " + player.getName()));
    }

    // ══════════════════════════════════════════════════════════════════
    // SQLite — one-time persistence
    // ══════════════════════════════════════════════════════════════════

    private void initSeenTable() {
        try (Connection conn = plugin.getNaturalDropManager().borrowConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS forge_cinematic_seen (
                    uuid    TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    PRIMARY KEY (uuid, item_id)
                )
            """);
        } catch (SQLException e) {
            plugin.getLogger().warning("[ForgeCinematic] initSeenTable: " + e.getMessage());
        }
    }

    private void loadSeenFromDb() {
        try (Connection conn = plugin.getNaturalDropManager().borrowConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uuid, item_id FROM forge_cinematic_seen")) {
            while (rs.next())
                seenOnce.add(rs.getString("uuid") + ":" + rs.getString("item_id"));
            plugin.getLogger().info("[ForgeCinematic] Loaded " + seenOnce.size() + " seen record(s).");
        } catch (SQLException e) {
            plugin.getLogger().warning("[ForgeCinematic] loadSeenFromDb: " + e.getMessage());
        }
    }

    private void saveSeenToDb(UUID uuid, String itemId) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getNaturalDropManager().borrowConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT OR IGNORE INTO forge_cinematic_seen (uuid, item_id) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, itemId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("[ForgeCinematic] saveSeenToDb: " + e.getMessage());
            }
        });
    }
}