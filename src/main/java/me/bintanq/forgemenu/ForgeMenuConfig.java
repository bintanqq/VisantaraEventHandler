package me.bintanq.forgemenu;

import me.bintanq.VisantaraEventHandler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ForgeMenuConfig {


    public record RankData(
            String rankName,
            int maxQueue,
            String titleSuffix,
            Material fillMaterial
    ) {}

    public record TemplateItem(
            int slot,
            Material material,
            String name,
            List<String> lore,
            String stationIdTemplate, // bisa mengandung {rank}
            boolean closeOnOpen
    ) {}

    public record GUITemplate(
            int rows,
            String title,
            boolean fillEmpty,
            String fillName,
            List<TemplateItem> items
    ) {}

    public record ResolvedMenu(
            int rows,
            String title,
            boolean fillEmpty,
            Material fillMaterial,
            String fillName,
            List<ResolvedItem> items
    ) {}

    public record ResolvedItem(
            int slot,
            Material material,
            String name,
            List<String> lore,
            String stationId,
            boolean closeOnOpen
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
    private final Map<String, RankData> rankDataMap  = new LinkedHashMap<>();
    private GUITemplate template;
    private String noRankMessage = "&cKamu tidak memiliki akses ke Forge Station.";


    public ForgeMenuConfig(VisantaraEventHandler plugin) {
        this.plugin = plugin;
        load();
    }


    public void load() {
        triggerBlocks.clear();
        rankDataMap.clear();
        template = null;

        File file = new File(plugin.getDataFolder(), "forge-menu.yml");
        if (!file.exists()) plugin.saveResource("forge-menu.yml", false);

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        InputStream defaultStream = plugin.getResource("forge-menu.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            cfg.setDefaults(defaults);
        }

        String msg = cfg.getString("forge-menu.no-rank-message");
        if (msg != null && !msg.isBlank()) noRankMessage = msg;

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

                int maxQueue     = rs.getInt("max-queue", 1);
                String suffix    = rs.getString("title-suffix", "");
                Material fillMat = parseMaterial(rs.getString("fill-material", "GRAY_STAINED_GLASS_PANE"), rankKey);

                rankDataMap.put(rankKey.toLowerCase(), new RankData(rankKey.toLowerCase(), maxQueue, suffix, fillMat));
            }
        }

        ConfigurationSection tmplSec = cfg.getConfigurationSection("forge-menu.template");
        if (tmplSec == null) {
            plugin.getLogger().severe("[ForgeMenu] Missing 'forge-menu.template' section in forge-menu.yml!");
        } else {
            int rows       = Math.max(1, Math.min(6, tmplSec.getInt("rows", 3)));
            String title   = tmplSec.getString("title", "&8Forge Station");
            boolean fill   = tmplSec.getBoolean("fill-empty", false);
            String fillNm  = tmplSec.getString("fill-name", " ");

            List<TemplateItem> tItems = new ArrayList<>();
            for (Map<?, ?> raw : tmplSec.getMapList("items")) {
                TemplateItem ti = parseTemplateItem(raw);
                if (ti != null) tItems.add(ti);
            }
            template = new GUITemplate(rows, title, fill, fillNm, tItems);
        }

        plugin.getLogger().info("[ForgeMenu] Loaded " + triggerBlocks.size() + " trigger(s), "
                + rankDataMap.size() + " rank(s).");
    }

    public ResolvedMenu resolve(RankData rank) {
        if (template == null) return null;

        String maxQ     = String.valueOf(rank.maxQueue());
        String rankName = rank.rankName();

        String title = template.title();
        if (rank.titleSuffix() != null && !rank.titleSuffix().isBlank()) {
            title = title + " " + rank.titleSuffix();
        }

        List<ResolvedItem> resolvedItems = new ArrayList<>();
        for (TemplateItem ti : template.items()) {
            List<String> resolvedLore = new ArrayList<>();
            for (String line : ti.lore()) {
                resolvedLore.add(line
                        .replace("{max_queue}", maxQ)
                        .replace("{rank}", rankName));
            }

            String stationId = ti.stationIdTemplate()
                    .replace("{rank}", rankName)
                    .replace("{max_queue}", maxQ);

            resolvedItems.add(new ResolvedItem(
                    ti.slot(), ti.material(), ti.name(),
                    resolvedLore, stationId, ti.closeOnOpen()));
        }

        return new ResolvedMenu(
                template.rows(), title,
                template.fillEmpty(), rank.fillMaterial(), template.fillName(),
                resolvedItems);
    }


    private TemplateItem parseTemplateItem(Map<?, ?> raw) {
        try {
            int slot         = toInt(raw.get("slot"), 0);
            Material mat     = parseMaterial(Objects.toString(raw.get("material"), "STONE"), "template");
            String name      = Objects.toString(raw.get("name"), "&fUnnamed");
            String stationId = Objects.toString(raw.get("station-id"), "");
            boolean close    = raw.get("close-on-open") == null
                    || Boolean.parseBoolean(raw.get("close-on-open").toString());

            List<String> lore = new ArrayList<>();
            if (raw.get("lore") instanceof List<?> loreList) {
                for (Object l : loreList) lore.add(l.toString());
            }

            return new TemplateItem(slot, mat, name, lore, stationId, close);
        } catch (Exception e) {
            plugin.getLogger().warning("[ForgeMenu] Failed to parse template item: " + e.getMessage());
            return null;
        }
    }

    private Material parseMaterial(String raw, String context) {
        if (raw == null) return Material.STONE;
        try {
            return Material.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[ForgeMenu] Unknown material '" + raw + "' (" + context + "), using STONE.");
            return Material.STONE;
        }
    }

    private int toInt(Object obj, int fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Number n) return n.intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return fallback; }
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
}