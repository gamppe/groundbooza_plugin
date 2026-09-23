package com.example.magicwar;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Everything a summoner has out: what it is, whose it is, how long it lives and who it hunts.
 *
 * <p>Two ways of chasing, because the mobs differ. A piglin or a wolf has attack AI, so it is
 * enough to hand it a target; a pig has none, so it has to be pathed at its quarry by hand
 * every so often. Retargeting is on a slow tick - a summon that picks a new victim four times
 * a second looks twitchy and costs a scan each time.
 */
public class Summons {

    public enum Kind { PIG, PIGLIN, WOLF_PET, WOLF_WILD }

    /** How a summon pursues: not at all, by walking, or by attacking. */
    public enum Chase { NONE, PATHFIND, ATTACK }

    /** Who counts as prey. Players come first either way. */
    public enum Prey { PLAYERS_AND_MOBS, PLAYERS_ONLY }

    private static final double HUNT_RADIUS = 40;
    private static final int RETARGET_INTERVAL_TICKS = 10;

    private static final class Summon {
        final Mob mob;
        final UUID casterId;
        final Kind kind;
        final Chase chase;
        final Prey prey;
        final long expiresAtMillis;
        final Consumer<Mob> onExpire;

        Summon(Mob mob, UUID casterId, Kind kind, Chase chase, Prey prey,
               long expiresAtMillis, Consumer<Mob> onExpire) {
            this.mob = mob;
            this.casterId = casterId;
            this.kind = kind;
            this.chase = chase;
            this.prey = prey;
            this.expiresAtMillis = expiresAtMillis;
            this.onExpire = onExpire;
        }
    }

    private final List<Summon> summons = new ArrayList<>();
    private final Set<UUID> summonIds = new HashSet<>();
    private int tickCounter;

    public void add(Mob mob, Player caster, Kind kind, Chase chase, Prey prey,
                    int lifetimeTicks, Consumer<Mob> onExpire) {
        mob.setPersistent(false);
        summons.add(new Summon(mob, caster.getUniqueId(), kind, chase, prey,
                System.currentTimeMillis() + lifetimeTicks * 50L, onExpire));
        summonIds.add(mob.getUniqueId());
    }

    public boolean isSummon(Entity entity) {
        return summonIds.contains(entity.getUniqueId());
    }

    /** This caster's live summons of one kind. */
    public List<Mob> of(Player caster, Kind kind) {
        List<Mob> mine = new ArrayList<>();
        for (Summon summon : summons) {
            if (summon.casterId.equals(caster.getUniqueId()) && summon.kind == kind && summon.mob.isValid()) {
                mine.add(summon.mob);
            }
        }
        return mine;
    }

    public List<Mob> allOf(Player caster) {
        List<Mob> mine = new ArrayList<>();
        for (Summon summon : summons) {
            if (summon.casterId.equals(caster.getUniqueId()) && summon.mob.isValid()) {
                mine.add(summon.mob);
            }
        }
        return mine;
    }

    /** Takes a summon off the books without running its expiry - for one being detonated early. */
    public void forget(Mob mob) {
        summons.removeIf(summon -> summon.mob.equals(mob));
        summonIds.remove(mob.getUniqueId());
    }

    /**
     * Points everything this caster has out at whatever they just hurt.
     *
     * <p>Vanilla only does this for melee: a tame wolf's "help the owner" goal reads the last
     * mob its owner <em>struck</em>, which a spell never sets. So a summoner whose magic is all
     * at range would otherwise watch their pack stand around.
     */
    public void rally(Player caster, LivingEntity victim) {
        if (victim.equals(caster) || isSummon(victim)) {
            return;
        }
        for (Summon summon : summons) {
            if (summon.casterId.equals(caster.getUniqueId()) && summon.mob.isValid()) {
                summon.mob.setTarget(victim);
            }
        }
    }

    public void boostSpeed(Player caster, int ticks, int amplifier) {
        allOf(caster).forEach(mob ->
                mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, amplifier)));
    }

    /** Called every tick from the plugin. */
    public void tick() {
        boolean retarget = ++tickCounter % RETARGET_INTERVAL_TICKS == 0;
        long now = System.currentTimeMillis();
        Iterator<Summon> it = summons.iterator();
        List<Runnable> expired = new ArrayList<>();
        while (it.hasNext()) {
            Summon summon = it.next();
            if (!summon.mob.isValid()) {
                it.remove();
                summonIds.remove(summon.mob.getUniqueId());
                continue;
            }
            if (summon.expiresAtMillis <= now) {
                it.remove();
                summonIds.remove(summon.mob.getUniqueId());
                // Deferred: an expiry may spawn more summons, and this list is being walked.
                expired.add(() -> summon.onExpire.accept(summon.mob));
                continue;
            }
            if (retarget && summon.chase != Chase.NONE) {
                pursue(summon);
            }
        }
        expired.forEach(Runnable::run);
    }

    private void pursue(Summon summon) {
        LivingEntity target = nearestPrey(summon.mob.getLocation(), summon.casterId, summon.prey);
        if (target == null) {
            return;
        }
        if (summon.chase == Chase.ATTACK) {
            summon.mob.setTarget(target);
        } else {
            summon.mob.getPathfinder().moveTo(target, 1.0);
        }
    }

    /** Nearest living thing worth chasing. A player anywhere in range beats any mob, however
     * close the mob is - the summons are meant to go for people. The caster and everybody's
     * summons are skipped, or a pack would spend the fight eating itself. */
    public LivingEntity nearestPrey(Location from, UUID casterId, Prey prey) {
        LivingEntity closestPlayer = null;
        LivingEntity closestMob = null;
        double playerDistance = Double.MAX_VALUE;
        double mobDistance = Double.MAX_VALUE;

        for (Entity entity : from.getWorld().getNearbyEntities(from, HUNT_RADIUS, HUNT_RADIUS, HUNT_RADIUS)) {
            if (!(entity instanceof LivingEntity living) || entity.getUniqueId().equals(casterId)
                    || isSummon(entity) || living.isDead()) {
                continue;
            }
            double distance = living.getLocation().distanceSquared(from);
            if (living instanceof Player player) {
                if (player.isInvulnerable() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR
                        || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                    continue;
                }
                if (distance < playerDistance) {
                    playerDistance = distance;
                    closestPlayer = living;
                }
            } else if (distance < mobDistance) {
                mobDistance = distance;
                closestMob = living;
            }
        }
        if (closestPlayer != null) {
            return closestPlayer;
        }
        return prey == Prey.PLAYERS_ONLY ? null : closestMob;
    }

    /** Wipes the board without running any expiry - a round ending should not set off a field
     * of pigs. */
    public void clear() {
        summons.forEach(summon -> {
            if (summon.mob.isValid()) {
                summon.mob.remove();
            }
        });
        summons.clear();
        summonIds.clear();
    }
}
