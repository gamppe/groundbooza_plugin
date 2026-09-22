package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * What the skills actually do. Dispatch is on {@link ClassSkill#id()}, so adding a skill is an
 * entry in {@link MagicClass} plus a case here; anything without a case falls back to the
 * placeholder flourish.
 *
 * <p>Two skills are two-step: 파동탄 for a 수도사 leaves a mark to blink to, and 뇌격 leaps
 * before it dashes. Both second steps are casts of the same item, so they answer
 * {@link #isFollowUp} and the caller lets them through without touching the cooldown.
 */
public class SkillEffects implements Listener {

    // ---------- 파동탄 ----------
    private static final double WAVE_SHOT_DAMAGE = 3.0;
    /** 수도사 trades the punch for the blink that follows. */
    private static final double WAVE_SHOT_MONK_DAMAGE = 1.0;
    private static final double WAVE_SHOT_SPEED = 1.6;
    /** Deliberately gentle - the wind burst vanilla would apply is far stronger. */
    private static final double WAVE_SHOT_KNOCKBACK = 0.45;
    private static final int WAVE_SHOT_REWARD_TICKS = 10 * 20;
    private static final int WAVE_SHOT_MONK_LIFETIME_TICKS = 2 * 20;
    private static final long BLINK_WINDOW_MILLIS = 3000;
    private static final double BLINK_DAMAGE = 5.0;
    private static final double BLINK_RADIUS = 3.0;

    // ---------- 볼트마법 ----------
    private static final double BOLT_DAMAGE = 5.0;
    private static final double BOLT_SPEED = 1.9;

    // ---------- 돼지 소환 ----------
    private static final int PIG_COUNT = 10;
    private static final double PIG_SCATTER_SPEED = 0.7;
    private static final double PIG_SCATTER_LIFT = 0.45;

    // ---------- 뇌격 ----------
    private static final double THUNDER_LEAP = 1.9;
    private static final double THUNDER_DASH_SPEED = 1.8;
    /** How far up the dash may be aimed. Look higher than this and it is pulled back down to
     * here, which is what turns an over-eager aim into a dive at the floor. */
    private static final double THUNDER_MAX_UPWARD = 0.25;
    private static final double THUNDER_LAND_RADIUS = 5.0;
    private static final double THUNDER_LAND_DAMAGE = 5.0;
    private static final int THUNDER_SLOW_TICKS = 3 * 20;
    /** Give up waiting for a landing after this, so nobody is left mid-skill forever. */
    private static final long THUNDER_TIMEOUT_MILLIS = 15000;

    /** The mark follows the mob rather than the spot it was standing on, so a target that runs
     * during the three seconds is still where the blink lands. {@code lastKnown} is only there
     * for the case where it dies or despawns before the follow-up. */
    private record Blink(UUID targetId, Location lastKnown, long expiresAtMillis) {}

    /** {@code leftGround} matters: velocity is applied a tick before the player actually rises,
     * so without it the very next tick sees them still standing and cancels the whole skill. */
    private record Leap(boolean dashed, boolean leftGround, long startedAtMillis) {}

    private final MagicWarPlugin plugin;
    private final ClassManager classes;
    private final SkillCooldowns cooldowns;
    private final NamespacedKey waveShotKey;
    private final NamespacedKey boltKey;
    private final Random random = new Random();
    private final Map<UUID, Blink> blinks = new HashMap<>();
    private final Map<UUID, Leap> leaps = new HashMap<>();
    /** Players mid-뇌격, exempt from fall damage until shortly after they touch down. */
    private final Set<UUID> noFall = new HashSet<>();

    public SkillEffects(MagicWarPlugin plugin, ClassManager classes, SkillCooldowns cooldowns) {
        this.plugin = plugin;
        this.classes = classes;
        this.cooldowns = cooldowns;
        this.waveShotKey = new NamespacedKey(plugin, "wave_shot");
        this.boltKey = new NamespacedKey(plugin, "bolt");
    }

    /** True when this cast is the second half of a two-step skill, which the caller uses to
     * skip both the cooldown check and spending a charge. */
    public boolean isFollowUp(Player player, ClassSkill skill) {
        return switch (skill.id()) {
            case "wave_shot" -> blinkReady(player);
            case "thunder_strike" -> leapReady(player);
            default -> false;
        };
    }

    /** @return false when the skill could not be cast after all, so the caller can skip the
     * cooldown. */
    public boolean cast(Player player, ClassSkill skill) {
        return switch (skill.id()) {
            case "wave_shot" -> waveShot(player);
            case "bolt" -> bolt(player);
            case "pig_burst" -> pigBurst(player);
            case "thunder_strike" -> thunderStrike(player);
            default -> placeholder(player, skill);
        };
    }

    /** Hide or restore the sweep on whichever item carries this skill. Silent when the player
     * somehow no longer owns it. */
    private void showFollowUp(Player player, String skillId, boolean pending) {
        ClassSkill skill = classes.skillById(player.getUniqueId(), skillId);
        if (skill == null) {
            return;
        }
        if (pending) {
            cooldowns.suspend(player, skill);
        } else {
            cooldowns.resume(player, skill);
        }
    }

    private boolean isMonk(Player player) {
        MagicClass.Advancement advancement = classes.advancementOf(player.getUniqueId());
        return advancement != null && advancement.id().equals("monk");
    }

    // ---------- 배틀메이지 1 : 파동탄 (수도사면 2단 스킬) ----------

    private boolean waveShot(Player player) {
        if (blinkReady(player)) {
            return blinkStrike(player);
        }
        Vector direction = player.getEyeLocation().getDirection().normalize().multiply(WAVE_SHOT_SPEED);
        BreezeWindCharge charge = player.launchProjectile(BreezeWindCharge.class, direction, shot ->
                shot.getPersistentDataContainer().set(waveShotKey, PersistentDataType.BOOLEAN, true));
        if (isMonk(player)) {
            // A 수도사 shot is a marker, not a missile: short-lived, and the blink is the payoff.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (charge.isValid()) {
                    charge.remove();
                }
            }, WAVE_SHOT_MONK_LIFETIME_TICKS);
        }
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
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        boolean monk = isMonk(shooter);
        Entity hit = event.getHitEntity();

        if (hit instanceof LivingEntity target && !target.equals(shooter)) {
            event.setCancelled(true);
            projectile.remove();
            target.damage(monk ? WAVE_SHOT_MONK_DAMAGE : WAVE_SHOT_DAMAGE, shooter);
            Vector push = target.getLocation().toVector().subtract(shooter.getLocation().toVector());
            if (push.lengthSquared() < 1.0E-4) {
                push = shooter.getEyeLocation().getDirection();
            }
            push = push.normalize().multiply(WAVE_SHOT_KNOCKBACK).setY(0.25);
            target.setVelocity(target.getVelocity().add(push));
            target.getWorld().spawnParticle(Particle.GUST, target.getLocation().add(0, 1, 0), 1);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1.3f);

            if (monk) {
                markBlink(shooter, target);
            } else {
                // Landing the hit is what pays out, so a miss gives nothing.
                shooter.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, WAVE_SHOT_REWARD_TICKS, 0));
                shooter.sendActionBar(Component.text("명중! 신속 10초", NamedTextColor.AQUA));
            }
            return;
        }
    }

    private void markBlink(Player player, LivingEntity target) {
        blinks.put(player.getUniqueId(), new Blink(target.getUniqueId(), target.getLocation().clone(),
                System.currentTimeMillis() + BLINK_WINDOW_MILLIS));
        showFollowUp(player, "wave_shot", true);
        player.playSound(player, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.8f);
        player.sendActionBar(Component.text("3초 내로 다시 시전하면 그 자리로 이동합니다", NamedTextColor.GOLD));
    }

    private boolean blinkReady(Player player) {
        Blink blink = blinks.get(player.getUniqueId());
        if (blink == null) {
            return false;
        }
        if (System.currentTimeMillis() > blink.expiresAtMillis()) {
            blinks.remove(player.getUniqueId());
            showFollowUp(player, "wave_shot", false);
            return false;
        }
        return true;
    }

    /** Where the marked mob is now, falling back to where it was hit if it is gone. */
    private Location currentPositionOf(Blink blink) {
        Entity marked = Bukkit.getEntity(blink.targetId());
        if (marked instanceof LivingEntity alive && alive.isValid()) {
            return alive.getLocation().clone();
        }
        return blink.lastKnown();
    }

    private boolean blinkStrike(Player player) {
        Blink blink = blinks.remove(player.getUniqueId());
        showFollowUp(player, "wave_shot", false);
        if (blink == null) {
            return false;
        }
        Location target = currentPositionOf(blink);
        if (target == null || !target.getWorld().equals(player.getWorld())) {
            return false;
        }
        Location from = player.getLocation();
        from.getWorld().spawnParticle(Particle.PORTAL, from.add(0, 1, 0), 30, 0.3, 0.6, 0.3);

        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        player.teleport(target);

        for (Entity nearby : player.getNearbyEntities(BLINK_RADIUS, BLINK_RADIUS, BLINK_RADIUS)) {
            if (nearby instanceof LivingEntity victim && !victim.equals(player)) {
                victim.damage(BLINK_DAMAGE, player);
            }
        }
        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.clone().add(0, 1, 0), 8, 1.0, 0.5, 1.0);
        target.getWorld().playSound(target, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.9f);
        player.sendActionBar(Component.text("추격!", NamedTextColor.GOLD));
        return true;
    }

    // ---------- 수도사 2 : 뇌격 ----------

    private boolean thunderStrike(Player player) {
        if (leapReady(player)) {
            return thunderDash(player);
        }
        player.setVelocity(player.getVelocity().setY(THUNDER_LEAP));
        leaps.put(player.getUniqueId(), new Leap(false, false, System.currentTimeMillis()));
        noFall.add(player.getUniqueId());
        showFollowUp(player, "thunder_strike", true);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 0.9f);
        player.sendActionBar(Component.text("공중에서 다시 시전하면 돌진합니다", NamedTextColor.GOLD));
        return true;
    }

    /** The dash is only offered while the leap is still in the air and has not been spent. */
    private boolean leapReady(Player player) {
        Leap leap = leaps.get(player.getUniqueId());
        return leap != null && !leap.dashed() && !player.isOnGround();
    }

    /** The leap is high enough that the landing would otherwise hurt more than the skill does. */
    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && noFall.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private boolean thunderDash(Player player) {
        Vector direction = player.getEyeLocation().getDirection().normalize();
        if (direction.getY() > THUNDER_MAX_UPWARD) {
            // Clamped, then re-normalised, so aiming at the sky becomes a dive instead of a hop.
            direction.setY(THUNDER_MAX_UPWARD);
            direction.normalize();
        }
        player.setVelocity(direction.multiply(THUNDER_DASH_SPEED));
        Leap current = leaps.get(player.getUniqueId());
        leaps.put(player.getUniqueId(), new Leap(true, current != null && current.leftGround(),
                System.currentTimeMillis()));
        showFollowUp(player, "thunder_strike", false);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_THROW, 1f, 0.8f);
        player.sendActionBar(Component.text("뇌격!", NamedTextColor.GOLD));
        return true;
    }

    /** Called every tick from the plugin: watches for a dashing player touching down. */
    public void tick() {
        expireBlinks();
        Iterator<Map.Entry<UUID, Leap>> it = leaps.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Leap> entry = it.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            Leap leap = entry.getValue();
            if (player == null || !player.isOnline()
                    || System.currentTimeMillis() - leap.startedAtMillis() > THUNDER_TIMEOUT_MILLIS) {
                it.remove();
                if (player != null) {
                    showFollowUp(player, "thunder_strike", false);
                }
                continue;
            }
            if (!player.isOnGround()) {
                if (!leap.leftGround()) {
                    entry.setValue(new Leap(leap.dashed(), true, leap.startedAtMillis()));
                }
                if (leap.dashed()) {
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation(), 3, 0.2, 0.2, 0.2);
                }
                continue;
            }
            if (!leap.leftGround()) {
                continue; // still on the launch tick - the jump has not started yet
            }
            it.remove();
            showFollowUp(player, "thunder_strike", false);
            if (leap.dashed()) {
                thunderLanding(player);
            }
            // A moment's grace so the slam itself does not hurt the caster.
            Bukkit.getScheduler().runTaskLater(plugin, () -> noFall.remove(player.getUniqueId()), 10L);
        }
    }

    /** The three-second window closes on its own, so the sweep has to come back on its own too
     * rather than waiting for the next cast to notice. */
    private void expireBlinks() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Blink>> it = blinks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Blink> entry = it.next();
            if (now <= entry.getValue().expiresAtMillis()) {
                continue;
            }
            it.remove();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                showFollowUp(player, "wave_shot", false);
            }
        }
    }

    /** Lightning is struck for effect only - the damage and the slow are applied by hand, since
     * a real bolt would also set the arena on fire. */
    private void thunderLanding(Player player) {
        Location at = player.getLocation();
        at.getWorld().strikeLightningEffect(at);
        at.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 1, 0), 60, 1.5, 0.5, 1.5, 0.2);
        at.getWorld().playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.2f);

        for (Entity nearby : player.getNearbyEntities(THUNDER_LAND_RADIUS, THUNDER_LAND_RADIUS, THUNDER_LAND_RADIUS)) {
            if (nearby instanceof LivingEntity victim && !victim.equals(player)) {
                victim.damage(THUNDER_LAND_DAMAGE, player);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, THUNDER_SLOW_TICKS, 1));
                victim.getWorld().strikeLightningEffect(victim.getLocation());
            }
        }
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

    public void clear() {
        blinks.clear();
        leaps.clear();
        noFall.clear();
    }
}
