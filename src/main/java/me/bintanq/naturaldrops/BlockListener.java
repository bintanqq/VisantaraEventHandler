package me.bintanq.naturaldrops;

import me.bintanq.VisantaraEventHandler;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.block.Biome;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

public class BlockListener implements Listener {

    private final VisantaraEventHandler plugin;
    private final NaturalDropManager dropManager;
    private final DropConfig dropConfig;
    private final Random random = new Random();

    public BlockListener(VisantaraEventHandler plugin, NaturalDropManager dropManager, DropConfig dropConfig) {
        this.plugin = plugin;
        this.dropManager = dropManager;
        this.dropConfig = dropConfig;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        dropManager.markPlayerPlaced(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation();
        Player player = event.getPlayer();
        World world = block.getWorld();

        boolean wasPlayerPlaced = dropManager.isPlayerPlaced(loc);

        if (wasPlayerPlaced) {
            dropManager.unmarkPlayerPlaced(loc);
            return;
        }

        if (!plugin.getConfig().getBoolean("natural-drops.enabled", true)) return;
        if (!dropConfig.hasDrop(block.getType())) return;

        Biome biome = block.getBiome();
        long worldTime = world.getTime();
        boolean isRaining = world.hasStorm();
        boolean isThundering = world.isThundering();

        List<DropConfig.DropEntry> drops = dropConfig.getDrops(block.getType());

        for (DropConfig.DropEntry entry : drops) {
            if (!entry.appliesToBiome(biome)) continue;

            if (!entry.appliesToWeather(isRaining, isThundering)) continue;

            if (!entry.appliesToTime(worldTime)) continue;

            if (random.nextDouble() > entry.chance()) continue;

            int amount = entry.amountMin() == entry.amountMax()
                    ? entry.amountMin()
                    : random.nextInt(entry.amountMax() - entry.amountMin() + 1) + entry.amountMin();

            spawnMMOItem(entry.mmoType(), entry.mmoId(), amount, loc);
        }
    }

    private void spawnMMOItem(String typeName, String itemId, int amount, Location loc) {
        try {
            Type type = MMOItems.plugin.getTypes().get(typeName);
            if (type == null) {
                plugin.getLogger().warning("natural-drops: MMOItems type '" + typeName + "' not found.");
                return;
            }

            ItemStack item = MMOItems.plugin.getItem(type, itemId);
            if (item == null) {
                plugin.getLogger().warning("natural-drops: MMOItems item '" + itemId + "' of type '" + typeName + "' not found.");
                return;
            }

            item.setAmount(Math.min(amount, item.getMaxStackSize()));
            loc.getWorld().dropItemNaturally(loc, item);
        } catch (Exception e) {
            plugin.getLogger().warning("natural-drops: failed to spawn MMOItem '" + typeName + ":" + itemId + "': " + e.getMessage());
        }
    }
}