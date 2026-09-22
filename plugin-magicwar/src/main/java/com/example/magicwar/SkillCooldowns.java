package com.example.magicwar;

import net.kyori.adventure.key.Key;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gating for casts, in two flavours.
 *
 * <p>A one-charge skill rides entirely on the vanilla item cooldown group, so nothing is stored
 * here and the hotbar sweep is free. A skill with several charges cannot: vanilla has no notion
 * of charges, so the count is kept here and refilled one per cooldown. The vanilla cooldown is
 * still set when the last charge goes, which is what greys the item out until one comes back.
 */
public class SkillCooldowns {

    private static final class Charges {
        int left;
        long nextRefillMillis;

        Charges(int left) {
            this.left = left;
        }
    }

    private final Map<UUID, Map<String, Charges>> state = new ConcurrentHashMap<>();

    private Charges of(Player player, ClassSkill skill) {
        return state.computeIfAbsent(player.getUniqueId(), u -> new HashMap<>())
                .computeIfAbsent(skill.id(), id -> new Charges(skill.charges()));
    }

    /** Hands back any charges that have come due since the last look. */
    private void refill(Charges charges, ClassSkill skill) {
        long now = System.currentTimeMillis();
        long step = skill.cooldownSeconds() * 1000L;
        if (charges.left >= skill.charges()) {
            charges.nextRefillMillis = 0;
            return;
        }
        while (charges.left < skill.charges() && charges.nextRefillMillis != 0 && now >= charges.nextRefillMillis) {
            charges.left++;
            charges.nextRefillMillis = charges.left >= skill.charges() ? 0 : charges.nextRefillMillis + step;
        }
    }

    public boolean ready(Player player, ClassSkill skill) {
        if (skill.charges() <= 1) {
            return player.getCooldown(SkillItem.cooldownKey(skill)) <= 0;
        }
        Charges charges = of(player, skill);
        refill(charges, skill);
        return charges.left > 0;
    }

    /** Seconds until the skill can be cast again - for the "쿨타임 N초" line. */
    public int secondsLeft(Player player, ClassSkill skill) {
        if (skill.charges() <= 1) {
            return player.getCooldown(SkillItem.cooldownKey(skill)) / 20 + 1;
        }
        Charges charges = of(player, skill);
        refill(charges, skill);
        if (charges.nextRefillMillis == 0) {
            return 0;
        }
        return (int) Math.max(1, (charges.nextRefillMillis - System.currentTimeMillis() + 999) / 1000);
    }

    /** Spends one cast. @return charges left, or -1 for a skill that does not use them. */
    public int consume(Player player, ClassSkill skill) {
        Key key = SkillItem.cooldownKey(skill);
        if (skill.charges() <= 1) {
            player.setCooldown(key, skill.cooldownSeconds() * 20);
            return -1;
        }
        Charges charges = of(player, skill);
        refill(charges, skill);
        charges.left--;
        if (charges.nextRefillMillis == 0) {
            charges.nextRefillMillis = System.currentTimeMillis() + skill.cooldownSeconds() * 1000L;
        }
        if (charges.left <= 0) {
            // Out of charges: grey the item out until the next one lands.
            player.setCooldown(key, (int) Math.max(1,
                    (charges.nextRefillMillis - System.currentTimeMillis()) / 50));
        }
        return charges.left;
    }

    public void clear() {
        state.clear();
    }
}
