package me.bintanq.listener;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.events.MythicMobDamageEvent;
import me.bintanq.VisantaraEventHandler;
import me.bintanq.dummy.DummyEntity;
import me.bintanq.dummy.DummyType;
import me.bintanq.manager.DummyManager;
import net.Indyuce.mmoitems.api.event.PlayerAttackEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.potion.PotionEffect;

import java.util.Optional;
import java.util.StringJoiner;

public class DamageListener implements Listener {

    private final VisantaraEventHandler plugin;

    public DamageListener(VisantaraEventHandler plugin) {
        this.plugin = plugin;
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

        // Damage type
        String damageType = event.getCause().name().replace("_", " ");

        // Attacker / skill name resolution
        String skillName = "N/A";
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            skillName = resolveAttackerLabel(byEntity);
        }

        // Active effects on the dummy
        String effects = buildEffectsString(victim);

        // Build the analytics message
        String format = plugin.getConfig().getString("analytics.format",
                "&8[&6VEH&8] &7{entity} &8| &c{damage} DMG &8| &eType: {type} &8| &dSkill: {skill} &8| &aEffects: {effects}");

        int decimals = plugin.getConfig().getInt("analytics.decimal-places", 2);
        String dmgStr = String.format("%." + decimals + "f", finalDamage);

        String entityLabel = (dummy.getType() == DummyType.PLAYER) ? "Player Dummy" : "Mob Dummy";

        String message = format
                .replace("{entity}", entityLabel)
                .replace("{damage}", dmgStr)
                .replace("{type}", damageType)
                .replace("{skill}", skillName)
                .replace("{effects}", effects);

        broadcastAnalytics(message, event);

        // Invincibility logic — restore health after damage
        boolean invincible = plugin.getConfig().getBoolean(
                dummy.getType() == DummyType.PLAYER ? "dummy.player.invincible" : "dummy.mob.invincible", true);

