package me.bintanq.listener;

import io.lumine.mythic.bukkit.MythicBukkit;
import me.bintanq.VisantaraEventHandler;
import me.bintanq.dummy.DummyEntity;
import me.bintanq.dummy.DummyType;
import me.bintanq.manager.DummyManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DamageListener implements Listener {

    private final VisantaraEventHandler plugin;

    // Track total damage dealt ke dummy sejak spawn (buat display HP yang berkurang)
    private final Map<UUID, Double> damageAccumulated = new ConcurrentHashMap<>();

    public DamageListener(VisantaraEventHandler plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Core damage handler
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

        // Akumulasi damage untuk simulasi HP berkurang di nametag
        UUID victimUuid = victim.getUniqueId();
        double accumulated = damageAccumulated.merge(victimUuid, finalDamage, Double::sum);
        double simulatedHp = Math.max(0, dummy.getMaxHp() - accumulated);

        // Restore HP di tick berikutnya (invincible)
        boolean invincible = plugin.getConfig().getBoolean(
                dummy.getType() == DummyType.PLAYER ? "dummy.player.invincible" : "dummy.mob.invincible", true);
        if (invincible) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (victim.isValid()) restoreHp(victim);
            }, 1L);
        }

        // Reset accumulated jika HP simulasi habis
        if (simulatedHp <= 0) {
            damageAccumulated.put(victimUuid, 0.0);
            simulatedHp = dummy.getMaxHp();
        }

        String damageType = event.getCause().name().replace("_", " ");
        String skillName = "N/A";
        Player attacker = null;

        if (event instanceof EntityDamageByEntityEvent byEntity) {
            attacker = resolveAttacker(byEntity);
            skillName = resolveSkillName(byEntity);
        }

        final double finalSimulatedHp = simulatedHp;
        final String finalSkill = skillName;
        final String finalType = damageType;
        final Player finalAttacker = attacker;
        final String entityLabel = dummy.getType() == DummyType.PLAYER ? "Player Dummy" : "Mob Dummy";
        final double dmg = finalDamage;

        // Update nametag langsung dengan HP simulasi (no delay)
        updateNametagWithHp(victim, dummy, finalSimulatedHp);

        // Delay HANYA untuk efek — potion effect butuh beberapa tick untuk apply
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            String effects = buildEffects(victim);

            int decimals = plugin.getConfig().getInt("analytics.decimal-places", 2);
            String format = plugin.getConfig().getString("analytics.format",
                    "&8[&6VEH&8] &7{entity} &8| &c{damage} DMG &8| &eType: {type} &8| &dSkill: {skill} &8| &aEffects: {effects}");

            String message = format
                    .replace("{entity}", entityLabel)
                    .replace("{damage}", String.format("%." + decimals + "f", dmg))
                    .replace("{type}", finalType)
                    .replace("{skill}", finalSkill)
                    .replace("{effects}", effects);

            broadcastAnalytics(message, finalAttacker);
        }, 5L); // 5 tick cukup untuk MM/MMOItems apply potion
    }

    // -----------------------------------------------------------------------
    // Prevent dummy death — respawn di tempat yang sama
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        DummyManager manager = plugin.getDummyManager();
        UUID uuid = event.getEntity().getUniqueId();
        if (!manager.isDummy(uuid)) return;

        event.getDrops().clear();
        event.setDroppedExp(0);

        Optional<DummyEntity> dummyOpt = manager.getDummy(uuid);
        if (dummyOpt.isEmpty()) return;

        DummyEntity dummy = dummyOpt.get();
        var loc = event.getEntity().getLocation().clone();
        var type = dummy.getType();

        // Reset accumulated damage saat respawn
        damageAccumulated.remove(uuid);
        manager.unregisterDummy(uuid);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (type == DummyType.MOB) manager.spawnMobDummy(loc);
            else manager.spawnPlayerDummy(loc);
        }, 2L);
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private void restoreHp(LivingEntity entity) {
        try {
            var attr = entity.getAttribute(Attribute.MAX_HEALTH);
            double max = attr != null ? attr.getValue() : 20.0;
            entity.setHealth(Math.min(max, 1024.0));
        } catch (Exception ignored) {}
    }

    /**
     * Update nametag dengan HP simulasi (bukan HP entity yang selalu full).
     */
    private void updateNametagWithHp(LivingEntity entity, DummyEntity dummy, double simulatedHp) {
        String template = dummy.getNametag();
        String hp = String.format("%.1f", simulatedHp);
        String maxHp = String.format("%.1f", dummy.getMaxHp());

        String processed = template
                .replace("<hp>", hp)
                .replace("<maxhp>", maxHp);

        net.kyori.adventure.text.Component component;
        if (processed.contains("&")) {
            component = LegacyComponentSerializer.legacyAmpersand().deserialize(processed);
        } else {
            component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(processed);
        }
        entity.customName(component);
        entity.setCustomNameVisible(true);
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        var damager = event.getDamager();
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        return null;
    }

    /**
     * Resolusi nama skill dengan prioritas:
     * 1. Player yang pakai skill MMOItems → cek apakah item di tangan adalah MMOItem
     * 2. MythicMob attacker → tampilkan nama mob-nya
     * 3. Projectile dari player/mob
     * 4. Melee biasa
     *
     * TIDAK fallback ke nama item supaya output bersih.
     */
    private String resolveSkillName(EntityDamageByEntityEvent event) {
        var damager = event.getDamager();

        // Cek metadata skill yang di-inject MythicMobs/MythicLib
        for (String key : List.of("MythicMobsSkill", "MythicSkill", "mmskill", "SkillCaster", "origin")) {
            if (damager.hasMetadata(key)) {
                try {
                    String val = damager.getMetadata(key).get(0).asString();
                    if (val != null && !val.isEmpty()) return val;
                } catch (Exception ignored) {}
            }
        }

        // Projectile — cek shooter
        if (damager instanceof Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof LivingEntity shooter) {
                for (String key : List.of("MythicMobsSkill", "MythicSkill", "mmskill")) {
                    if (shooter.hasMetadata(key)) {
                        try {
                            String val = shooter.getMetadata(key).get(0).asString();
                            if (val != null && !val.isEmpty()) return val;
                        } catch (Exception ignored) {}
                    }
                }
                // MythicMob shooter
                try {
                    var mob = MythicBukkit.inst().getMobManager().getActiveMob(shooter.getUniqueId());
                    if (mob.isPresent()) return "MythicMob:" + mob.get().getType().getInternalName();
                } catch (Exception ignored) {}
                if (src instanceof Player) return "Projectile";
            }
        }

        // MythicMob melee
        try {
            if (damager instanceof LivingEntity le) {
                var mob = MythicBukkit.inst().getMobManager().getActiveMob(le.getUniqueId());
                if (mob.isPresent()) return "MythicMob:" + mob.get().getType().getInternalName();
            }
        } catch (Exception ignored) {}

        // Player — kembalikan "Melee" saja, bukan nama item
        if (damager instanceof Player) {
            return "Melee";
        }

        return "N/A";
    }

    /**
     * Baca potion effects yang aktif di entity.
     * Dipanggil setelah delay 5 tick supaya MM/MMOItems sudah apply effect-nya.
     */
    private String buildEffects(LivingEntity entity) {
        Collection<PotionEffect> effects = entity.getActivePotionEffects();
        if (effects.isEmpty()) {
            return plugin.getConfig().getBoolean("analytics.show-no-effects", true) ? "None" : "";
        }

        List<String> result = new ArrayList<>();
        for (PotionEffect effect : effects) {
            String name = effect.getType().getName().replace("_", " ");
            int amp = effect.getAmplifier() + 1;
            int dur = effect.getDuration();
            String durStr = dur >= 32767 ? "∞" : (dur / 20) + "s";
            result.add(name + " " + amp + " [" + durStr + "]");
        }
        return String.join(", ", result);
    }

    private void broadcastAnalytics(String message, Player directAttacker) {
        boolean broadcast = plugin.getConfig().getBoolean("settings.broadcast-damage", false);

        if (directAttacker != null && directAttacker.hasPermission("visantara.damage.output")) {
            directAttacker.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
        }

        if (broadcast) {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.hasPermission("visantara.damage.output")
                        && (directAttacker == null || !online.equals(directAttacker))) {
                    online.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
                }
            }
        }
    }
}