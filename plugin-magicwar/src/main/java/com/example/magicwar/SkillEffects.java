package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.Sound;
import org.bukkit.entity.BreezeWindCharge;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pig;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
    private static final int WAVE_SHOT_MONK_LIFETIME_TICKS = 2 * 20;
    private static final long BLINK_WINDOW_MILLIS = 3000;
    private static final double BLINK_DAMAGE = 5.0;
    private static final double BLINK_RADIUS = 3.0;

    // ---------- 볼트마법 ----------
    private static final double BOLT_DAMAGE = 5.0;
    private static final double BOLT_SPEED = 1.5;

    // ---------- 일렉트로맨서 ----------
    private static final double BOLT_BEAM_DAMAGE = 2.0;
    private static final int BOLT_BEAM_RANGE = 30;
    /** Widens the beam a little so a shot that looks like a hit is one. */
    private static final double BOLT_BEAM_FORGIVENESS = 0.25;
    private static final int ROOT_TICKS = 4; // 0.2s
    private static final int SHOCK_SECONDS = 5;
    private static final int SHOCK_MAX_STACKS = 3;
    private static final int ROD_DELAY_TICKS = 12; // 0.6s
    private static final int ROD_REPEAT_TICKS = 10;
    /** Vanilla lightning deals 5, under the 8 that would have made it worth faking. */
    private static final double ROD_BOLT_DAMAGE = 2.0;

    // ---------- 돼지 소환 ----------
    private static final int PIG_COUNT = 10;
    private static final double PIG_SCATTER_SPEED = 0.7;
    private static final double PIG_SCATTER_LIFT = 0.45;
    private static final int PIG_FUSE_TICKS = 3 * 20;
    private static final double PIG_BLAST_DAMAGE = 6.0;
    private static final double PIG_BLAST_RADIUS = 3.0;

    // ---------- 네크로맨서 ----------
    /** Mob movement speed is roughly blocks-per-second over twenty, and a sprinting player
     * covers 5.6 - so this is about nine tenths of that. */
    private static final double PIG_CHASE_SPEED = 0.2526;
    private static final int PIG_HUNTER_FUSE_TICKS = 10 * 20;
    private static final int PIGLIN_FUSE_TICKS = 20 * 20;
    private static final double PIGLIN_BLAST_DAMAGE = 6.0;
    private static final long CORPSE_REFUND_MILLIS = 3000;
    private static final double CORPSE_REFUND_RADIUS = 16;

    // ---------- 드루이드 ----------
    private static final int WOLF_PACK_SIZE = 5;
    private static final int WOLF_PACK_TICKS = 20 * 20;
    private static final int WOLF_PACK_SURVIVORS = 2;
    private static final int WILD_WOLF_TICKS = 60 * 20;
    private static final int TRACK_HASTE_TICKS = 20 * 20;

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
    // ---------- 서리기사 ----------
    private static final int FROSTBITE_SECONDS = 10;
    private static final int ICE_PRISON_TICKS = 2 * 20;
    private static final int ICE_PRISON_SLOW_AMPLIFIER = 9; // 구속 X
    private static final double ICE_PRISON_DAMAGE = 5.0;
    private static final double ICE_PRISON_RADIUS = 1.6;
    private static final int ICE_SPIKE_DIRECTIONS = 8;
    private static final int ICE_SPIKE_RANGE = 20;
    private static final int ICE_SPIKE_STEP_TICKS = 2;
    /** Blocks either side of the centre line: 0 keeps each arm a single file. */
    private static final int ICE_SPIKE_HALF_WIDTH = 0;
    private static final Material ICE_SPIKE_MARKER = Material.BLUE_STAINED_GLASS;
    private static final Material ERUPTION_MARKER = Material.MAGMA_BLOCK;
    private static final double ICE_SPIKE_DAMAGE = 8.0;
    private static final int ICE_SPIKE_LINGER_TICKS = 6 * 20;

    // ---------- 파이로맨서 ----------
    /** Small enough to singe rather than crater - block damage is off regardless. */
    private static final float BOLT_BLAST_POWER = 1.2f;
    private static final int ERUPTION_RANGE = 24;
    /** 7x7 masked to a circle. */
    private static final int ERUPTION_RADIUS = 3;
    private static final int ERUPTION_MAGMA_TICKS = 8 * 20;
    private static final int ERUPTION_DELAY_TICKS = 15; // 0.75s between the magma and the lava
    private static final int ERUPTION_HEIGHT = 20;
    private static final int ERUPTION_STEP_TICKS = 2;
    /** Steps of head start the centre gets over a block one further out. Delaying per block
     * index instead made the outer ring wait on all twenty-odd blocks ahead of it, which read
     * as a slow crawl rather than a burst. */
    private static final double ERUPTION_SPREAD_STEPS_PER_BLOCK = 1.5;
    /** Each lava block is only there for a moment, so the column reads as a spout. */
    private static final int ERUPTION_LAVA_TICKS = 20;

    private static final int DUST_RING_POINTS = 16;
    private static final double DUST_RING_RADIUS = 2.2;

    /** The mark follows the mob rather than the spot it was standing on, so a target that runs
     * during the three seconds is still where the blink lands. {@code lastKnown} is only there
     * for the case where it dies or despawns before the follow-up. */
    private record Blink(UUID targetId, Location lastKnown, long expiresAtMillis) {}

    private record Shock(int stacks, long expiresAtMillis) {}

    /** {@code leftGround} matters: velocity is applied a tick before the player actually rises,
     * so without it the very next tick sees them still standing and cancels the whole skill. */
    private record Leap(boolean dashed, boolean leftGround, long startedAtMillis) {}

    private final MagicWarPlugin plugin;
    private final ClassManager classes;
    private final SkillCooldowns cooldowns;
    private final FrostState frost;
    private final TempBlocks tempBlocks;
    private final SkillItem skillItems;
    private final SkillPreview preview;
    private final Summons summons;
    /** Players 흔적 추적 has already given away, per caster - one report each. */
    private final Map<UUID, Set<UUID>> tracked = new HashMap<>();
    private final NamespacedKey waveShotKey;
    private final NamespacedKey boltKey;
    private final NamespacedKey summonedPigKey;
    private final Random random = new Random();
    private final Map<UUID, Blink> blinks = new HashMap<>();
    private final Map<UUID, Leap> leaps = new HashMap<>();
    /** Entity id to its 감전 stacks and when they lapse. */
    private final Map<UUID, Shock> shocks = new HashMap<>();
    /** Players mid-뇌격, exempt from fall damage until shortly after they touch down. */
    private final Set<UUID> noFall = new HashSet<>();

    public SkillEffects(MagicWarPlugin plugin, ClassManager classes, SkillCooldowns cooldowns,
                        FrostState frost, TempBlocks tempBlocks, SkillItem skillItems,
                        SkillPreview preview, Summons summons) {
        this.plugin = plugin;
        this.classes = classes;
        this.cooldowns = cooldowns;
        this.frost = frost;
        this.tempBlocks = tempBlocks;
        this.skillItems = skillItems;
        this.preview = preview;
        this.summons = summons;
        this.waveShotKey = new NamespacedKey(plugin, "wave_shot");
        this.boltKey = new NamespacedKey(plugin, "bolt");
        this.summonedPigKey = new NamespacedKey(plugin, "summoned_pig");
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
            case "ice_spike" -> iceSpike(player);
            case "lava_eruption" -> lavaEruption(player);
            case "lightning_rod" -> lightningRod(player);
            case "corpse_explosion" -> corpseExplosion(player);
            case "track" -> track(player);
            default -> placeholder(player, skill);
        };
    }

    /** Hide or restore the sweep on whichever item carries this skill. Silent when the player
     * somehow no longer owns it. */
    private void showFollowUp(Player player, String skillId, boolean pending, int windowTicks) {
        ClassSkill skill = classes.skillById(player.getUniqueId(), skillId);
        if (skill == null) {
            return;
        }
        if (pending) {
            cooldowns.suspend(player, skill, windowTicks);
        } else {
            cooldowns.resume(player, skill);
        }
    }

    private void showFollowUp(Player player, String skillId, boolean pending) {
        showFollowUp(player, skillId, pending, 0);
    }

    private boolean isMonk(Player player) {
        return isAdvancement(player, "monk");
    }

    private boolean isFrostKnight(Player player) {
        return isAdvancement(player, "frost_knight");
    }

    private boolean isPyromancer(Player player) {
        return isAdvancement(player, "pyromancer");
    }

    private boolean isElectromancer(Player player) {
        return isAdvancement(player, "electromancer");
    }

    /**
     * Every point of damage a skill deals goes through here.
     *
     * <p>Attribution is already right - {@code damage(amount, caster)} makes the caster the
     * killer and wakes the target's own aggro, exactly as an arrow would. What it does not do
     * is set the pack on them, so that is done explicitly.
     */
    private void hurt(Player caster, LivingEntity victim, double amount) {
        victim.damage(amount, caster);
        summons.rally(caster, victim);
    }

    private boolean isNecromancer(Player player) {
        return isAdvancement(player, "necromancer");
    }

    private boolean isDruid(Player player) {
        return isAdvancement(player, "druid");
    }

    private boolean isAdvancement(Player player, String id) {
        MagicClass.Advancement advancement = classes.advancementOf(player.getUniqueId());
        return advancement != null && advancement.id().equals(id);
    }

    /** Sealed inside an ice prison - checked by the caller before any skill goes off. */
    public boolean isSilenced(Player player) {
        return frost.isSilenced(player.getUniqueId());
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
        boolean frostKnight = isFrostKnight(shooter);
        Entity hit = event.getHitEntity();
        // Read before any damage lands, so a target that dies to the hit still counts as sealed.
        boolean wasFrostbitten = hit != null && frost.isFrostbitten(hit);

        if (hit instanceof LivingEntity target && !target.equals(shooter)) {
            event.setCancelled(true);
            projectile.remove();
            hurt(shooter, target, monk ? WAVE_SHOT_MONK_DAMAGE : WAVE_SHOT_DAMAGE);
            Vector push = target.getLocation().toVector().subtract(shooter.getLocation().toVector());
            if (push.lengthSquared() < 1.0E-4) {
                push = shooter.getEyeLocation().getDirection();
            }
            push = push.normalize().multiply(WAVE_SHOT_KNOCKBACK).setY(0.25);
            target.setVelocity(target.getVelocity().add(push));
            target.getWorld().spawnParticle(Particle.GUST, target.getLocation().add(0, 1, 0), 1);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1.3f);

            if (frostKnight) {
                // A second hit on something already frostbitten is the payoff: it gets sealed.
                if (wasFrostbitten) {
                    icePrison(shooter, target);
                } else {
                    frost.applyFrostbite(target, FROSTBITE_SECONDS);
                    target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1, 0),
                            25, 0.4, 0.6, 0.4, 0.02);
                }
            } else if (monk) {
                markBlink(shooter, target);
            }
            return;
        }
    }

    private void markBlink(Player player, LivingEntity target) {
        blinks.put(player.getUniqueId(), new Blink(target.getUniqueId(), target.getLocation().clone(),
                System.currentTimeMillis() + BLINK_WINDOW_MILLIS));
        showFollowUp(player, "wave_shot", true, (int) (BLINK_WINDOW_MILLIS / 50));
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
                hurt(player, victim, BLINK_DAMAGE);
            }
        }
        target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.clone().add(0, 1, 0), 8, 1.0, 0.5, 1.0);
        target.getWorld().playSound(target, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.9f);
        return true;
    }

    // ---------- 수도사 2 : 뇌격 ----------

    private boolean thunderStrike(Player player) {
        if (leapReady(player)) {
            return thunderDash(player);
        }
        // A leap only ever starts from the ground. Without this, a press in mid-air after the
        // dash was already spent simply launched again - endless hopping, and free, because a
        // refused cast costs no cooldown either.
        if (!player.isOnGround() || player.isFlying()) {
            player.sendActionBar(Component.text("땅에 서 있어야 시전할 수 있습니다.", NamedTextColor.RED));
            return false;
        }
        player.setVelocity(player.getVelocity().setY(THUNDER_LEAP));
        leaps.put(player.getUniqueId(), new Leap(false, false, System.currentTimeMillis()));
        noFall.add(player.getUniqueId());
        showFollowUp(player, "thunder_strike", true);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 0.9f);
        player.sendActionBar(Component.text("공중에서 다시 시전하면 돌진합니다", NamedTextColor.GOLD));
        return true;
    }

    /** The dash is only offered while the leap is still in the air and has not been spent.
     * Flight is excluded: a flying player never reports touching down, so the leap would hang
     * open forever. */
    private boolean leapReady(Player player) {
        Leap leap = leaps.get(player.getUniqueId());
        return leap != null && !leap.dashed() && !player.isOnGround() && !player.isFlying();
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
        return true;
    }

    /** Called every tick from the plugin: watches for a dashing player touching down. */
    public void tick() {
        expireBlinks();
        tickShocks();
        tickPreview();
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
            if (player.isFlying()) {
                // Taking off mid-skill: treat it as over rather than waiting for a landing
                // that will never be reported.
                it.remove();
                showFollowUp(player, "thunder_strike", false);
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
        kickUpDust(at);

        for (Entity nearby : player.getNearbyEntities(THUNDER_LAND_RADIUS, THUNDER_LAND_RADIUS, THUNDER_LAND_RADIUS)) {
            if (nearby instanceof LivingEntity victim && !victim.equals(player)) {
                hurt(player, victim, THUNDER_LAND_DAMAGE);
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, THUNDER_SLOW_TICKS, 1));
                victim.getWorld().strikeLightningEffect(victim.getLocation());
            }
        }
    }

    /** Debris off whatever was actually landed on, so a slam into sand throws sand. DUST_PILLAR
     * is the mace-smash column; the ring of BLOCK around it is the spray. */
    private void kickUpDust(Location at) {
        Block floor = at.clone().subtract(0, 1, 0).getBlock();
        if (floor.getType().isAir()) {
            floor = at.getBlock(); // landed on something thin - use what is underfoot instead
        }
        BlockData data = floor.getType().isAir() ? Material.DIRT.createBlockData() : floor.getBlockData();

        at.getWorld().spawnParticle(Particle.DUST_PILLAR, at, 60, 1.2, 0.1, 1.2, 0.0, data);
        for (int i = 0; i < DUST_RING_POINTS; i++) {
            double angle = Math.PI * 2 * i / DUST_RING_POINTS;
            Location edge = at.clone().add(Math.cos(angle) * DUST_RING_RADIUS, 0.1, Math.sin(angle) * DUST_RING_RADIUS);
            at.getWorld().spawnParticle(Particle.BLOCK, edge, 10, 0.3, 0.2, 0.3, 0.12, data);
        }
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.6f);
    }

    // ---------- 서리기사 : 얼음 감옥 ----------

    /** Seals a frostbitten target in a shell of ice for two seconds: slowed to a standstill,
     * unable to cast, and cracked open at the end for damage. The shell is temporary blocks,
     * so it goes back to whatever was there even if the round ends mid-freeze. */
    private void icePrison(Player caster, LivingEntity target) {
        Location centre = target.getLocation().add(0, 1, 0);
        int radius = (int) Math.ceil(ICE_PRISON_RADIUS);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    // Shell only: a solid ball would swallow the mob and hide the effect.
                    if (distance > ICE_PRISON_RADIUS || distance < ICE_PRISON_RADIUS - 1) {
                        continue;
                    }
                    Block block = centre.clone().add(dx, dy, dz).getBlock();
                    if (block.getType().isAir() || !block.getType().isSolid()) {
                        frost.placeTemporary(block, Material.ICE, ICE_PRISON_TICKS);
                    }
                }
            }
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ICE_PRISON_TICKS,
                ICE_PRISON_SLOW_AMPLIFIER));
        frost.silence(target.getUniqueId(), ICE_PRISON_TICKS);
        target.getWorld().playSound(centre, Sound.BLOCK_GLASS_PLACE, 1.2f, 0.6f);

        UUID targetId = target.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> shatterPrison(caster, targetId, centre), ICE_PRISON_TICKS);
    }

    private void shatterPrison(Player caster, UUID targetId, Location centre) {
        centre.getWorld().playSound(centre, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.8f);
        centre.getWorld().spawnParticle(Particle.BLOCK, centre, 60, 0.8, 0.8, 0.8, 0.1,
                Material.ICE.createBlockData());
        Entity sealed = Bukkit.getEntity(targetId);
        if (sealed instanceof LivingEntity alive && alive.isValid()) {
            hurt(caster, alive, ICE_PRISON_DAMAGE);
            frost.clearFrostbite(alive);
        }
    }

    // ---------- 서리기사 2 : 얼음송곳 ----------

    /** Eight lines of ice crawling outward a block every couple of ticks. Anything standing
     * where a spike appears is hit once - the line remembers who it caught, so a mob frozen in
     * place is not hit again by the same line. */
    /** Every block an 얼음송곳 cast would cover: each arm walking outward exactly as
     * growSpikeLine does. Shared with the marker so the two cannot drift. */
    private List<Block> spikeFootprint(Location origin) {
        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < ICE_SPIKE_DIRECTIONS; i++) {
            double angle = Math.PI * 2 * i / ICE_SPIKE_DIRECTIONS;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            int lastY = origin.getBlockY();
            for (int step = 1; step <= ICE_SPIKE_RANGE; step++) {
                int centreX = origin.getBlockX() + (int) Math.round(dx * step);
                int centreZ = origin.getBlockZ() + (int) Math.round(dz * step);
                boolean placedAny = false;
                for (int off = -ICE_SPIKE_HALF_WIDTH; off <= ICE_SPIKE_HALF_WIDTH; off++) {
                    int x = centreX + (int) Math.round(-dz * off);
                    int z = centreZ + (int) Math.round(dx * off);
                    Block surface = frost.surfaceAt(origin, x, z, lastY);
                    if (surface == null) {
                        continue;
                    }
                    if (off == 0) {
                        lastY = surface.getY();
                    }
                    placedAny = true;
                    blocks.add(surface);
                }
                if (!placedAny) {
                    break; // the arm ran off a cliff; so will the cast
                }
            }
        }
        return blocks;
    }

    private boolean iceSpike(Player player) {
        Location origin = player.getLocation().clone();
        preview.hide(player.getUniqueId());
        player.getWorld().playSound(origin, Sound.BLOCK_GLASS_BREAK, 1.4f, 0.5f);
        for (int i = 0; i < ICE_SPIKE_DIRECTIONS; i++) {
            double angle = Math.PI * 2 * i / ICE_SPIKE_DIRECTIONS;
            growSpikeLine(player, origin, Math.cos(angle), Math.sin(angle));
        }
        return true;
    }

    private void growSpikeLine(Player caster, Location origin, double dx, double dz) {
        Set<UUID> alreadyHit = new HashSet<>();
        new BukkitRunnable() {
            int step = 1;
            int lastY = origin.getBlockY();

            @Override
            public void run() {
                if (step > ICE_SPIKE_RANGE || !caster.isOnline()) {
                    cancel();
                    return;
                }
                int centreX = origin.getBlockX() + (int) Math.round(dx * step);
                int centreZ = origin.getBlockZ() + (int) Math.round(dz * step);
                step++;
                // Perpendicular to the arm, so the widening is across it rather than along it.
                double perpX = -dz;
                double perpZ = dx;
                boolean placedAny = false;
                for (int off = -ICE_SPIKE_HALF_WIDTH; off <= ICE_SPIKE_HALF_WIDTH; off++) {
                    int x = centreX + (int) Math.round(perpX * off);
                    int z = centreZ + (int) Math.round(perpZ * off);
                    Block surface = frost.surfaceAt(origin, x, z, lastY);
                    if (surface == null) {
                        continue; // a cliff or an overhang - skip this block, keep crawling
                    }
                    if (off == 0) {
                        lastY = surface.getY(); // the centre line is what the arm follows
                    }
                    placedAny = true;
                    frost.placeTemporary(surface, Material.PACKED_ICE, ICE_SPIKE_LINGER_TICKS);
                    surface.getWorld().spawnParticle(Particle.SNOWFLAKE,
                            surface.getLocation().add(0.5, 1, 0.5), 6, 0.2, 0.4, 0.2, 0.01);

                    for (Entity nearby : surface.getWorld().getNearbyEntities(
                            surface.getLocation().add(0.5, 1, 0.5), 0.8, 1.2, 0.8)) {
                        if (!(nearby instanceof LivingEntity victim) || victim.equals(caster)
                                || !alreadyHit.add(victim.getUniqueId())) {
                            continue;
                        }
                        hurt(caster, victim, ICE_SPIKE_DAMAGE);
                        frost.applyFrostbite(victim, FROSTBITE_SECONDS);
                    }
                }
                if (!placedAny) {
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, ICE_SPIKE_STEP_TICKS);
    }

    // ---------- 파이로맨서 : 불에 데지 않는 몸 ----------

    /** The passive. Anything hot simply does not apply - fire, lava, and the magma block the
     * eruption stands on. */
    @EventHandler(ignoreCancelled = true)
    public void onBurn(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !isPyromancer(player)) {
            return;
        }
        switch (event.getCause()) {
            case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> {
                event.setCancelled(true);
                player.setFireTicks(0);
            }
            default -> { }
        }
    }

    // ---------- 파이로맨서 2 : 용암분출 ----------

    /** The ground the caster is looking at, or null when they are aiming at the sky. */
    private Block eruptionTarget(Player player) {
        RayTraceResult hit = player.rayTraceBlocks(ERUPTION_RANGE, FluidCollisionMode.NEVER);
        return hit == null ? null : hit.getHitBlock();
    }

    /** The 7x7 circle centred on a block, following the surface so a slope is covered too. */
    private List<Block> eruptionArea(Block centre) {
        List<Block> area = new ArrayList<>();
        for (int dx = -ERUPTION_RADIUS; dx <= ERUPTION_RADIUS; dx++) {
            for (int dz = -ERUPTION_RADIUS; dz <= ERUPTION_RADIUS; dz++) {
                if (dx * dx + dz * dz > (ERUPTION_RADIUS + 0.5) * (ERUPTION_RADIUS + 0.5)) {
                    continue; // round the square off
                }
                Block above = frost.surfaceAt(centre.getLocation(), centre.getX() + dx, centre.getZ() + dz,
                        centre.getY() + 1);
                if (above != null) {
                    area.add(above.getRelative(0, -1, 0)); // the solid block, not the air over it
                }
            }
        }
        return area;
    }

    /** Called every tick: paints the shape whichever held skill would carve, and takes it away
     * the instant it should not be there. */
    private void tickPreview() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ClassSkill held = previewableSkill(player);
            if (held == null) {
                preview.hide(player.getUniqueId());
                continue;
            }
            Block anchor;
            List<Block> area;
            if (held.id().equals("lava_eruption")) {
                anchor = eruptionTarget(player);
                area = anchor == null ? List.of() : eruptionArea(anchor);
            } else {
                // 얼음송곳 erupts from where the caster stands, so the shape only moves when
                // they do - aiming around costs nothing.
                anchor = player.getLocation().getBlock();
                area = spikeFootprint(player.getLocation());
            }
            if (anchor == null || area.isEmpty()) {
                preview.hide(player.getUniqueId());
                continue;
            }
            preview.show(player, anchor, area,
                    held.id().equals("lava_eruption") ? ERUPTION_MARKER : ICE_SPIKE_MARKER);
        }
    }

    /** The held skill when it is one that draws a marker and is ready to cast, else null:
     * not holding one, no longer owning it, or still recharging all mean no marker. */
    private ClassSkill previewableSkill(Player player) {
        String heldId = skillItems.skillIdOf(player.getInventory().getItemInMainHand());
        if (!"lava_eruption".equals(heldId) && !"ice_spike".equals(heldId)) {
            return null;
        }
        ClassSkill skill = classes.skillById(player.getUniqueId(), heldId);
        return skill != null && cooldowns.ready(player, skill) ? skill : null;
    }

    private boolean lavaEruption(Player player) {
        Block target = eruptionTarget(player);
        if (target == null) {
            player.sendActionBar(Component.text("땅을 바라보고 시전하세요.", NamedTextColor.RED));
            return false;
        }
        List<Block> area = eruptionArea(target);
        if (area.isEmpty()) {
            player.sendActionBar(Component.text("땅을 바라보고 시전하세요.", NamedTextColor.RED));
            return false;
        }
        preview.hide(player.getUniqueId());
        // Centre outwards, so the eruption reads as spreading from where it was aimed.
        Location centre = target.getLocation();
        area.sort(Comparator.comparingDouble(block -> block.getLocation().distanceSquared(centre)));
        area.forEach(block -> tempBlocks.place(block, Material.MAGMA_BLOCK, ERUPTION_MAGMA_TICKS));
        player.getWorld().playSound(centre, Sound.BLOCK_LAVA_POP, 1.4f, 0.6f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> erupt(area, centre), ERUPTION_DELAY_TICKS);
        return true;
    }

    /** One task drives every column: a column starts later the further out it sits, and they
     * all climb at the same rate. Running a task per column would be dozens of timers doing the
     * same arithmetic. */
    private void erupt(List<Block> area, Location centre) {
        centre.getWorld().playSound(centre, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.5f);
        // Head start by distance, not by position in the list: every column on the same ring
        // goes up together.
        int[] startStep = new int[area.size()];
        for (int i = 0; i < area.size(); i++) {
            double distance = Math.sqrt(area.get(i).getLocation().distanceSquared(centre));
            startStep[i] = (int) Math.round(distance * ERUPTION_SPREAD_STEPS_PER_BLOCK);
        }
        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                boolean anyLeft = false;
                for (int i = 0; i < area.size(); i++) {
                    int height = step - startStep[i];
                    if (height < 0) {
                        anyLeft = true;
                        continue;
                    }
                    if (height >= ERUPTION_HEIGHT) {
                        continue;
                    }
                    anyLeft = true;
                    Block base = area.get(i);
                    Block lava = base.getRelative(0, height + 1, 0);
                    if (lava.getType().isAir()) {
                        tempBlocks.place(lava, Material.LAVA, ERUPTION_LAVA_TICKS);
                    }
                }
                step++;
                if (!anyLeft) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, ERUPTION_STEP_TICKS);
    }

    // ---------- 소서러 1 : 볼트마법 ----------

    /** A small fireball stripped of everything that makes it a fireball: no block damage, no
     * fires started. It is here for the flame trail - the bolt's damage is applied by hand. */
    private boolean bolt(Player player) {
        if (isElectromancer(player)) {
            return boltBeam(player);
        }
        launchBolt(player, "bolt");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.6f);
        return true;
    }

    /** @param skillId written onto the projectile, so the hit handler knows which skill to run. */
    private void launchBolt(Player player, String skillId) {
        Vector direction = player.getEyeLocation().getDirection().normalize().multiply(BOLT_SPEED);
        player.launchProjectile(SmallFireball.class, direction, bolt -> {
            bolt.setIsIncendiary(false);
            bolt.setYield(0f);
            bolt.getPersistentDataContainer().set(boltKey, PersistentDataType.STRING, skillId);
        });
    }

    // ---------- 일렉트로맨서 1 : 볼트마법 (히트스캔) ----------

    /** No projectile at all: the line is traced once, stopped at the first wall, and everything
     * standing along it is hit at once. Piercing is why the entities are gathered by hand
     * rather than taking rayTrace's first result. */
    private boolean boltBeam(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult wall = player.getWorld().rayTraceBlocks(eye, direction, BOLT_BEAM_RANGE,
                FluidCollisionMode.NEVER, true);
        double range = wall == null ? BOLT_BEAM_RANGE : eye.toVector().distance(wall.getHitPosition());

        for (double along = 0.5; along < range; along += 0.4) {
            Location point = eye.clone().add(direction.clone().multiply(along));
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.02, 0.02, 0.02, 0.0);
        }
        player.getWorld().playSound(eye, Sound.ENTITY_BREEZE_SHOOT, 1f, 2f);

        for (Entity nearby : player.getWorld().getNearbyEntities(eye, range, range, range)) {
            if (!(nearby instanceof LivingEntity victim) || victim.equals(player)) {
                continue;
            }
            if (victim.getBoundingBox().expand(BOLT_BEAM_FORGIVENESS).rayTrace(
                    eye.toVector(), direction, range) == null) {
                continue;
            }
            hurt(player, victim, BOLT_BEAM_DAMAGE);
            root(victim);
            addShock(victim);
        }
        return true;
    }

    /** Pinned for a fraction of a second. Slowness alone does not stop a player, so the
     * velocity is held at zero for the duration as well. */
    private void root(LivingEntity victim) {
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ROOT_TICKS, 10));
        new BukkitRunnable() {
            int left = ROOT_TICKS;

            @Override
            public void run() {
                if (left-- <= 0 || !victim.isValid()) {
                    cancel();
                    return;
                }
                victim.setVelocity(new Vector(0, victim.isOnGround() ? 0 : -0.08, 0));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ---------- 감전 ----------

    private void addShock(LivingEntity victim) {
        Shock current = shocks.get(victim.getUniqueId());
        int stacks = current == null || current.expiresAtMillis() <= System.currentTimeMillis()
                ? 1 : Math.min(SHOCK_MAX_STACKS, current.stacks() + 1);
        shocks.put(victim.getUniqueId(), new Shock(stacks, System.currentTimeMillis() + SHOCK_SECONDS * 1000L));
        // Outlined for everyone, so a charged target is worth chasing. The effect times itself
        // out alongside the stack, which saves having to switch the glow back off.
        victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, SHOCK_SECONDS * 20, 0, false, false));
    }

    private int shockStacks(Entity entity) {
        Shock shock = shocks.get(entity.getUniqueId());
        return shock == null || shock.expiresAtMillis() <= System.currentTimeMillis() ? 0 : shock.stacks();
    }

    /** Sparks over anything still carrying a charge, and drops the lapsed marks. The glow
     * expires on its own timer, so nothing has to be undone here. */
    private void tickShocks() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Shock>> it = shocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Shock> entry = it.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.isValid() || entry.getValue().expiresAtMillis() <= now) {
                it.remove();
                continue;
            }
            entity.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, entity.getLocation().add(0, 1, 0),
                    entry.getValue().stacks(), 0.3, 0.5, 0.3, 0.02);
        }
    }

    // ---------- 일렉트로맨서 2 : 피뢰침 ----------

    private boolean lightningRod(Player player) {
        launchBolt(player, "lightning_rod");
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1.4f);
        return true;
    }

    /** One bolt per 감전 stack, a few ticks apart so they read as a volley rather than a single
     * flash. Real lightning: vanilla deals 5, comfortably under the 8 that would have been
     * worth faking - it does set the target alight and can charge a creeper, which is part of
     * the deal. */
    private void callLightning(Player caster, LivingEntity target, int strikes) {
        new BukkitRunnable() {
            int left = strikes;

            @Override
            public void run() {
                if (left-- <= 0 || !target.isValid() || !caster.isOnline()) {
                    cancel();
                    return;
                }
                target.getWorld().strikeLightning(target.getLocation());
            }
        }.runTaskTimer(plugin, ROD_DELAY_TICKS, ROD_REPEAT_TICKS);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBoltHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        String firedBy = projectile.getPersistentDataContainer().get(boltKey, PersistentDataType.STRING);
        if (firedBy == null) {
            return;
        }
        // Cancelled either way: a fireball that touches a block would still scorch it.
        event.setCancelled(true);
        projectile.remove();
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        if (firedBy.equals("track")) {
            Location at = event.getHitBlock() != null
                    ? event.getHitBlock().getLocation().add(0.5, 1, 0.5)
                    : projectile.getLocation();
            releaseWildWolves(shooter, at);
            return;
        }
        Entity hit = event.getHitEntity();
        boolean rod = firedBy.equals("lightning_rod");
        if (hit instanceof LivingEntity target && !target.equals(shooter)) {
            hurt(shooter, target, rod ? ROD_BOLT_DAMAGE : BOLT_DAMAGE);
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0),
                    20, 0.3, 0.4, 0.3, 0.1);
            if (rod) {
                addShock(target);
                callLightning(shooter, target, shockStacks(target));
            }
        }
        if (isPyromancer(shooter)) {
            // Fires are set, blocks are not broken: the arena should scorch, not crater.
            projectile.getWorld().createExplosion(projectile.getLocation(), BOLT_BLAST_POWER, true, false, shooter);
            return;
        }
        projectile.getWorld().playSound(projectile.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 2f);
    }

    // ---------- 서머너 1 : 돼지 소환 ----------

    /** Ten pigs thrown outwards from where the caster stands, evenly around the circle with a
     * little jitter so it reads as a burst rather than a formation. */
    private boolean pigBurst(Player player) {
        if (isDruid(player)) {
            return wolfPack(player);
        }
        boolean hunters = isNecromancer(player);
        Location centre = player.getLocation();
        for (int i = 0; i < PIG_COUNT; i++) {
            double angle = Math.PI * 2 * i / PIG_COUNT + random.nextDouble() * 0.3;
            double speed = PIG_SCATTER_SPEED * (0.8 + random.nextDouble() * 0.4);
            Pig pig = centre.getWorld().spawn(centre, Pig.class);
            pig.getPersistentDataContainer().set(summonedPigKey, PersistentDataType.BOOLEAN, true);
            pig.setVelocity(new Vector(Math.cos(angle) * speed, PIG_SCATTER_LIFT, Math.sin(angle) * speed));

            if (hunters) {
                AttributeInstance speedAttribute = pig.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speedAttribute != null) {
                    speedAttribute.setBaseValue(PIG_CHASE_SPEED);
                }
                // A pig has no attack AI of its own, so it is walked at its quarry instead.
                summons.add(pig, player, Summons.Kind.PIG, Summons.Chase.PATHFIND,
                        Summons.Prey.PLAYERS_AND_MOBS, PIG_HUNTER_FUSE_TICKS,
                        mob -> detonatePig((Pig) mob, player));
            } else {
                summons.add(pig, player, Summons.Kind.PIG, Summons.Chase.NONE,
                        Summons.Prey.PLAYERS_AND_MOBS, PIG_FUSE_TICKS,
                        mob -> detonatePig((Pig) mob, player));
            }
        }
        centre.getWorld().spawnParticle(Particle.EXPLOSION, centre.clone().add(0, 1, 0), 3, 0.3, 0.3, 0.3);
        centre.getWorld().playSound(centre, Sound.ENTITY_PIG_AMBIENT, 1.4f, 0.8f);
        centre.getWorld().playSound(centre, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.6f);
        return true;
    }

    // ---------- 드루이드 1 : 늑대 소환 ----------

    /** Five tame wolves. They fight for the caster on their own - a tamed wolf already answers
     * what its owner hits - so nothing steers them; after twenty seconds all but two wander off. */
    private boolean wolfPack(Player player) {
        Location centre = player.getLocation();
        List<Wolf> pack = new ArrayList<>();
        for (int i = 0; i < WOLF_PACK_SIZE; i++) {
            Wolf wolf = centre.getWorld().spawn(centre, Wolf.class);
            wolf.setTamed(true);
            wolf.setOwner(player);
            // Thrown outwards like the pigs, so a summon reads as a burst rather than a huddle.
            double angle = Math.PI * 2 * i / WOLF_PACK_SIZE + random.nextDouble() * 0.3;
            double speed = PIG_SCATTER_SPEED * (0.8 + random.nextDouble() * 0.4);
            wolf.setVelocity(new Vector(Math.cos(angle) * speed, PIG_SCATTER_LIFT, Math.sin(angle) * speed));
            summons.add(wolf, player, Summons.Kind.WOLF_PET, Summons.Chase.NONE,
                    Summons.Prey.PLAYERS_AND_MOBS, Integer.MAX_VALUE / 100, mob -> { });
            pack.add(wolf);
        }
        centre.getWorld().spawnParticle(Particle.POOF, centre.clone().add(0, 1, 0), 20, 0.4, 0.3, 0.4, 0.02);
        centre.getWorld().playSound(centre, Sound.ENTITY_WOLF_ANGRY_AMBIENT, 1.2f, 1f);

        // Keeping two is group business, not something each wolf can decide for itself.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<Wolf> alive = pack.stream().filter(Wolf::isValid).toList();
            for (int i = WOLF_PACK_SURVIVORS; i < alive.size(); i++) {
                Wolf leaving = alive.get(i);
                leaving.getWorld().spawnParticle(Particle.POOF, leaving.getLocation().add(0, 0.5, 0),
                        12, 0.3, 0.3, 0.3, 0.02);
                summons.forget(leaving);
                leaving.remove();
            }
        }, WOLF_PACK_TICKS);
        return true;
    }

    // ---------- 네크로맨서 2 : 시체폭발 ----------

    /** Sets off every pig at once and leaves a piglin standing in each crater. */
    private boolean corpseExplosion(Player player) {
        List<Mob> pigs = summons.of(player, Summons.Kind.PIG);
        if (pigs.isEmpty()) {
            player.sendActionBar(Component.text("터뜨릴 소환수가 없습니다.", NamedTextColor.RED));
            return false;
        }
        for (Mob pig : pigs) {
            Location at = pig.getLocation().clone();
            summons.forget(pig);
            detonatePig((Pig) pig, player);
            raisePiglin(player, at);
        }
        return true;
    }

    private void raisePiglin(Player caster, Location at) {
        PigZombie piglin = at.getWorld().spawn(at, PigZombie.class);
        piglin.getEquipment().clear(); // raised bare-handed, not armed from the piglin loot table
        piglin.setAngry(true);
        piglin.setAdult();
        summons.add(piglin, caster, Summons.Kind.PIGLIN, Summons.Chase.ATTACK,
                Summons.Prey.PLAYERS_AND_MOBS, PIGLIN_FUSE_TICKS, mob -> detonatePiglin(mob, caster));
        at.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 0.5, 0), 20, 0.3, 0.5, 0.3, 0.02);
    }

    private void detonatePiglin(Mob piglin, Player caster) {
        if (!piglin.isValid()) {
            return;
        }
        Location at = piglin.getLocation();
        piglin.remove();
        at.getWorld().spawnParticle(Particle.EXPLOSION, at.clone().add(0, 0.5, 0), 3, 0.3, 0.3, 0.3);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);
        for (Entity nearby : at.getWorld().getNearbyEntities(at, PIG_BLAST_RADIUS, PIG_BLAST_RADIUS, PIG_BLAST_RADIUS)) {
            if (nearby instanceof LivingEntity victim && !victim.equals(caster) && !summons.isSummon(nearby)) {
                hurt(caster, victim, PIGLIN_BLAST_DAMAGE);
            }
        }
    }

    /** Every death near a necromancer buys back a little of 시체폭발. */
    @EventHandler(ignoreCancelled = true)
    public void onNearbyDeath(EntityDeathEvent event) {
        for (Player player : event.getEntity().getWorld().getPlayers()) {
            if (!isNecromancer(player)
                    || player.getLocation().distance(event.getEntity().getLocation()) > CORPSE_REFUND_RADIUS) {
                continue;
            }
            ClassSkill skill = classes.skillById(player.getUniqueId(), "corpse_explosion");
            if (skill != null) {
                cooldowns.reduce(player, skill, CORPSE_REFUND_MILLIS);
            }
        }
    }

    // ---------- 드루이드 2 : 흔적 추적 ----------

    private boolean track(Player player) {
        tracked.put(player.getUniqueId(), new HashSet<>());
        launchBolt(player, "track");
        summons.boostSpeed(player, TRACK_HASTE_TICKS, 0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WOLF_ANGRY_AMBIENT, 1.2f, 1.4f);
        return true;
    }

    /** A wild wolf per rival, loosed where the shot landed. They are not the caster's pets -
     * they hunt players and report where they found them. */
    private void releaseWildWolves(Player caster, Location at) {
        int rivals = (int) at.getWorld().getPlayers().stream().filter(p -> !p.equals(caster)).count();
        for (int i = 0; i < Math.max(1, rivals); i++) {
            Wolf wolf = at.getWorld().spawn(at, Wolf.class);
            wolf.setAngry(true);
            summons.add(wolf, caster, Summons.Kind.WOLF_WILD, Summons.Chase.ATTACK,
                    Summons.Prey.PLAYERS_ONLY, WILD_WOLF_TICKS, Mob::remove);
        }
        at.getWorld().playSound(at, Sound.ENTITY_WOLF_ANGRY_GROWL, 1.4f, 0.8f);
    }

    /** The point of the skill: a wolf that lands a bite, or takes one, gives its quarry away. */
    @EventHandler(ignoreCancelled = true)
    public void onWildWolfContact(EntityDamageByEntityEvent event) {
        Entity wolf = event.getDamager();
        Entity other = event.getEntity();
        if (!(wolf instanceof Wolf) || !(other instanceof Player)) {
            Entity swap = wolf;
            wolf = other;
            other = swap;
        }
        if (!(wolf instanceof Wolf) || !(other instanceof Player quarry) || !summons.isSummon(wolf)) {
            return;
        }
        for (Map.Entry<UUID, Set<UUID>> entry : tracked.entrySet()) {
            Player caster = Bukkit.getPlayer(entry.getKey());
            if (caster == null || !entry.getValue().add(quarry.getUniqueId())) {
                continue; // already given away this one
            }
            Location at = quarry.getLocation();
            caster.sendMessage(Component.text("[추적] ", NamedTextColor.GREEN)
                    .append(Component.text(quarry.getName() + " — "
                            + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ(),
                            NamedTextColor.WHITE)));
        }
    }

    // ---------- not written yet ----------

    /** Done by hand rather than with createExplosion: that would knock holes in the arena and
     * would not know to spare the summoner. Other summoned pigs are spared too, or the first
     * blast would set the rest off at once instead of letting each run its own fuse. */
    private void detonatePig(Pig pig, Player caster) {
        if (!pig.isValid()) {
            return;
        }
        Location at = pig.getLocation();
        pig.remove();
        at.getWorld().spawnParticle(Particle.EXPLOSION, at.clone().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2);
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);

        for (Entity nearby : at.getWorld().getNearbyEntities(at, PIG_BLAST_RADIUS, PIG_BLAST_RADIUS, PIG_BLAST_RADIUS)) {
            if (!(nearby instanceof LivingEntity victim) || victim.equals(caster) || isSummonedPig(nearby)) {
                continue;
            }
            hurt(caster, victim, PIG_BLAST_DAMAGE);
        }
    }

    private boolean isSummonedPig(Entity entity) {
        return Boolean.TRUE.equals(entity.getPersistentDataContainer()
                .get(summonedPigKey, PersistentDataType.BOOLEAN));
    }

    /** Summoned pigs are ammunition, not livestock - killing one early yields nothing. */
    @EventHandler(ignoreCancelled = true)
    public void onSummonedPigDeath(EntityDeathEvent event) {
        if (isSummonedPig(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    // ---------- not written yet ----------

    @SuppressWarnings("unused") // skill is part of the dispatch signature
    private boolean placeholder(Player player, ClassSkill skill) {
        var from = player.getEyeLocation();
        player.getWorld().spawnParticle(Particle.ENCHANT, from.clone().add(from.getDirection().multiply(1.5)),
                40, 0.4, 0.4, 0.4, 0.5);
        player.getWorld().playSound(from, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);
        return true;
    }

    public void clear() {
        blinks.clear();
        tracked.clear();
        shocks.keySet().forEach(id -> {
            if (Bukkit.getEntity(id) instanceof LivingEntity alive) {
                alive.removePotionEffect(PotionEffectType.GLOWING);
            }
        });
        shocks.clear();
        leaps.clear();
        noFall.clear();
        preview.clear();
    }
}
