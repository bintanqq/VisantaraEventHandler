package me.bintanq.manager;

import io.lumine.mythic.api.MythicProvider;
import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import me.bintanq.VisantaraEventHandler;
import me.bintanq.dummy.DummyEntity;
import me.bintanq.dummy.DummyType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DummyManager {

    public static final String DUMMY_META_KEY = "visantara_dummy";
    public static final String DUMMY_TYPE_KEY = "visantara_dummy_type";
    public static final String DUMMY_MAX_HP_KEY = "visantara_dummy_maxhp";

    private static final double NMS_MAX_HEALTH_CAP = 1024.0;

    private final VisantaraEventHandler plugin;
    private final Map<UUID, DummyEntity> dummies = new ConcurrentHashMap<>();

    public DummyManager(VisantaraEventHandler plugin) {
        this.plugin = plugin;
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
                try {
                    Entity spawned = MythicBukkit.inst().getAPIHelper().spawnMythicMob(mythicType, location);
                    if (!(spawned instanceof LivingEntity living)) {
                        plugin.getLogger().warning("MythicMob type '" + mythicType + "' did not spawn a LivingEntity.");
                        entity = spawnVanillaMob(location, maxHp);
                    } else {
                        entity = living;
                    }
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to spawn MythicMob '" + mythicType + "': " + ex.getMessage());
                    entity = spawnVanillaMob(location, maxHp);
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
        setFullHealth(entity);
        entity.setRemoveWhenFarAway(false);
        entity.setAI(false);
        entity.setInvulnerable(false);

        if (plugin.getConfig().getBoolean("dummy.mob.disable-sounds", true)) {
            entity.setSilent(true);
        }

        DummyEntity dummy = new DummyEntity(entity.getUniqueId(), DummyType.MOB, maxHp, nametag);
        dummies.put(entity.getUniqueId(), dummy);

        // Tampilkan HP awal = maxHp
        updateNametagWithHp(entity, dummy, maxHp);
        return dummy;
    }

    public DummyEntity spawnPlayerDummy(Location location) {
        double maxHp = plugin.getConfig().getDouble("dummy.player.max-hp", 2000.0);
        String nametag = plugin.getConfig().getString("dummy.player.nametag", "&b[PLAYER DUMMY] &c❤ <hp>/<maxhp>");

        Zombie zombie = (Zombie) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
        zombie.setBaby(false);
        zombie.setShouldBurnInDay(false);
        zombie.setCanPickupItems(false);
        zombie.setRemoveWhenFarAway(false);
        zombie.setAI(false);

        if (plugin.getConfig().getBoolean("dummy.player.disable-sounds", true)) {
            zombie.setSilent(true);
        }

        applyDummyMeta(zombie, DummyType.PLAYER, maxHp);
        zombie.setMetadata("visantara_player_dummy", new FixedMetadataValue(plugin, true));

        setEntityMaxHp(zombie, maxHp);
        setFullHealth(zombie);

        DummyEntity dummy = new DummyEntity(zombie.getUniqueId(), DummyType.PLAYER, maxHp, nametag);
        dummies.put(zombie.getUniqueId(), dummy);

        updateNametagWithHp(zombie, dummy, maxHp);
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
                if (mob instanceof Zombie z) {
                    z.setBaby(false);
                    z.setShouldBurnInDay(false);
                } else if (mob instanceof Skeleton s) {
                    s.setShouldBurnInDay(false);
                } else if (mob instanceof Drowned d) {
                    d.setShouldBurnInDay(false);
                }
            }
            return living;
        }
        entity.remove();
        return (LivingEntity) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
    }

    private void applyDummyMeta(LivingEntity entity, DummyType type, double maxHp) {
        entity.setMetadata(DUMMY_META_KEY, new FixedMetadataValue(plugin, true));
        entity.setMetadata(DUMMY_TYPE_KEY, new FixedMetadataValue(plugin, type.name()));
        entity.setMetadata(DUMMY_MAX_HP_KEY, new FixedMetadataValue(plugin, maxHp));
    }

    private void setEntityMaxHp(LivingEntity entity, double requestedHp) {
        double capped = Math.min(requestedHp, NMS_MAX_HEALTH_CAP);
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.getModifiers().forEach(attr::removeModifier);
            attr.setBaseValue(capped);
        }
    }

    private void setFullHealth(LivingEntity entity) {
        AttributeInstance attr = entity.getAttribute(Attribute.MAX_HEALTH);
        double max = (attr != null) ? attr.getValue() : 20.0;
        entity.setHealth(Math.min(max, NMS_MAX_HEALTH_CAP));
    }

    /**
     * Update nametag dengan HP simulasi yang diberikan dari DamageListener.
     * DummyManager TIDAK baca entity.getHealth() karena selalu full (invincible restore).
     */
    public void updateNametagWithHp(LivingEntity entity, DummyEntity dummy, double simulatedHp) {
        String template = dummy.getNametag();
        String hp = String.format("%.1f", Math.max(0, simulatedHp));
        String maxHp = String.format("%.1f", dummy.getMaxHp());

        String processed = template
                .replace("<hp>", hp)
                .replace("<maxhp>", maxHp);

        net.kyori.adventure.text.Component component;
        if (processed.contains("&")) {
            component = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacyAmpersand().deserialize(processed);
        } else {
            component = MiniMessage.miniMessage().deserialize(processed);
        }
        entity.customName(component);
        entity.setCustomNameVisible(true);
    }

    /**
     * Legacy method — dipanggil dari tempat lain jika perlu reset nametag ke maxHp.
     */
    public void updateNametag(LivingEntity entity, DummyEntity dummy) {
        updateNametagWithHp(entity, dummy, dummy.getMaxHp());
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