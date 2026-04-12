package me.bintanq.forgemenu;

import me.bintanq.VisantaraEventHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ForgeMenuGUI implements Listener {

    private final VisantaraEventHandler plugin;
    private final ForgeMenuConfig menuConfig;

    private final Map<UUID, Map<Integer, ForgeMenuConfig.ResolvedItem>> openMenus = new HashMap<>();

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ForgeMenuGUI(VisantaraEventHandler plugin, ForgeMenuConfig menuConfig) {
        this.plugin = plugin;
        this.menuConfig = menuConfig;
    }


    public void openForPlayer(Player player, ForgeMenuConfig.ResolvedMenu menu) {
        int size = menu.rows() * 9;
        Inventory inv = Bukkit.createInventory(null, size, parseText(menu.title()));

        Map<Integer, ForgeMenuConfig.ResolvedItem> slotMap = new HashMap<>();

        if (menu.fillEmpty() && menu.fillMaterial() != null) {
            ItemStack filler = buildFiller(menu.fillMaterial(), menu.fillName());
            for (int i = 0; i < size; i++) inv.setItem(i, filler);
        }

        for (ForgeMenuConfig.ResolvedItem item : menu.items()) {
            int slot = item.slot();
            if (slot < 0 || slot >= size) {
                plugin.getLogger().warning("[ForgeMenu] Slot " + slot + " out of range (max=" + (size - 1) + "), skipping.");
                continue;
            }
            inv.setItem(slot, buildItem(item));
            slotMap.put(slot, item);
        }

        openMenus.put(player.getUniqueId(), slotMap);
        player.openInventory(inv);
    }


    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        if (!openMenus.containsKey(uuid)) return;

        event.setCancelled(true);

        Map<Integer, ForgeMenuConfig.ResolvedItem> slotMap = openMenus.get(uuid);
        if (slotMap == null) return;

        ForgeMenuConfig.ResolvedItem item = slotMap.get(event.getRawSlot());
        if (item == null) return; // klik filler / kosong

        handleClick(player, item);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openMenus.remove(player.getUniqueId());
        }
    }


    private void handleClick(Player player, ForgeMenuConfig.ResolvedItem item) {
        if (item.stationId() == null || item.stationId().isBlank()) {
            plugin.getLogger().warning("[ForgeMenu] Item '" + item.name() + "' tidak punya station-id!");
            return;
        }

        if (item.closeOnOpen()) player.closeInventory();

        String command = "mmoitems stations open " + item.stationId() + " " + player.getName();
        plugin.getServer().getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));

    }


    private ItemStack buildItem(ForgeMenuConfig.ResolvedItem item) {
        ItemStack stack = new ItemStack(item.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(parseText(item.name()));

        if (!item.lore().isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : item.lore()) {
                loreComponents.add(parseText(line));
            }
            meta.lore(loreComponents);
        }

        meta.setEnchantmentGlintOverride(false);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack buildFiller(org.bukkit.Material mat, String name) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(parseText(name));
            meta.setEnchantmentGlintOverride(false);
            stack.setItemMeta(meta);
        }
        return stack;
    }


    private Component parseText(String text) {
        if (text == null || text.isBlank()) return Component.empty();
        Component component = (text.contains("<") && text.contains(">"))
                ? MM.deserialize(text)
                : LEGACY.deserialize(text);
        return component.decoration(TextDecoration.ITALIC, false);
    }


    public boolean hasOpenMenu(UUID uuid) {
        return openMenus.containsKey(uuid);
    }

    public void closeAll() {
        for (UUID uuid : new HashSet<>(openMenus.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) player.closeInventory();
        }
        openMenus.clear();
    }
}