        if (invincible) {
            double currentHp = victim.getHealth();
            double newHp = Math.max(1.0, currentHp - finalDamage);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (victim.isValid()) {
                    victim.setHealth(Math.max(1.0, victim.getHealth()));
                    // After restoring, update nametag with simulated lower HP for display
                    // We show the accumulated "virtual" damage for realism
                    manager.getDummy(victim.getUniqueId()).ifPresent(d -> manager.updateNametag(victim, d));
                }
            }, 1L);
        } else {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (victim.isValid()) {
                    manager.getDummy(victim.getUniqueId()).ifPresent(d -> manager.updateNametag(victim, d));
                }
            }, 1L);
        }
    }

    // -----------------------------------------------------------------------
    // MMOItems custom damage integration
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMMOItemsAttack(PlayerAttackEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        DummyManager manager = plugin.getDummyManager();
        if (!manager.isDummy(victim.getUniqueId())) return;

        Optional<DummyEntity> dummyOpt = manager.getDummy(victim.getUniqueId());
        if (dummyOpt.isEmpty()) return;

        DummyEntity dummy = dummyOpt.get();

        double finalDamage = event.getAttack().getDamage().getDamage();
        double minDamage = plugin.getConfig().getDouble("settings.min-display-damage", 0.0);
        if (finalDamage < minDamage) return;

        String damageType = "MMOITEMS";
        String itemName = "N/A";
        try {
            if (event.getAttack().getItem() != null) {
                itemName = event.getAttack().getItem().getType().getId();
            }
        } catch (Exception ignored) {}

        // Check for custom damage type
        try {
            String customType = event.getAttack().getDamage().getMainDamageType() != null
                    ? event.getAttack().getDamage().getMainDamageType().getName()
                    : "PHYSICAL";
            damageType = "MMOItems:" + customType;
        } catch (Exception ignored) {
            damageType = "MMOItems:PHYSICAL";
        }

        String effects = buildEffectsString(victim);
        String format = plugin.getConfig().getString("analytics.format",
                "&8[&6VEH&8] &7{entity} &8| &c{damage} DMG &8| &eType: {type} &8| &dSkill: {skill} &8| &aEffects: {effects}");
        int decimals = plugin.getConfig().getInt("analytics.decimal-places", 2);
        String dmgStr = String.format("%." + decimals + "f", finalDamage);
        String entityLabel = (dummy.getType() == DummyType.PLAYER) ? "Player Dummy" : "Mob Dummy";

        String message = format
                .replace("{entity}", entityLabel)
                .replace("{damage}", dmgStr)
                .replace("{type}", damageType)
                .replace("{skill}", itemName)
                .replace("{effects}", effects);

        // Broadcast to attacker
        if (event.getPlayer() != null) {
            sendMessage(event.getPlayer(), message);
        }
        if (plugin.getConfig().getBoolean("settings.broadcast-damage", false)) {
            broadcastToOps(message);
        }
    }

    // -----------------------------------------------------------------------
    // MythicMobs damage integration
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMythicDamage(MythicMobDamageEvent event) {
        // MythicMob dealing damage to a dummy
        if (!(event.getTarget() instanceof LivingEntity victim)) return;

        DummyManager manager = plugin.getDummyManager();
        if (!manager.isDummy(victim.getUniqueId())) return;

        Optional<DummyEntity> dummyOpt = manager.getDummy(victim.getUniqueId());
        if (dummyOpt.isEmpty()) return;

        DummyEntity dummy = dummyOpt.get();
        double finalDamage = event.getDamage();

        String damageType = "MythicMobs";
        String skillName = "N/A";
        try {
            if (event.getSkill() != null) {
                skillName = event.getSkill().getSkillName();
            }
        } catch (Exception ignored) {}

        String effects = buildEffectsString(victim);
        String format = plugin.getConfig().getString("analytics.format",
                "&8[&6VEH&8] &7{entity} &8| &c{damage} DMG &8| &eType: {type} &8| &dSkill: {skill} &8| &aEffects: {effects}");
        int decimals = plugin.getConfig().getInt("analytics.decimal-places", 2);
        String dmgStr = String.format("%." + decimals + "f", finalDamage);
        String entityLabel = (dummy.getType() == DummyType.PLAYER) ? "Player Dummy" : "Mob Dummy";

        String message = format
                .replace("{entity}", entityLabel)
                .replace("{damage}", dmgStr)
                .replace("{type}", damageType)
                .replace("{skill}", skillName)
                .replace("{effects}", effects);

        broadcastToOps(message);
    }

    // -----------------------------------------------------------------------
    // Prevent dummy death
    // -----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        DummyManager manager = plugin.getDummyManager();
        if (!manager.isDummy(event.getEntity().getUniqueId())) return;

        // Cancel drops and XP
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Respawn the dummy at death location on next tick
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

    private String resolveAttackerLabel(EntityDamageByEntityEvent event) {
        // Check if damager is a MythicMob with an active skill
        // MythicMobs stores skill metadata on the projectile/entity
        var damager = event.getDamager();

        // Try to get skill name from metadata
        if (damager.hasMetadata("MVdamager")) {
            try {
                return damager.getMetadata("MVdamager").get(0).asString();
            } catch (Exception ignored) {}
        }

        if (damager instanceof Player p) {
            return "Player(" + p.getName() + ")";
        }

        if (damager instanceof LivingEntity le) {
            // Check if it's a MythicMob
            try {
                var activeMob = io.lumine.mythic.bukkit.MythicBukkit.inst().getMobManager().getActiveMob(le.getUniqueId());
                if (activeMob.isPresent()) {
                    return "MythicMob(" + activeMob.get().getType().getInternalName() + ")";
                }
            } catch (Exception ignored) {}
            return le.getType().name();
        }

        return damager.getType().name();
    }

    private String buildEffectsString(LivingEntity entity) {
        var effects = entity.getActivePotionEffects();
        if (effects.isEmpty()) {
            boolean showNone = plugin.getConfig().getBoolean("analytics.show-no-effects", true);
            return showNone ? "None" : "";
        }

        StringJoiner joiner = new StringJoiner(", ");
        for (PotionEffect effect : effects) {
            int durationTicks = effect.getDuration();
            String durationStr = durationTicks >= 32767 ? "∞" : (durationTicks / 20) + "s";
            joiner.add(formatEffectName(effect.getType().getName()) + " " + (effect.getAmplifier() + 1) + " [" + durationStr + "]");
        }
        return joiner.toString();
    }

    private String formatEffectName(String raw) {
        return raw.replace("_", " ");
    }

    private void broadcastAnalytics(String message, EntityDamageEvent event) {
        boolean broadcast = plugin.getConfig().getBoolean("settings.broadcast-damage", false);

        if (event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player attacker) {
            if (attacker.hasPermission("visantara.damage.output")) {
                sendMessage(attacker, message);
            }
        }

        if (broadcast) {
            broadcastToOps(message);
        }
    }

    private void broadcastToOps(String message) {
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.hasPermission("visantara.damage.output")) {
                sendMessage(online, message);
            }
        }
    }

    private void sendMessage(Player player, String message) {
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }
}