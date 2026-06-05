package me.bintanq.forgemenu;

import me.bintanq.VisantaraEventHandler;
import me.bintanq.util.ConfigUpdater;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ForgeMenuConfig {

    /**
     * Hanya menyimpan mapping rank -> station-id MMOItems.
     * Plugin ini murni sebagai jembatan deteksi rank, bukan sebagai GUI engine.
     */
    public record RankData(
            String rankName,
            String materialStation,
            String natureStation
    ) {}

    public record TriggerBlock(String world, int x, int y, int z) {
        public boolean matches(Location loc) {
            if (loc.getWorld() == null) return false;
            return loc.getWorld().getName().equals(world)
                    && loc.getBlockX() == x
                    && loc.getBlockY() == y
                    && loc.getBlockZ() == z;
        }
    }

    private final VisantaraEventHandler plugin;
    private final List<TriggerBlock> triggerBlocks = new ArrayList<>();
    private final Map<String, RankData> rankDataMap = new LinkedHashMap<>();
    private String noRankMessage = "&cKamu tidak memiliki akses ke Forge Station.";
    private int switchSlot = 40;
    private String materialTrigger = "MATERIAL";
    private String natureTrigger = "NATURE";

    public ForgeMenuConfig(VisantaraEventHandler plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        triggerBlocks.clear();
        rankDataMap.clear();

        File file = new File(plugin.getDataFolder(), "forge-menu.yml");
        if (!file.exists()) {
            plugin.saveResource("forge-menu.yml", false);
        }

        try {
            ConfigUpdater.update(plugin, "forge-menu.yml");
        } catch (IOException e) {
            plugin.getLogger().warning("[ForgeMenu] Failed to auto-update forge-menu.yml: " + e.getMessage());
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        String msg = cfg.getString("forge-menu.no-rank-message");
        if (msg != null && !msg.isBlank()) noRankMessage = msg;

        switchSlot = cfg.getInt("forge-menu.switch-slot", 40);
        
        String mTrig = cfg.getString("forge-menu.switch-triggers.material");
        if (mTrig != null && !mTrig.isBlank()) materialTrigger = mTrig;
        
        String nTrig = cfg.getString("forge-menu.switch-triggers.nature");
        if (nTrig != null && !nTrig.isBlank()) natureTrigger = nTrig;

        for (String entry : cfg.getStringList("forge-menu.trigger-blocks")) {
            String[] p = entry.split(";");
            if (p.length != 4) {
                plugin.getLogger().warning("[ForgeMenu] Invalid trigger-block: '" + entry + "' (expected world;x;y;z)");
                continue;
            }
            try {
                triggerBlocks.add(new TriggerBlock(p[0].trim(),
                        Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim()),
                        Integer.parseInt(p[3].trim())));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("[ForgeMenu] Bad coordinates in trigger-block: '" + entry + "'");
            }
        }

        ConfigurationSection ranksSec = cfg.getConfigurationSection("forge-menu.ranks");
        if (ranksSec != null) {
            for (String rankKey : ranksSec.getKeys(false)) {
                ConfigurationSection rs = ranksSec.getConfigurationSection(rankKey);
                if (rs == null) continue;
                
                String materialStation = rs.getString("material", "");
                String natureStation = rs.getString("nature", "");
                
                if (materialStation.isEmpty() && natureStation.isEmpty()) {
                    plugin.getLogger().warning("[ForgeMenu] Rank '" + rankKey + "' tidak punya material/nature station, skip.");
                    continue;
                }
                rankDataMap.put(rankKey.toLowerCase(), new RankData(rankKey.toLowerCase(), materialStation, natureStation));
            }
        }

        plugin.getLogger().info("[ForgeMenu] Loaded " + triggerBlocks.size() + " trigger(s), " + rankDataMap.size() + " rank(s).");
    }

    public boolean isTriggerBlock(Location loc) {
        for (TriggerBlock tb : triggerBlocks) {
            if (tb.matches(loc)) return true;
        }
        return false;
    }

    public RankData getRankData(String rankName) {
        if (rankName == null) return null;
        return rankDataMap.get(rankName.toLowerCase());
    }

    public Set<String> getRegisteredRanks() {
        return Collections.unmodifiableSet(rankDataMap.keySet());
    }

    public String getNoRankMessage() { return noRankMessage; }
    public List<TriggerBlock> getTriggerBlocks() { return Collections.unmodifiableList(triggerBlocks); }
    public int getSwitchSlot() { return switchSlot; }
    public String getMaterialTrigger() { return materialTrigger; }
    public String getNatureTrigger() { return natureTrigger; }
}
