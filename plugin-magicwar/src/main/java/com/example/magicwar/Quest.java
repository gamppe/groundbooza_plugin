package com.example.magicwar;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One objective on the class board. The tag is what sits in the brackets: "공통" for the three
 * a class always shows, and otherwise the name of the tier-2 class this quest unlocks, in that
 * class's own colour - finishing it is what lets you take that 전직.
 *
 * <p>What a quest watches for is {@link #goal()}, one of the names in {@link Goal}, narrowed by
 * {@link #filter()} - mob types, materials or skill ids, depending on the goal. Adding a quest
 * is a line in {@link MagicClass} plus, if the goal is new, a case in {@link QuestTracker} and
 * whatever raises it. Nothing about a quest is per-class, so the same goal serves several.
 */
public record Quest(String id, String tag, NamedTextColor color, String title, int target,
                    Material icon, String goal, Set<String> filter, Reward reward, boolean best) {

    public static final String COMMON_TAG = "공통";

    /** Every goal the tracker knows. Most take a filter; the comment says of what. */
    public static final class Goal {
        /** Mob types, or nothing for anything at all. */
        public static final String KILL = "kill";
        /** Mob types as {@code TYPE:count} - each capped on its own, the target is the sum. */
        public static final String KILL_QUOTA = "kill_quota";
        public static final String KILL_SWORD = "kill_sword";
        public static final String KILL_FIST = "kill_fist";
        public static final String KILL_HOSTILE_FIST = "kill_hostile_fist";
        public static final String KILL_BURNING = "kill_burning";
        /** Skill ids: the killing blow came from that spell, and the victim was hostile. */
        public static final String KILL_SKILL_HOSTILE = "kill_skill_hostile";
        /** Distinct mob types killed or fed. */
        public static final String DISTINCT_MOB = "distinct_mob";
        /** Mobs dying anywhere near the player, whoever killed them. */
        public static final String NEARBY_DEATH = "nearby_death";
        /** Kills by the player's own summons. */
        public static final String SUMMON_KILL = "summon_kill";
        /** Health the player's summons have regained. */
        public static final String SUMMON_HEAL = "summon_heal";

        /** Block materials. */
        public static final String MINE = "mine";
        /** Item materials. */
        public static final String PICKUP = "pickup";
        /** Item materials. */
        public static final String EAT = "eat";
        /** Armour pieces worn at once - best, not a total. */
        public static final String WEAR_ARMOR = "wear_armor";
        public static final String WEAR_DYED_HELMET = "wear_dyed_helmet";
        public static final String EXTINGUISH = "extinguish";
        public static final String GLOW_SIGN = "glow_sign";
        public static final String SNEAK_ON_ROD = "sneak_on_rod";
        public static final String TAME_WOLF = "tame_wolf";

        /** Skill ids: casts that went off. */
        public static final String SKILL_CAST = "skill_cast";
        /** Skill ids: most things hit by a single cast - best, not a total. */
        public static final String SKILL_MULTI = "skill_multi";
        /** Skill ids: damage dealt, added up. */
        public static final String SKILL_DAMAGE = "skill_damage";
        /** Mobs put into 동상. */
        public static final String FROST_APPLY = "frost_apply";
        /** Targets sealed in an ice prison. */
        public static final String ICE_TRAP = "ice_trap";
        /** Lightning called down by 피뢰침. */
        public static final String ROD_STRIKE = "rod_strike";
        /** Long-range 볼트마법 hits landed in quick succession - best, not a total. */
        public static final String BOLT_SNIPER = "bolt_sniper";
        /** A player found by 흔적 추적. */
        public static final String TRACK_FIND = "track_find";
        /** Taking a harmful effect on purpose, by drinking or eating something. */
        public static final String DEBUFF = "debuff";

        /** A slot that has no quest in it yet. Never advances, never pays out. */
        public static final String NONE = "none";

        private Goal() {
        }
    }

    public static Quest of(String id, String tag, NamedTextColor color, String title, int target,
                           Material icon, String goal, Reward reward, String... filter) {
        return new Quest(id, tag, color, title, target, icon, goal,
                Set.copyOf(new LinkedHashSet<>(Set.of(filter))), reward, false);
    }

    /** A quest measured by the best single attempt rather than by a running total: three mobs
     * in one cast is either done or not, and two casts of two do not add up to it. */
    public static Quest best(String id, String tag, NamedTextColor color, String title, int target,
                             Material icon, String goal, Reward reward, String... filter) {
        return new Quest(id, tag, color, title, target, icon, goal,
                Set.copyOf(new LinkedHashSet<>(Set.of(filter))), reward, true);
    }

    /** An empty slot on the board, for a tier that has been laid out but not written yet. */
    public static Quest todo(String id, String tag, NamedTextColor color) {
        return new Quest(id, tag, color, "미정", 1, Material.BARRIER, Goal.NONE, Set.of(),
                Reward.of("미정"), false);
    }

    public boolean isPlaceholder() {
        return Goal.NONE.equals(goal);
    }

    /** True when this quest cares about {@code name} - an empty filter cares about everything. */
    public boolean matches(String name) {
        return filter.isEmpty() || filter.contains(name);
    }

    /** How many of {@code name} a {@link Goal#KILL_QUOTA} quest wants, or 0 if it wants none. */
    public int quotaFor(String name) {
        String prefix = name + ":";
        for (String entry : filter) {
            if (entry.startsWith(prefix)) {
                return Integer.parseInt(entry.substring(prefix.length()));
            }
        }
        return 0;
    }

    /** "[공통] 돼지 4마리 잡기", "[수도사] 맨손으로 적대 몬스터 5마리 처치" */
    public String display() {
        return "[" + tag + "] " + title;
    }
}
