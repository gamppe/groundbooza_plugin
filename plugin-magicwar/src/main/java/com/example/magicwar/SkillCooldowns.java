package com.example.magicwar;

import net.kyori.adventure.key.Key;
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
 * refilled one per cooldown, and the sweep only appears once the last one is spent.
 */
public class SkillCooldowns {

    private static final class State {
        int charges;
        /** When the next charge lands; 0 when the skill is full. */
        long nextRefillMillis;
        /** True while a follow-up cast is pending, so the sweep is hidden. */
        boolean suspended;

        State(int charges) {
            this.charges = charges;
        }
    }

    private final Map<UUID, Map<String, State>> states = new ConcurrentHashMap<>();

    private State of(Player player, ClassSkill skill) {
        return states.computeIfAbsent(player.getUniqueId(), u -> new HashMap<>())
                .computeIfAbsent(skill.id(), id -> new State(skill.charges()));
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

    /** Hides the sweep while a follow-up is available, so the item does not look unusable at
     * the exact moment it is most usable. */
    public void suspend(Player player, ClassSkill skill) {
        State state = of(player, skill);
        state.suspended = true;
        draw(player, skill, state);
    }

    /** Puts the sweep back with whatever cooldown is actually left. */
    public void resume(Player player, ClassSkill skill) {
        State state = of(player, skill);
        state.suspended = false;
        refill(state, skill);
        draw(player, skill, state);
    }

    /** The vanilla cooldown is only ever a picture of the state above. */
    private void draw(Player player, ClassSkill skill, State state) {
        Key key = SkillItem.cooldownKey(skill);
        if (state.suspended || state.charges > 0 || state.nextRefillMillis == 0) {
            player.setCooldown(key, 0);
            return;
        }
        int ticks = (int) Math.max(1, (state.nextRefillMillis - System.currentTimeMillis()) / 50);
        player.setCooldown(key, ticks);
    }

    public void reset(UUID uuid) {
        states.remove(uuid);
    }

    public void clear() {
        states.clear();
    }
}
