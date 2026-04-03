package me.bintanq.naturaldrops;

import me.bintanq.VisantaraEventHandler;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class DropConfig {

    public record DropEntry(
            String mmoType,
            String mmoId,
            double chance,
            int amountMin,
            int amountMax,
            Set<Biome> biomes,
            Set<String> weather,
            Set<String> time
    ) {
        public boolean appliesToBiome(Biome biome) {
            return biomes.isEmpty() || biomes.contains(biome);
        }

        public boolean appliesToWeather(boolean isRaining, boolean isThundering) {
            if (weather.isEmpty()) return true;
            if (isThundering && weather.contains("THUNDER")) return true;
            if (isRaining && weather.contains("RAIN")) return true;
            if (!isRaining && !isThundering && weather.contains("CLEAR")) return true;
            return false;
        }

        public boolean appliesToTime(long worldTime) {
            if (time.isEmpty()) return true;
            boolean isDay = worldTime >= 0 && worldTime < 12000;
            if (isDay && time.contains("DAY")) return true;
            if (!isDay && time.contains("NIGHT")) return true;
            return false;
        }
    }

    private final Map<Material, List<DropEntry>> dropTable = new HashMap<>();

    private final Set<Material> placeIgnoreList = new HashSet<>();

    public DropConfig(VisantaraEventHandler plugin) {
        load(plugin);
    }

    public void load(VisantaraEventHandler plugin) {
        dropTable.clear();
        placeIgnoreList.clear();

        List<String> ignoreList = plugin.getConfig().getStringList("natural-drops.place-ignore-list");
        for (String s : ignoreList) {
            try {
                placeIgnoreList.add(Material.valueOf(s.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("natural-drops: invalid material in place-ignore-list: '" + s + "', skipping.");
            }
        }

        ConfigurationSection blocksSection = plugin.getConfig().getConfigurationSection("natural-drops.blocks");
        if (blocksSection == null) return;

        for (String blockKey : blocksSection.getKeys(false)) {
            Material material;
            try {
                material = Material.valueOf(blockKey.toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("natural-drops: unknown block material '" + blockKey + "', skipping.");
                continue;
            }

            List<Map<?, ?>> dropList = blocksSection.getMapList(blockKey + ".drops");
            List<DropEntry> entries = new ArrayList<>();

            for (Map<?, ?> raw : dropList) {
                try {
                    String type = (String) raw.get("type");
                    String id = (String) raw.get("id");
                    double chance = toDouble(raw.get("chance"));
                    int amountMin = toInt(raw.get("amount-min"), 1);
                    int amountMax = toInt(raw.get("amount-max"), 1);

                    if (amountMin > amountMax) amountMin = amountMax;

                    Set<Biome> biomes = new HashSet<>();
                    Object biomeObj = raw.get("biomes");
                    if (biomeObj instanceof List<?> biomeList) {
                        for (Object b : biomeList) {
                            NamespacedKey key = NamespacedKey.minecraft(b.toString().toLowerCase());
                            Biome biome = Registry.BIOME.get(key);
                            if (biome != null) {
                                biomes.add(biome);
                            } else {
                                plugin.getLogger().warning("natural-drops: unknown biome '" + b + "' for block " + blockKey + ", skipping.");
                            }
                        }
                    }

                    Set<String> weather = new HashSet<>();
                    Object weatherObj = raw.get("weather");
                    if (weatherObj instanceof List<?> weatherList) {
                        for (Object w : weatherList) {
                            String wStr = w.toString().toUpperCase();
                            if (wStr.equals("CLEAR") || wStr.equals("RAIN") || wStr.equals("THUNDER")) {
                                weather.add(wStr);
                            } else {
                                plugin.getLogger().warning("natural-drops: invalid weather '" + w + "' for block " + blockKey + ", skipping. Use: CLEAR, RAIN, THUNDER");
                            }
                        }
                    }

                    Set<String> time = new HashSet<>();
                    Object timeObj = raw.get("time");
                    if (timeObj instanceof List<?> timeList) {
                        for (Object t : timeList) {
                            String tStr = t.toString().toUpperCase();
                            if (tStr.equals("DAY") || tStr.equals("NIGHT")) {
                                time.add(tStr);
                            } else {
                                plugin.getLogger().warning("natural-drops: invalid time '" + t + "' for block " + blockKey + ", skipping. Use: DAY, NIGHT");
                            }
                        }
                    }

                    entries.add(new DropEntry(type, id, chance, amountMin, amountMax, biomes, weather, time));
                } catch (Exception e) {
                    plugin.getLogger().warning("natural-drops: malformed drop entry for block " + blockKey + ": " + e.getMessage());
                }
            }

            if (!entries.isEmpty()) {
                dropTable.put(material, entries);
            }
        }

        plugin.getLogger().info("natural-drops: loaded " + dropTable.size() + " block drop configuration(s).");
        plugin.getLogger().info("natural-drops: place-ignore-list=" + placeIgnoreList.size() + " material(s).");
    }

    public List<DropEntry> getDrops(Material material) {
        return dropTable.getOrDefault(material, Collections.emptyList());
    }

    public boolean hasDrop(Material material) {
        return dropTable.containsKey(material);
    }

    public boolean isPlaceIgnored(Material material) {
        return placeIgnoreList.contains(material);
    }

    private double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        return Double.parseDouble(obj.toString());
    }

    private int toInt(Object obj, int fallback) {
        if (obj == null) return fallback;
        if (obj instanceof Number n) return n.intValue();
        try { return Integer.parseInt(obj.toString()); } catch (Exception e) { return fallback; }
    }
}