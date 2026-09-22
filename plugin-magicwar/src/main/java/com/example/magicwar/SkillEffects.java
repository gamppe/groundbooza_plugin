package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BreezeWindCharge;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * What the skills actually do. Dispatch is on {@link ClassSkill#id()}, so adding a skill is an
 * entry in {@link MagicClass} plus a case here; anything without a case falls back to the
 * placeholder flourish.
 */
public class SkillEffects implements Listener {

    // ---------- 파동탄 ----------
    private static final double WAVE_SHOT_DAMAGE = 3.0;
    private static final double WAVE_SHOT_SPEED = 1.6;
    /** Deliberately gentle - the wind burst vanilla would apply is far stronger. */
    private static final double WAVE_SHOT_KNOCKBACK = 0.45;
    private static final int WAVE_SHOT_REWARD_TICKS = 10 * 20;

    // ---------- 볼트마법 ----------
    private static final double BOLT_DAMAGE = 5.0;
    private static final double BOLT_SPEED = 1.9;

    // ---------- 돼지 소환 ----------
    private static final int PIG_COUNT = 10;
    private static final double PIG_SCATTER_SPEED = 0.7;
    private static final double PIG_SCATTER_LIFT = 0.45;

    private final NamespacedKey waveShotKey;
    private final NamespacedKey boltKey;
    private final Random random = new Random();

    public SkillEffects(MagicWarPlugin plugin) {
        this.waveShotKey = new NamespacedKey(plugin, "wave_shot");
        this.boltKey = new NamespacedKey(plugin, "bolt");
    }

    /** @return false when the skill could not be cast after all, so the caller can skip the
     * cooldown. */
    public boolean cast(Player player, ClassSkill skill) {
        return switch (skill.id()) {
            case "wave_shot" -> waveShot(player);
            case "bolt" -> bolt(player);
            case "pig_burst" -> pigBurst(player);
            default -> placeholder(player, skill);
        };
    }

    // ---------- 배틀메이지 1 : 파동탄 ----------

    private boolean waveShot(Player player) {
        Vector direction = player.getEyeLocation().getDirection().normalize().multiply(WAVE_SHOT_SPEED);
        player.launchProjectile(BreezeWindCharge.class, direction, charge ->
                charge.getPersistentDataContainer().set(waveShotKey, PersistentDataType.BOOLEAN, true));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.1f);
        player.sendActionBar(Component.text("파동탄!", NamedTextColor.AQUA));
        return true;
    }

    /** The burst is cancelled and replaced: vanilla's wind charge throws a target much further
     * than "조금 밀쳐내고" and does no damage worth speaking of. */
    @EventHandler(ignoreCancelled = true)
    public void onWaveShotHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!Boolean.TRUE.equals(projectile.getPersistentDataContainer()
                .get(waveShotKey, PersistentDataType.BOOLEAN))) {
            return;
        }
        Entity hit = event.getHitEntity();
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        if (!(hit instanceof LivingEntity target) || target.equals(shooter)) {
            return; // a miss: let the projectile pop against the block as usual
        }
        event.setCancelled(true);
        projectile.remove();

        target.damage(WAVE_SHOT_DAMAGE, shooter);
        Vector push = target.getLocation().toVector().subtract(shooter.getLocation().toVector());
        if (push.lengthSquared() < 1.0E-4) {
            push = shooter.getEyeLocation().getDirection();
        }
        push = push.normalize().multiply(WAVE_SHOT_KNOCKBACK).setY(0.25);
        target.setVelocity(target.getVelocity().add(push));

        target.getWorld().spawnParticle(Particle.GUST, target.getLocation().add(0, 1, 0), 1);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1.3f);

        // Landing the hit is what pays out, so a miss gives nothing.
        shooter.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, WAVE_SHOT_REWARD_TICKS, 0));
        shooter.sendActionBar(Component.text("명중! 신속 10초", NamedTextColor.AQUA));
    }

    // ---------- 소서러 1 : 볼트마법 ----------

    /** A small fireball stripped of everything that makes it a fireball: no block damage, no
     * fires started. It is here for the flame trail - the bolt's damage is applied by hand. */
    private boolean bolt(Player player) {
        Vector direction = player.getEyeLocation().getDirection().normalize().multiply(BOLT_SPEED);
        player.launchProjectile(SmallFireball.class, direction, bolt -> {
            bolt.setIsIncendiary(false);
            bolt.setYield(0f);
            bolt.getPersistentDataContainer().set(boltKey, PersistentDataType.BOOLEAN, true);
        });
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.6f);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBoltHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!Boolean.TRUE.equals(projectile.getPersistentDataContainer()
                .get(boltKey, PersistentDataType.BOOLEAN))) {
            return;
        }
        // Cancelled either way: a fireball that touches a block would still scorch it.
        event.setCancelled(true);
        projectile.remove();
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        Entity hit = event.getHitEntity();
        if (hit instanceof LivingEntity target && !target.equals(shooter)) {
            target.damage(BOLT_DAMAGE, shooter);
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0),
                    20, 0.3, 0.4, 0.3, 0.1);
        }
        projectile.getWorld().playSound(projectile.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 2f);
    }

    // ---------- 서머너 1 : 돼지 소환 ----------

    /** Ten pigs thrown outwards from where the caster stands, evenly around the circle with a
     * little jitter so it reads as a burst rather than a formation. */
    private boolean pigBurst(Player player) {
        Location centre = player.getLocation();
        for (int i = 0; i < PIG_COUNT; i++) {
            double angle = Math.PI * 2 * i / PIG_COUNT + random.nextDouble() * 0.3;
            double speed = PIG_SCATTER_SPEED * (0.8 + random.nextDouble() * 0.4);
            Pig pig = centre.getWorld().spawn(centre, Pig.class);
            pig.setVelocity(new Vector(Math.cos(angle) * speed, PIG_SCATTER_LIFT, Math.sin(angle) * speed));
        }
        centre.getWorld().spawnParticle(Particle.EXPLOSION, centre.clone().add(0, 1, 0), 3, 0.3, 0.3, 0.3);
        centre.getWorld().playSound(centre, Sound.ENTITY_PIG_AMBIENT, 1.4f, 0.8f);
        centre.getWorld().playSound(centre, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.6f);
        return true;
    }

    // ---------- not written yet ----------

    private boolean placeholder(Player player, ClassSkill skill) {
        var from = player.getEyeLocation();
        player.getWorld().spawnParticle(Particle.ENCHANT, from.clone().add(from.getDirection().multiply(1.5)),
                40, 0.4, 0.4, 0.4, 0.5);
        player.getWorld().playSound(from, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);
        player.sendActionBar(Component.text(skill.name() + " 사용!", NamedTextColor.AQUA));
        return true;
    }
}
