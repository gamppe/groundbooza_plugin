package com.example.magicwar;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityCategory;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Set;
import java.util.UUID;

/**
 * The side of {@link Perks} that only shows up when something happens: extra damage, the two
 * immunities that come with a 전직, and the shield a 드루이드 gets back for a lost summon.
 *
 * <p>Damage is added at {@link EventPriority#HIGH} so it lands on top of whatever armour and
 * enchantments worked out, and it covers melee, arrows and spells alike - a spell damages
 * through {@code damage(amount, caster)}, which arrives here as the caster hitting the victim,
 * exactly as a sword swing would.
 */
public class PerkListener implements Listener {

    private static final Set<EntityDamageEvent.DamageCause> FIRE_CAUSES = Set.of(
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.HOT_FLOOR,
            EntityDamageEvent.DamageCause.CAMPFIRE);

    private final Perks perks;
    private final Summons summons;

    public PerkListener(Perks perks, Summons summons) {
        this.perks = perks;
        this.summons = summons;
    }

    /**
     * Three adjustments, in the order they read: the flat bonus that applies to everything,
     * then either the spell bonus or the melee penalty, never both.
     *
     * <p>A swing is what is left when the damage is neither a spell nor a projectile - the
     * player themselves, with nothing between them and the victim. The penalty comes off after
     * the bonus, so a 소서러 who has earned 주는 피해 still feels it, and never takes a hit
     * below one point.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageDealt(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event);
        if (attacker == null || attacker.equals(event.getEntity())) {
            return;
        }
        UUID uuid = attacker.getUniqueId();
        double damage = event.getDamage() + perks.amount(uuid, Perks.DAMAGE);
        if (isSpell(event)) {
            damage += perks.amount(uuid, Perks.SPELL_DAMAGE);
        } else if (event.getDamager() instanceof Player) {
            double penalty = perks.amount(uuid, Perks.MELEE_PENALTY);
            if (penalty > 0) {
                damage = Math.max(MINIMUM_MELEE_DAMAGE, damage - penalty);
            }
        }
        if (damage != event.getDamage()) {
            event.setDamage(damage);
        }
    }

    /** However weak a 소서러 gets with a sword, a hit still lands. */
    private static final double MINIMUM_MELEE_DAMAGE = 1;

    /** Lightning is a spell too - it is only dealt by vanilla because 피뢰침 asked it to. */
    private boolean isSpell(EntityDamageByEntityEvent event) {
        return perks.isSpellDamage() || event.getDamager() instanceof LightningStrike;
    }

    /**
     * The player behind the blow: the damager itself, whoever loosed the projectile, or whoever
     * called the lightning down. A summon is nobody - the bonus is for what the player does
     * themselves.
     *
     * <p>Natural lightning has no causing player, so a storm stays a storm.
     */
    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player ? player : null;
        }
        if (event.getDamager() instanceof LightningStrike bolt) {
            return bolt.getCausingPlayer();
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFire(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && FIRE_CAUSES.contains(event.getCause())
                && perks.has(player.getUniqueId(), Perks.FIRE_IMMUNE)) {
            event.setCancelled(true);
            player.setFireTicks(0);
        }
    }

    /**
     * 네크로맨서: the ordinary undead lose interest. Only unprovoked picks are taken away - hit
     * a zombie and it still hits back, and one set on the necromancer by another player's magic
     * still comes, since the peace is with the dead rather than with whoever is commanding them.
     *
     * <p>The pick is cleared rather than the event cancelled: a cancel only refuses the change,
     * so a zombie that had already locked on would simply keep coming.
     */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity mob)
                || mob.getCategory() != EntityCategory.UNDEAD
                || PROVOKED.contains(event.getReason())) {
            return;
        }
        if (perks.has(player.getUniqueId(), Perks.UNDEAD_PEACE)) {
            event.setTarget(null);
        }
    }

    /** Reasons the undead are allowed to keep: being hit, and being pointed at someone. */
    private static final Set<EntityTargetEvent.TargetReason> PROVOKED = Set.of(
            EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY,
            EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY,
            EntityTargetEvent.TargetReason.CUSTOM);

    @EventHandler(ignoreCancelled = true)
    public void onSummonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        UUID owner = summons.ownerOf(mob);
        if (owner == null) {
            return;
        }
        Player player = Bukkit.getPlayer(owner);
        double refill = player == null ? 0 : perks.amount(owner, Perks.SHIELD_REFILL);
        if (refill > 0) {
            perks.refillShield(player, refill);
        }
    }
}
