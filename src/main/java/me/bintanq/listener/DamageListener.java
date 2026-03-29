package me.bintanq.listener;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobCastSkillEvent;
import me.bintanq.VisantaraEventHandler;
import me.bintanq.dummy.DummyEntity;
import me.bintanq.dummy.DummyType;
import me.bintanq.manager.DummyManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DamageListener implements Listener {

    private final VisantaraEventHandler plugin;

    /**
     * Cache skill name per-caster UUID.
     * MythicMobCastSkillEvent terjadi sebelum/bersamaan attack,
     * sehingga kita bisa ambil nama skill dari sini.
     * Key: caster UUID, Value: nama skill terakhir yang di-cast
     */
    private final Map<UUID, String> recentSkillByCaster = new ConcurrentHashMap<>();

    /**
     * Cache potion effects yang baru ditambahkan ke dummy.
     * Key: dummy UUID, Value: list nama efek yang pending ditampilkan
     */
    private final Map<UUID, List<String>> pendingEffects = new ConcurrentHashMap<>();

    public DamageListener(VisantaraEventHandler plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // MythicMobs Skill Cast — intercept SEBELUM damage terjadi
    // -----------------------------------------------------------------------

    /**
     * MythicMobCastSkillEvent difire saat skill mulai di-cast oleh entity manapun
     * (termasuk player via MythicLib trigger). Kita cache nama skill-nya per caster.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicSkillCast(MythicMobCastSkillEvent event) {
        UUID casterUuid = null;
        try {
            // Caster bisa berupa ActiveMob atau entity biasa via MythicLib
            var caster = event.getCaster();
            if (caster != null && caster.getEntity() != null) {
                casterUuid = caster.getEntity().getBukkitEntity().getUniqueId();
            }
        } catch (Exception ignored) {}

        if (casterUuid == null) return;

        String skillName = "N/A";
        try {
            skillName = event.getSkill() != null ? event.getSkill().getInternalName() : "N/A";
        } catch (Exception ignored) {}

        recentSkillByCaster.put(casterUuid, skillName);

        // Hapus cache setelah 10 tick supaya tidak stale
        final UUID finalUuid = casterUuid;
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> recentSkillByCaster.remove(finalUuid), 10L);
    }

    // -----------------------------------------------------------------------
    // Potion Effect Added ke dummy — terjadi SETELAH damage event
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionEffectAdded(EntityPotionEffectEvent event) {
        if (event.getAction() != EntityPotionEffectEvent.Action.ADDED
                && event.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        DummyManager manager = plugin.getDummyManager();
        if (!manager.isDummy(victim.getUniqueId())) return;

        PotionEffect newEffect = event.getNewEffect();
        if (newEffect == null) return;

        String effectName = formatEffectName(newEffect.getType().getName())
                + " " + (newEffect.getAmplifier() + 1);

        pendingEffects.computeIfAbsent(victim.getUniqueId(), k -> new ArrayList<>()).add(effectName);

        // Bersihkan pending setelah 5 tick
        final UUID dummyUuid = victim.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> pendingEffects.remove(dummyUuid), 5L);
    }

    // -----------------------------------------------------------------------
    // Core damage interception
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        DummyManager manager = plugin.getDummyManager();
        if (!manager.isDummy(victim.getUniqueId())) return;

        Optional<DummyEntity> dummyOpt = manager.getDummy(victim.getUniqueId());
        if (dummyOpt.isEmpty()) return;

        DummyEntity dummy = dummyOpt.get();
        double finalDamage = event.getFinalDamage();

        double minDamage = plugin.getConfig().getDouble("settings.min-display-damage", 0.0);
        if (finalDamage < minDamage) return;

        // Damage cause
        String damageType = event.getCause().name().replace("_", " ");

        // Resolve attacker & skill dari cache
        String skillName = "N/A";
        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            attacker = resolveAttackingPlayer(byEntity);
            skillName = resolveSkillName(byEntity);
        }

        // Invincibility restore
        boolean invincible = plugin.getConfig().getBoolean(
                dummy.getType() == DummyType.PLAYER ? "dummy.player.invincible" : "dummy.mob.invincible", true);

        final Player finalAttacker = attacker;
        final String finalSkillName = skillName;
        final String finalDamageType = damageType;
        final String entityLabel = (dummy.getType() == DummyType.PLAYER) ? "Player Dummy" : "Mob Dummy";
        final UUID victimUuid = victim.getUniqueId();
        final double dmg = finalDamage;

        if (invincible) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (victim.isValid()) {
                    AttributeMaxHpRestore(victim, manager);
                }
            }, 1L);
        }

        // Delay report 3 tick: supaya MythicMobs sempat apply potion effect ke entity
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Effects: gabungkan efek aktif + efek pending yang baru ditambahkan
            String effects = buildCombinedEffects(victim, victimUuid);

            String format = plugin.getConfig().getString("analytics.format",
                    "&8[&6VEH&8] &7{entity} &8| &c{damage} DMG &8| &eType: {type} &8| &dSkill: {skill} &8| &aEffects: {effects}");
            int decimals = plugin.getConfig().getInt("analytics.decimal-places", 2);
            String dmgStr = String.format("%." + decimals + "f", dmg);

            String message = format
                    .replace("{entity}", entityLabel)
                    .replace("{damage}", dmgStr)
                    .replace("{type}", finalDamageType)
                    .replace("{skill}", finalSkillName)
                    .replace("{effects}", effects);

            // Update nametag setelah delay juga
            if (victim.isValid()) {
                manager.getDummy(victimUuid).ifPresent(d -> manager.updateNametag(victim, d));
            }

            broadcastAnalytics(message, finalAttacker);
        }, 3L);
    }

    // -----------------------------------------------------------------------
    // Prevent dummy death
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        DummyManager manager = plugin.getDummyManager();
        if (!manager.isDummy(event.getEntity().getUniqueId())) return;

        event.getDrops().clear();
        event.setDroppedExp(0);

        Optional<DummyEntity> dummyOpt = manager.getDummy(event.getEntity().getUniqueId());
        if (dummyOpt.isEmpty()) return;

        DummyEntity dummy = dummyOpt.get();
        var loc = event.getEntity().getLocation().clone();
        var type = dummy.getType();

        manager.unregisterDummy(event.getEntity().getUniqueId());

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (type == DummyType.MOB) {
                manager.spawnMobDummy(loc);
            } else {
                manager.spawnPlayerDummy(loc);
            }
        }, 2L);
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private void AttributeMaxHpRestore(LivingEntity victim, DummyManager manager) {
        try {
            var attr = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double max = (attr != null) ? attr.getValue() : 20.0;
            victim.setHealth(Math.min(max, 1024.0));
        } catch (Exception ignored) {}
        manager.getDummy(victim.getUniqueId()).ifPresent(d -> manager.updateNametag(victim, d));
    }

    /**
     * Resolve player yang menyerang. Bisa langsung atau lewat projectile.
     */
    private Player resolveAttackingPlayer(EntityDamageByEntityEvent event) {
        var damager = event.getDamager();
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj) {
            ProjectileSource shooter = proj.getShooter();
            if (shooter instanceof Player p) return p;
        }
        return null;
    }

    /**
     * Resolve nama skill dengan prioritas:
     * 1. Cache dari MythicMobCastSkillEvent (paling akurat untuk skill MythicMobs/MythicLib)
     * 2. Metadata MythicMobs di entity damager
     * 3. Fallback: nama MythicMob atau item yang dipakai
     */
    private String resolveSkillName(EntityDamageByEntityEvent event) {
        var damager = event.getDamager();

        // 1. Cek cache skill dari MythicMobCastSkillEvent
        String cached = recentSkillByCaster.get(damager.getUniqueId());
        if (cached != null && !cached.equals("N/A")) return cached;

        // Cek juga jika damager adalah projectile — ambil caster UUID-nya
        if (damager instanceof Projectile proj) {
            ProjectileSource shooter = proj.getShooter();
            if (shooter instanceof LivingEntity le) {
                String cachedFromShooter = recentSkillByCaster.get(le.getUniqueId());
                if (cachedFromShooter != null) return cachedFromShooter;
            }
        }

        // 2. Metadata MythicMobs yang di-set saat skill cast
        for (String metaKey : List.of("MythicMobsSkill", "MythicSkillSource", "MythicSkill", "mmskill")) {
            if (damager.hasMetadata(metaKey)) {
                try {
                    String val = damager.getMetadata(metaKey).get(0).asString();
                    if (val != null && !val.isEmpty()) return val;
                } catch (Exception ignored) {}
            }
        }

        // 3. Cek apakah damager sendiri adalah MythicMob
        try {
            if (damager instanceof LivingEntity le) {
                var activeMob = MythicBukkit.inst().getMobManager().getActiveMob(le.getUniqueId());
                if (activeMob.isPresent()) {
                    return "MythicMob:" + activeMob.get().getType().getInternalName();
                }
            }
        } catch (Exception ignored) {}

        // 4. Player pakai item — coba baca display name item yang dipegang
        if (damager instanceof Player p) {
            var item = p.getInventory().getItemInMainHand();
            if (!item.getType().isAir() && item.hasItemMeta()) {
                var meta = item.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    // Strip formatting untuk nama bersih
                    String displayName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText().serialize(meta.displayName());
                    return "Item:" + displayName;
                }
                return "Item:" + item.getType().name();
            }
            return "Melee";
        }

        return "N/A";
    }

    /**
     * Gabungkan efek aktif di entity + efek pending yang baru saja ditambahkan.
     * Ini penting karena potion dari MythicMobs skill ditambahkan SETELAH damage event,
     * jadi kita baca keduanya setelah delay 3 tick.
     */
    private String buildCombinedEffects(LivingEntity entity, UUID dummyUuid) {
        Set<String> effectNames = new LinkedHashSet<>();

        // Efek aktif yang sudah ada di entity (termasuk yang baru dari tick ini)
        for (PotionEffect effect : entity.getActivePotionEffects()) {
            int durationTicks = effect.getDuration();
            String durationStr = durationTicks >= 32767 ? "∞" : (durationTicks / 20) + "s";
            effectNames.add(formatEffectName(effect.getType().getName())
                    + " " + (effect.getAmplifier() + 1)
                    + " [" + durationStr + "]");
        }

        // Tambahkan efek pending yang ter-cache dari EntityPotionEffectEvent
        // (sebagai fallback jika belum muncul di getActivePotionEffects)
        List<String> pending = pendingEffects.get(dummyUuid);
        if (pending != null) {
            effectNames.addAll(pending);
        }

        if (effectNames.isEmpty()) {
            boolean showNone = plugin.getConfig().getBoolean("analytics.show-no-effects", true);
            return showNone ? "None" : "";
        }

        return String.join(", ", effectNames);
    }

    private String formatEffectName(String raw) {
        return raw.replace("_", " ");
    }

    private void broadcastAnalytics(String message, Player directAttacker) {
        boolean broadcast = plugin.getConfig().getBoolean("settings.broadcast-damage", false);

        if (directAttacker != null && directAttacker.hasPermission("visantara.damage.output")) {
            sendMessage(directAttacker, message);
        }

        if (broadcast) {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.hasPermission("visantara.damage.output")
                        && (directAttacker == null || !online.equals(directAttacker))) {
                    sendMessage(online, message);
                }
            }
        }
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}