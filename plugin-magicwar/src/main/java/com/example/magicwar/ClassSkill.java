package com.example.magicwar;

import org.bukkit.Material;

/**
 * One castable skill. The id is what {@link SkillEffects} switches on, so a skill stays a data
 * entry in {@link MagicClass} and its behaviour lives in one place.
 *
 * <p>The icon is always a banner pattern: those have no right-click behaviour of their own, so
 * casting works the same whether you are aiming at a block or at thin air.
 *
 * @param charges how many casts are stored up. 1 is an ordinary cooldown; more than that and
 *                {@link SkillCooldowns} refills them one at a time, one per cooldown.
 */
public record ClassSkill(String id, String name, Material icon, int cooldownSeconds, int charges) {

    public ClassSkill(String id, String name, Material icon, int cooldownSeconds) {
        this(id, name, icon, cooldownSeconds, 1);
    }
}
