package me.bintanq.manager;

import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.BukkitAPIHelper;
import me.bintanq.VisantaraEventHandler;
import me.bintanq.dummy.DummyEntity;
import me.bintanq.dummy.DummyType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DummyManager {

    public static final String DUMMY_META_KEY = "visantara_dummy";
    public static final String DUMMY_TYPE_KEY = "visantara_dummy_type";
    public static final String DUMMY_MAX_HP_KEY = "visantara_dummy_maxhp";

    private final VisantaraEventHandler plugin;
    private final Map<UUID, DummyEntity> dummies = new ConcurrentHashMap<>();
    private final BukkitAPIHelper mythicHelper;

    public DummyManager(VisantaraEventHandler plugin) {
        this.plugin = plugin;
        this.mythicHelper = new BukkitAPIHelper();
    }

    // -----------------------------------------------------------------------
    // Spawn
    // -----------------------------------------------------------------------

    public DummyEntity spawnMobDummy(Location location) {
        double maxHp = plugin.getConfig().getDouble("dummy.mob.max-hp", 2000.0);
        String mythicType = plugin.getConfig().getString("dummy.mob.mythic-mob-type", "");
        String nametag = plugin.getConfig().getString("dummy.mob.nametag", "&e[MOB DUMMY] &c❤ <hp>/<maxhp>");

        LivingEntity entity;

        if (mythicType != null && !mythicType.isEmpty()) {
            Optional<MythicMob> mythicMobOpt = MythicProvider.get().getMobManager().getMythicMob(mythicType);
            if (mythicMobOpt.isPresent()) {
                Entity spawned = mythicHelper.spawnMythicMob(mythicType, location, 1);
                if (!(spawned instanceof LivingEntity living)) {
                    plugin.getLogger().warning("MythicMob type '" + mythicType + "' did not spawn a LivingEntity.");
                    entity = spawnVanillaMob(location, maxHp);
                } else {
                    entity = living;
                }
            } else {
                plugin.getLogger().warning("MythicMob type '" + mythicType + "' not found. Falling back to vanilla.");
                entity = spawnVanillaMob(location, maxHp);
            }
        } else {
            entity = spawnVanillaMob(location, maxHp);
        }

        applyDummyMeta(entity, DummyType.MOB, maxHp);
        setEntityMaxHp(entity, maxHp);
        entity.setHealth(maxHp);
        entity.setRemoveWhenFarAway(false);
        entity.setAI(false);
        entity.setInvulnerable(false); // We handle invincibility via event cancellation

        DummyEntity dummy = new DummyEntity(entity.getUniqueId(), DummyType.MOB, maxHp, nametag);
        dummies.put(entity.getUniqueId(), dummy);

        updateNametag(entity, dummy);
        return dummy;
    }

    public DummyEntity spawnPlayerDummy(Location location) {
        double maxHp = plugin.getConfig().getDouble("dummy.player.max-hp", 2000.0);
        String nametag = plugin.getConfig().getString("dummy.player.nametag", "&b[PLAYER DUMMY] &c❤ <hp>/<maxhp>");

        // Use a Zombie with disguise-like metadata to flag it as player-type for MMOItems/MythicMobs
        // NMS-based PlayerNPC is complex; we use a Zombie flagged with custom metadata.
        // For MythicMobs @PlayersInRadius compatibility, we register it as a fake "player" target via metadata.
        Zombie zombie = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
        zombie.setBaby(false);
        zombie.setShouldBurnInDay(false);
        zombie.setCanPickupItems(false);
        zombie.setRemoveWhenFarAway(false);
        zombie.setAI(false);

        applyDummyMeta(zombie, DummyType.PLAYER, maxHp);
        // Additional metadata flag to hint plugins this should be treated as a player target
        zombie.setMetadata("visantara_player_dummy", new FixedMetadataValue(plugin, true));

        setEntityMaxHp(zombie, maxHp);
        zombie.setHealth(maxHp);

        DummyEntity dummy = new DummyEntity(zombie.getUniqueId(), DummyType.PLAYER, maxHp, nametag);
        dummies.put(zombie.getUniqueId(), dummy);

        updateNametag(zombie, dummy);
        return dummy;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private LivingEntity spawnVanillaMob(Location location, double maxHp) {
        String typeStr = plugin.getConfig().getString("dummy.mob.vanilla-type", "ZOMBIE");
        EntityType type;
        try {
            type = EntityType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid vanilla-type '" + typeStr + "'. Using ZOMBIE.");
            type = EntityType.ZOMBIE;
        }
        Entity entity = location.getWorld().spawnEntity(location, type);
        if (entity instanceof LivingEntity living) {
            if (living instanceof Mob mob) {
                mob.setAI(false);
                mob.setShouldBurnInDay(false);
                if (mob instanceof Zombie z) z.setBaby(false);
            }
            return living;
        }
        // Fallback
        entity.remove();
        return (LivingEntity) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
    }

    private void applyDummyMeta(LivingEntity entity, DummyType type, double maxHp) {
        entity.setMetadata(DUMMY_META_KEY, new FixedMetadataValue(plugin, true));
        entity.setMetadata(DUMMY_TYPE_KEY, new FixedMetadataValue(plugin, type.name()));
        entity.setMetadata(DUMMY_MAX_HP_KEY, new FixedMetadataValue(plugin, maxHp));
    }

    private void setEntityMaxHp(LivingEntity entity, double maxHp) {
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(maxHp);
        }
    }

    public void updateNametag(LivingEntity entity, DummyEntity dummy) {
        String template = dummy.getNametag();
        String currentHp = String.format("%.1f", entity.getHealth());
        String maxHp = String.format("%.1f", dummy.getMaxHp());

        String processed = template
                .replace("<hp>", currentHp)
                .replace("<maxhp>", maxHp);

        // Support both legacy (&) and MiniMessage formats
        if (processed.contains("&")) {
            // Convert legacy to Adventure component
            net.kyori.adventure.text.Component component = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand()
                    .deserialize(processed);
            entity.customName(component);
        } else {
            entity.customName(MiniMessage.miniMessage().deserialize(processed));
        }
        entity.setCustomNameVisible(true);
    }

    // -----------------------------------------------------------------------
    // Removal
    // -----------------------------------------------------------------------

    public int removeAllDummies() {
        int count = 0;
        for (UUID uuid : new HashSet<>(dummies.keySet())) {
            Entity entity = plugin.getServer().getEntity(uuid);
            if (entity != null) {
                entity.remove();
                count++;
            }
        }
        dummies.clear();
        return count;
    }

    public int removeDummiesInRadius(Location center, double radius) {
        int count = 0;
        double radiusSq = radius * radius;
        for (UUID uuid : new HashSet<>(dummies.keySet())) {
            Entity entity = plugin.getServer().getEntity(uuid);
            if (entity != null && entity.getWorld().equals(center.getWorld())) {
                if (entity.getLocation().distanceSquared(center) <= radiusSq) {
                    entity.remove();
                    dummies.remove(uuid);
                    count++;
                }
            } else if (entity == null) {
                dummies.remove(uuid);
            }
        }
        return count;
    }

    public void unregisterDummy(UUID uuid) {
        dummies.remove(uuid);
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    public boolean isDummy(UUID uuid) {
        return dummies.containsKey(uuid);
    }

    public Optional<DummyEntity> getDummy(UUID uuid) {
        return Optional.ofNullable(dummies.get(uuid));
    }

    public Map<UUID, DummyEntity> getDummies() {
        return Collections.unmodifiableMap(dummies);
    }

    public void removeDeadDummies() {
        dummies.entrySet().removeIf(entry -> {
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            return entity == null || !entity.isValid();
        });
    }
}