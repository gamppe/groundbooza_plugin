package com.example.magicwar;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gating for casts, and the greyed-out sweep that shows it.
 *
 * <p>The real state lives here rather than in the vanilla cooldown, which is driven purely as a
 * display. That is what lets a two-step skill {@link #suspend} the sweep while its follow-up
 * window is open - the item reads as usable, because it is - and {@link #resume} it afterwards
 * with whatever time was left.
 *
 * <p>Charges work the same way: vanilla has no notion of them, so the count is kept here and
 * refilled one per cooldown. For those the sweep is a refill timer rather than a lockout - it
 * runs whenever anything is still coming back, even with charges in hand.
 */
public class SkillCooldowns {

    private static final class State {
        int charges;
        /** When the next charge lands; 0 when the skill is full. */
        long nextRefillMillis;
        /** True while a follow-up cast is pending. */
        boolean suspended;
        /** What to draw while suspended: the follow-up window in ticks, or 0 for nothing. */
        int suspendedDisplayTicks;
        /** Kept so the ticker can refill and announce without being told which skill it is.
         * Re-set on every lookup, since an advancement can re-time a skill under the same id. */
        ClassSkill skill;

        State(int charges) {
            this.charges = charges;
        }
    }

    private final Map<UUID, Map<String, State>> states = new ConcurrentHashMap<>();

    private State of(Player player, ClassSkill skill) {
        State state = states.computeIfAbsent(player.getUniqueId(), u -> new HashMap<>())
                .computeIfAbsent(skill.id(), id -> new State(skill.charges()));
        state.skill = skill;
        return state;
    }

    /** Hands back any charges that have come due since the last look. */
    private void refill(State state, ClassSkill skill) {
        if (state.charges >= skill.charges()) {
            state.nextRefillMillis = 0;
            return;
        }
        long now = System.currentTimeMillis();
        long step = skill.cooldownSeconds() * 1000L;
        while (state.charges < skill.charges() && state.nextRefillMillis != 0 && now >= state.nextRefillMillis) {
            state.charges++;
            state.nextRefillMillis = state.charges >= skill.charges() ? 0 : state.nextRefillMillis + step;
        }
    }

    public boolean ready(Player player, ClassSkill skill) {
        State state = of(player, skill);
        refill(state, skill);
        return state.charges > 0;
    }

    /** Seconds until the skill can be cast again - for the "쿨타임 N초" line. */
    public int secondsLeft(Player player, ClassSkill skill) {
        State state = of(player, skill);
        refill(state, skill);
        if (state.nextRefillMillis == 0) {
            return 0;
        }
        return (int) Math.max(1, (state.nextRefillMillis - System.currentTimeMillis() + 999) / 1000);
    }

    /** Spends one cast. @return charges left, or -1 for a skill that does not use them. */
    public int consume(Player player, ClassSkill skill) {
        State state = of(player, skill);
        refill(state, skill);
        state.charges--;
        if (state.nextRefillMillis == 0) {
            state.nextRefillMillis = System.currentTimeMillis() + skill.cooldownSeconds() * 1000L;
        }
        draw(player, skill, state);
        return skill.charges() <= 1 ? -1 : state.charges;
    }

    /**
     * Takes the real cooldown off the icon while a follow-up is available.
     *
     * @param displayTicks when the follow-up window is itself timed, the sweep shows that
     *                     instead - so 수도사's three seconds to blink read off the icon. Pass
     *                     0 for a window with no clock (뇌격 lasts until you land), which
     *                     leaves the icon clear.
     */
    public void suspend(Player player, ClassSkill skill, int displayTicks) {
        State state = of(player, skill);
        state.suspended = true;
        state.suspendedDisplayTicks = displayTicks;
        draw(player, skill, state);
    }

    /** Puts the sweep back with whatever cooldown is actually left. */
    public void resume(Player player, ClassSkill skill) {
        State state = of(player, skill);
        state.suspended = false;
        state.suspendedDisplayTicks = 0;
        refill(state, skill);
        draw(player, skill, state);
    }

    /** The vanilla cooldown is only ever a picture of the state above. */
    private void draw(Player player, ClassSkill skill, State state) {
        Key key = SkillItem.cooldownKey(skill);
        if (state.suspended) {
            player.setCooldown(key, state.suspendedDisplayTicks);
            return;
        }
        if (state.nextRefillMillis == 0) {
            player.setCooldown(key, 0); // fully charged
            return;
        }
        // Shown even with charges to spare: on a charge skill the sweep is the refill timer,
        // not a lockout, so it keeps running until the last charge is back.
        int ticks = (int) Math.max(1, (state.nextRefillMillis - System.currentTimeMillis()) / 50);
        player.setCooldown(key, ticks);
    }

    /**
     * Called every tick from the plugin. The sweep animates client-side once set, so this only
     * redraws when a charge actually lands - and says so, since a returning charge is the one
     * thing a player cannot see coming any other way.
     */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<String, State> mine = states.get(player.getUniqueId());
            if (mine == null) {
                continue;
            }
            for (State state : mine.values()) {
                if (state.skill == null || state.nextRefillMillis == 0) {
                    continue;
                }
                int before = state.charges;
                refill(state, state.skill);
                if (state.charges == before) {
                    continue;
                }
                draw(player, state.skill, state);
                if (state.skill.charges() > 1) {
                    player.sendActionBar(Component.text(state.skill.name()
                            + " (" + state.charges + "/" + state.skill.charges() + ")", NamedTextColor.AQUA));
                }
            }
        }
    }

    /** Brings the next charge forward. Used by 시체폭발, which pays the necromancer back for
     * every death near them. */
    public void reduce(Player player, ClassSkill skill, long millis) {
        State state = of(player, skill);
        if (state.nextRefillMillis == 0) {
            return; // already full, nothing to bring forward
        }
        state.nextRefillMillis = Math.max(System.currentTimeMillis(), state.nextRefillMillis - millis);
        refill(state, skill);
        draw(player, skill, state);
    }

    public void reset(UUID uuid) {
        states.remove(uuid);
    }

    public void clear() {
        states.clear();
    }
}
