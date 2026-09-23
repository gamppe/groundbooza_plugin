package com.example.magicwar;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything a player has earned this round, as one bag of named amounts per player.
 *
 * <p>A few of the keys are vanilla stats and are pushed onto the player the moment they change:
 * {@link #DAMAGE} is read back by {@link PerkListener} when they hit something, and the others
 * are an attribute modifier or a potion effect. Every other key is inert here - the skills look
 * themselves up ({@code level(uuid, ERUPTION_RADIUS)}) and decide what it means.
 *
 * <p>Memory only and wiped between rounds, like {@link ClassManager} and {@link QuestManager}:
 * a class, its quests and its rewards all last exactly one match.
 */
public class Perks {

    // ---------- vanilla stats ----------
    /** Flat extra damage on melee, arrows and spells alike. */
    public static final String DAMAGE = "damage";
    /** Flat extra damage on spells only - anything a skill deals, lightning included. */
    public static final String SPELL_DAMAGE = "spell_damage";
    /** Taken off a swing of the fist or a weapon, never below one point of damage. */
    public static final String MELEE_PENALTY = "melee_penalty";
    /** A fraction, not a count: 0.1 is ten per cent faster on foot. */
    public static final String MOVE_SPEED = "move_speed";
    public static final String HEALTH = "health";
    public static final String ARMOR = "armor";
    /** A cap, not a grant: taking it fills the shield, and refills top up to here. */
    public static final String ABSORPTION = "absorption";
    public static final String NIGHT_VISION = "night_vision";
    public static final String FIRE_IMMUNE = "fire_immune";
    /** 네크로맨서: the ordinary undead stop caring that you exist. */
    public static final String UNDEAD_PEACE = "undead_peace";
    /** Earns the 마르지않는 힘물약. */
    public static final String STRENGTH_POTION = "strength_potion";

    // ---------- per-skill, looked up by id ----------
    /** {@code charges_<skill id>}: extra stored casts. */
    public static final String CHARGES = "charges_";
    /** {@code cooldown_<skill id>}: seconds off the cooldown. */
    public static final String COOLDOWN = "cooldown_";

    // ---------- skill behaviour ----------
    public static final String WAVE_SHOT_SPEED = "wave_shot_speed";
    public static final String THUNDER_RADIUS = "thunder_radius";
    public static final String FROST_SLOW = "frost_slow";
    public static final String ICE_SPIKE_WIDE = "ice_spike_wide";
    public static final String ICE_PRISON_DAMAGE = "ice_prison_damage";
    public static final String ERUPTION_RADIUS = "eruption_radius";
    public static final String BOLT_SPLIT = "bolt_split";
    public static final String BOLT_ROOT = "bolt_root";
    public static final String SHOCK_STACKS = "shock_stacks";
    public static final String SUMMON_COUNT = "summon_count";
    public static final String WOLF_TAME = "wolf_tame";
    public static final String NECRO_SILVERFISH = "necro_silverfish";
    public static final String PIGLIN_SWORD = "piglin_sword";
    public static final String NECRO_ZOGLIN = "necro_zoglin";
    public static final String SHIELD_REFILL = "shield_refill";
    public static final String TRACK_BUFF = "track_buff";

    private final NamespacedKey healthKey;
    private final NamespacedKey armorKey;
    private final NamespacedKey speedKey;
    private final Map<UUID, Map<String, Double>> earned = new ConcurrentHashMap<>();
    private int spellDepth;

    public Perks(MagicWarPlugin plugin) {
        this.healthKey = new NamespacedKey(plugin, "perk_health");
        this.armorKey = new NamespacedKey(plugin, "perk_armor");
        this.speedKey = new NamespacedKey(plugin, "perk_speed");
    }

    /**
     * Whether the damage being dealt right now came out of a spell.
     *
     * <p>There is no way to tell from the event: {@code damage(amount, caster)} arrives as
     * ENTITY_ATTACK, exactly like a swing. So {@link SkillEffects} says so on the way in, and
     * {@link PerkListener} asks on the way out. A count rather than a flag because one spell
     * can set off another inside the same call.
     */
    public void beginSpellDamage() {
        spellDepth++;
    }

    public void endSpellDamage() {
        spellDepth = Math.max(0, spellDepth - 1);
    }

    public boolean isSpellDamage() {
        return spellDepth > 0;
    }

    /** Adds a reward to the pile and pushes whatever changed onto the player. */
    public void grant(Player player, Reward reward) {
        if (reward.grants().isEmpty()) {
            return;
        }
        Map<String, Double> mine = earned.computeIfAbsent(player.getUniqueId(),
                uuid -> new ConcurrentHashMap<>());
        reward.grants().forEach((key, value) -> mine.merge(key, value, Double::sum));
        apply(player);
    }

    public double amount(UUID uuid, String key) {
        Map<String, Double> mine = earned.get(uuid);
        return mine == null ? 0 : mine.getOrDefault(key, 0.0);
    }

    /** The same figure as an int, for the many perks that are really a count of levels. */
    public int level(UUID uuid, String key) {
        return (int) Math.round(amount(uuid, key));
    }

    public boolean has(UUID uuid, String key) {
        return amount(uuid, key) > 0;
    }

    /** A skill cooldown after any reduction earned, never dropping under a second. */
    public int cooldownFor(UUID uuid, ClassSkill skill) {
        return Math.max(1, skill.cooldownSeconds() - level(uuid, COOLDOWN + skill.id()));
    }

    public int chargesFor(UUID uuid, ClassSkill skill) {
        return skill.charges() + level(uuid, CHARGES + skill.id());
    }

    /**
     * Re-applies everything that lives on the player rather than in this map. Safe to call at
     * any time - the attribute modifiers are keyed, so they replace rather than stack.
     */
    public void apply(Player player) {
        UUID uuid = player.getUniqueId();
        double before = maxHealth(player);
        attribute(player, Attribute.MAX_HEALTH, healthKey, amount(uuid, HEALTH));
        attribute(player, Attribute.ARMOR, armorKey, amount(uuid, ARMOR));
        attribute(player, Attribute.MOVEMENT_SPEED, speedKey, amount(uuid, MOVE_SPEED),
                AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        // New hearts arrive full: a reward that leaves the player on the same health with more
        // of it missing would read as a punishment.
        double gained = maxHealth(player) - before;
        if (gained > 0 && player.isValid()) {
            player.setHealth(Math.min(maxHealth(player), player.getHealth() + gained));
        }
        if (has(uuid, NIGHT_VISION)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                    PotionEffect.INFINITE_DURATION, 0, false, false, false));
        }
        double shield = amount(uuid, ABSORPTION);
        if (shield > 0 && player.getAbsorptionAmount() < shield) {
            player.setAbsorptionAmount(shield);
        }
    }

    /** Tops the shield back up without ever going over what was earned. */
    public void refillShield(Player player, double by) {
        double cap = amount(player.getUniqueId(), ABSORPTION);
        if (cap <= 0) {
            return;
        }
        player.setAbsorptionAmount(Math.min(cap, player.getAbsorptionAmount() + by));
    }

    private static double maxHealth(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.MAX_HEALTH);
        return instance == null ? 20 : instance.getValue();
    }

    private static void attribute(Player player, Attribute attribute, NamespacedKey key, double value) {
        attribute(player, attribute, key, value, AttributeModifier.Operation.ADD_NUMBER);
    }

    private static void attribute(Player player, Attribute attribute, NamespacedKey key, double value,
                                  AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        List<AttributeModifier> stale = instance.getModifiers().stream()
                .filter(modifier -> key.equals(modifier.getKey())).toList();
        stale.forEach(instance::removeModifier);
        if (value > 0) {
            instance.addModifier(new AttributeModifier(key, value, operation));
        }
    }

    /** Strips the attributes back off before forgetting the player, or the extra hearts would
     * follow them out of the round. */
    public void reset(Player player) {
        earned.remove(player.getUniqueId());
        attribute(player, Attribute.MAX_HEALTH, healthKey, 0);
        attribute(player, Attribute.ARMOR, armorKey, 0);
        attribute(player, Attribute.MOVEMENT_SPEED, speedKey, 0);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.setAbsorptionAmount(0);
        if (player.getHealth() > maxHealth(player)) {
            player.setHealth(maxHealth(player));
        }
    }

    public void clear() {
        earned.clear();
    }
}
