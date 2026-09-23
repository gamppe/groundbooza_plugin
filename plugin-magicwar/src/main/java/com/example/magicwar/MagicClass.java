package com.example.magicwar;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.List;

/**
 * The three classes a player picks from when the arena opens, the tier-2 classes each one
 * branches into at 전직, and every quest either tier puts on the board.
 *
 * <p>A base class shows its own three quests plus the unlock quest of each tier-2 class it can
 * become; once the 전직 is taken the board switches to that class's three, with the tier-3 row
 * still to be written. Rewards are data too - see {@link Reward} and {@link Perks} - so the
 * whole progression is this one file plus the handful of places the perks are read.
 */
public enum MagicClass {

    BATTLE_MAGE("배틀메이지", Material.STONE_SWORD, "마법을 두른 근접 전사",
            List.of(new ClassSkill("wave_shot", "파동탄", Material.SKULL_BANNER_PATTERN, 10)),
            List.of(
                    Quest.of("bm_sword", Quest.COMMON_TAG, NamedTextColor.WHITE,
                            "검으로 아무나 4회 처치", 4, Material.IRON_SWORD,
                            Quest.Goal.KILL_SWORD, Reward.of("주는 피해 +1", Perks.DAMAGE, 1)),
                    Quest.best("bm_armor", Quest.COMMON_TAG, NamedTextColor.WHITE,
                            "방어구 2개 동시 장착", 2, Material.IRON_CHESTPLATE,
                            Quest.Goal.WEAR_ARMOR, Reward.of("영구 방어력 +4", Perks.ARMOR, 4)),
                    Quest.of("bm_cooked", Quest.COMMON_TAG, NamedTextColor.WHITE,
                            "구운 음식 섭취", 1, Material.COOKED_BEEF,
                            Quest.Goal.EAT, Reward.of("최대체력 +4", Perks.HEALTH, 4),
                            "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN", "COOKED_MUTTON",
                            "COOKED_RABBIT", "COOKED_COD", "COOKED_SALMON", "BAKED_POTATO",
                            "DRIED_KELP")),
            List.of(
                    new Advancement("monk", "수도사", Material.GOLD_INGOT, NamedTextColor.GOLD,
                            new ClassSkill("thunder_strike", "뇌격", Material.CREEPER_BANNER_PATTERN, 20),
                            Quest.of("adv_monk", "수도사", NamedTextColor.GOLD,
                                    "맨손으로 적대 몬스터 5회 처치", 5, Material.GOLD_NUGGET,
                                    Quest.Goal.KILL_HOSTILE_FIST, Reward.of("수도사 전직 가능")),
                            List.of(
                                    Quest.of("monk_fist", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "맨손으로 아무나 10회 처치", 10, Material.GOLDEN_APPLE,
                                            Quest.Goal.KILL_FIST,
                                            Reward.of("마르지않는 힘물약 획득", Perks.STRENGTH_POTION, 1)),
                                    Quest.of("monk_wave", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "파동탄으로 적대적 몹 10회 처치", 10, Material.WIND_CHARGE,
                                            Quest.Goal.KILL_SKILL_HOSTILE,
                                            Reward.of("파동탄 명중 시 10초간 신속 I",
                                                    Perks.WAVE_SHOT_SPEED, 1),
                                            "wave_shot"),
                                    Quest.best("monk_thunder", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "뇌격으로 한번에 3마리 타격", 3, Material.LIGHTNING_ROD,
                                            Quest.Goal.SKILL_MULTI,
                                            Reward.of("뇌격 착지 반경 +1", Perks.THUNDER_RADIUS, 1),
                                            "thunder_strike")),
                            Reward.of("주는 피해 +2", Perks.DAMAGE, 2)),
                    new Advancement("frost_knight", "서리기사", Material.BLUE_ICE, NamedTextColor.AQUA,
                            new ClassSkill("ice_spike", "얼음송곳", Material.PIGLIN_BANNER_PATTERN, 20),
                            Quest.of("adv_frost_knight", "서리기사", NamedTextColor.AQUA,
                                    "스트레이 · 보그드 · 허스크 중 1회 처치", 1, Material.ICE,
                                    Quest.Goal.KILL, Reward.of("서리기사 전직 가능"),
                                    "STRAY", "BOGGED", "HUSK"),
                            List.of(
                                    Quest.of("fk_frost", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "몹을 동상 상태로 20번 만들기", 20, Material.POWDER_SNOW_BUCKET,
                                            Quest.Goal.FROST_APPLY,
                                            Reward.of("동상이 이동속도 20% 감소", Perks.FROST_SLOW, 1)),
                                    Quest.best("fk_spike", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "얼음송곳으로 한번에 3마리 타격", 3, Material.PACKED_ICE,
                                            Quest.Goal.SKILL_MULTI,
                                            Reward.of("얼음송곳의 폭 증가", Perks.ICE_SPIKE_WIDE, 1),
                                            "ice_spike"),
                                    Quest.of("fk_prison", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "얼음뭉치로 5번 가두기", 5, Material.ICE,
                                            Quest.Goal.ICE_TRAP,
                                            Reward.of("얼음이 깨질 때 피해 +5", Perks.ICE_PRISON_DAMAGE, 5))),
                            Reward.of("최대체력 +4", Perks.HEALTH, 4),
                            List.of(new ClassSkill("wave_shot", "얼음뭉치",
                                    Material.SKULL_BANNER_PATTERN, 8))))),

    SORCERER("소서러", Material.FIRE_CHARGE, "원거리 주문으로 싸우는 마법사",
            List.of(new ClassSkill("bolt", "볼트마법", Material.FLOW_BANNER_PATTERN, 6, 3)),
            List.of(
                    Quest.of("sc_fire", Quest.COMMON_TAG, NamedTextColor.WHITE,
                            "불 5번 끄기", 5, Material.WATER_BUCKET,
                            Quest.Goal.EXTINGUISH, Reward.of("주는 피해 +2", Perks.DAMAGE, 2)),
                    Quest.of("sc_sign", Quest.COMMON_TAG, NamedTextColor.WHITE,
                            "표지판에 빛나는 먹물 바르기", 1, Material.GLOW_INK_SAC,
                            Quest.Goal.GLOW_SIGN,
                            Reward.of("영구 야간투시, 주는 피해 +1",
                                    Perks.NIGHT_VISION, 1, Perks.DAMAGE, 1)),
                    Quest.of("sc_helmet", Quest.COMMON_TAG, NamedTextColor.WHITE,
                            "염색된 가죽 투구 장착", 1, Material.LEATHER_HELMET,
                            Quest.Goal.WEAR_DYED_HELMET, Reward.of("최대체력 +4", Perks.HEALTH, 4))),
            List.of(
                    new Advancement("pyromancer", "파이로맨서", Material.BLAZE_POWDER, NamedTextColor.RED,
                            new ClassSkill("lava_eruption", "용암분출", Material.GLOBE_BANNER_PATTERN, 30),
                            Quest.of("adv_pyromancer", "파이로맨서", NamedTextColor.RED,
                                    "불타는 적 5마리 처치", 5, Material.BLAZE_POWDER,
                                    Quest.Goal.KILL_BURNING, Reward.of("파이로맨서 전직 가능")),
                            List.of(
                                    Quest.of("py_obsidian", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "흑요석 1개 채취", 1, Material.OBSIDIAN,
                                            Quest.Goal.MINE, Reward.of("주는 피해 +4", Perks.DAMAGE, 4),
                                            "OBSIDIAN"),
                                    Quest.of("py_erupt", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "용암분출 8회 시전", 8, Material.MAGMA_BLOCK,
                                            Quest.Goal.SKILL_CAST,
                                            Reward.of("용암분출 반경 +1", Perks.ERUPTION_RADIUS, 1),
                                            "lava_eruption"),
                                    Quest.of("py_bolt", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "볼트마법 누적 피해 200", 200, Material.FIRE_CHARGE,
                                            Quest.Goal.SKILL_DAMAGE,
                                            Reward.of("볼트마법이 3갈래로 발사", Perks.BOLT_SPLIT, 1),
                                            "bolt")),
                            Reward.of("화염 피해 면역", Perks.FIRE_IMMUNE, 1)),
                    new Advancement("electromancer", "일렉트로맨서", Material.LIGHTNING_ROD, NamedTextColor.YELLOW,
                            new ClassSkill("lightning_rod", "피뢰침", Material.FLOWER_BANNER_PATTERN, 15),
                            Quest.of("adv_electromancer", "일렉트로맨서", NamedTextColor.YELLOW,
                                    "피뢰침 위에 서서 웅크리기", 1, Material.LIGHTNING_ROD,
                                    Quest.Goal.SNEAK_ON_ROD, Reward.of("일렉트로맨서 전직 가능")),
                            List.of(
                                    Quest.best("el_multi", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "한번의 볼트마법으로 3마리 타격", 3, Material.COPPER_INGOT,
                                            Quest.Goal.SKILL_MULTI,
                                            Reward.of("볼트마법 경직 +0.1초", Perks.BOLT_ROOT, 2),
                                            "bolt"),
                                    Quest.of("el_rod", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "피뢰침마법으로 번개 10번 소환", 10, Material.LIGHTNING_ROD,
                                            Quest.Goal.ROD_STRIKE,
                                            Reward.of("감전 최대 스택 +1", Perks.SHOCK_STACKS, 1)),
                                    Quest.best("el_snipe", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "1초 내로 40m 밖의 적에게 볼트마법 2회 적중", 2, Material.SPYGLASS,
                                            Quest.Goal.BOLT_SNIPER,
                                            Reward.of("볼트마법 최대 스택 +1", Perks.CHARGES + "bolt", 1))),
                            Reward.of("최대체력 +2", Perks.HEALTH, 2),
                            List.of(new ClassSkill("bolt", "볼트마법",
                                    Material.FLOW_BANNER_PATTERN, 3, 3))))),

    SUMMONER("서머너", Material.PIG_SPAWN_EGG, "소환수를 부려 싸우는 술사",
            List.of(new ClassSkill("pig_burst", "돼지 소환", Material.GUSTER_BANNER_PATTERN, 40)),
            List.of(
                    Quest.of("sm_variety", Quest.COMMON_TAG, NamedTextColor.WHITE,
                            "서로 다른 종류의 몹 7마리 처치 또는 먹이주기", 7, Material.WHEAT,
                            Quest.Goal.DISTINCT_MOB, Reward.of("소환마법의 소환수 +1", Perks.SUMMON_COUNT, 1)),
                    Quest.of("sm_redstone", Quest.COMMON_TAG, NamedTextColor.WHITE,
                            "레드스톤 20개 획득", 20, Material.REDSTONE,
                            Quest.Goal.PICKUP,
                            Reward.of("소환마법 쿨타임 -3초, 최대체력 +4",
                                    Perks.COOLDOWN + "pig_burst", 3, Perks.HEALTH, 4),
                            "REDSTONE"),
                    Quest.of("sm_apple", Quest.COMMON_TAG, NamedTextColor.WHITE,
                            "사과 먹기", 1, Material.APPLE,
                            Quest.Goal.EAT, Reward.of("최대체력 +4", Perks.HEALTH, 4), "APPLE")),
            List.of(
                    new Advancement("necromancer", "네크로맨서", Material.WITHER_SKELETON_SKULL,
                            NamedTextColor.DARK_PURPLE,
                            new ClassSkill("corpse_explosion", "시체폭발",
                                    Material.BORDURE_INDENTED_BANNER_PATTERN, 60),
                            Quest.of("adv_necromancer", "네크로맨서", NamedTextColor.DARK_PURPLE,
                                    "주민 4회, 철골렘 1회 처치", 5, Material.EMERALD,
                                    Quest.Goal.KILL_QUOTA, Reward.of("네크로맨서 전직 가능"),
                                    "VILLAGER:4", "IRON_GOLEM:1"),
                            List.of(
                                    Quest.of("nc_corpse", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "시체폭발 10회 사용", 10, Material.WITHER_SKELETON_SKULL,
                                            Quest.Goal.SKILL_CAST,
                                            Reward.of("소환수 폭발 시 50% 확률로 좀벌레 생성",
                                                    Perks.NECRO_SILVERFISH, 1),
                                            "corpse_explosion"),
                                    Quest.of("nc_debuff", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "부정적인 효과가 있는 것 마시기", 1, Material.SPIDER_EYE,
                                            Quest.Goal.DEBUFF,
                                            Reward.of("소환하는 좀비피글린이 황금칼 무장",
                                                    Perks.PIGLIN_SWORD, 1)),
                                    Quest.of("nc_deaths", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "주위에서 죽은 몹 총합 100마리", 100, Material.SKELETON_SKULL,
                                            Quest.Goal.NEARBY_DEATH,
                                            Reward.of("주변 사망 시 20% 확률로 새끼 조글린 생성",
                                                    Perks.NECRO_ZOGLIN, 1))),
                            Reward.of("기본 언데드가 더이상 적대하지 않음", Perks.UNDEAD_PEACE, 1),
                            List.of(new ClassSkill("pig_burst", "돼지 소환",
                                    Material.GUSTER_BANNER_PATTERN, 20))),
                    new Advancement("druid", "드루이드", Material.BONE, NamedTextColor.GREEN,
                            new ClassSkill("track", "흔적 추적", Material.FIELD_MASONED_BANNER_PATTERN, 60),
                            Quest.of("adv_druid", "드루이드", NamedTextColor.GREEN,
                                    "늑대 길들이기", 1, Material.BONE,
                                    Quest.Goal.TAME_WOLF, Reward.of("드루이드 전직 가능")),
                            List.of(
                                    Quest.of("dr_summon_kill", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "소환수로 몹 10마리 처치", 10, Material.BONE,
                                            Quest.Goal.SUMMON_KILL,
                                            Reward.of("길들일 수 있는 늑대 +1, 소환수 +1",
                                                    Perks.WOLF_TAME, 1, Perks.SUMMON_COUNT, 1)),
                                    Quest.of("dr_heal", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "소환수의 체력 총합 20 회복", 20, Material.COOKED_BEEF,
                                            Quest.Goal.SUMMON_HEAL,
                                            Reward.of("소환수 사망 시 보호막 2 회복", Perks.SHIELD_REFILL, 2)),
                                    Quest.of("dr_track", Quest.COMMON_TAG, NamedTextColor.WHITE,
                                            "흔적 추적으로 플레이어의 흔적 발견", 1, Material.COMPASS,
                                            Quest.Goal.TRACK_FIND,
                                            Reward.of("추적 성공 시 나와 소환수가 20초간 신속 I · 힘 I",
                                                    Perks.TRACK_BUFF, 1))),
                            Reward.of("보호막 +6", Perks.ABSORPTION, 6),
                            List.of(new ClassSkill("pig_burst", "늑대 소환",
                                    Material.GUSTER_BANNER_PATTERN, 40)))));

    /**
     * A tier-2 class: its own colour, its own extra skill, the quest that unlocks it, the three
     * quests it puts on the board afterwards, and what taking it grants outright.
     *
     * @param overrides replacements for base-class skills, matched by id. This is how an
     *                  advancement renames or re-times a skill it inherits - 서리기사 turns
     *                  파동탄 into 얼음뭉치 on a shorter cooldown without it becoming a
     *                  separate skill, so the item and its cooldown group carry straight over.
     */
    public record Advancement(String id, String label, Material icon, NamedTextColor color,
                              ClassSkill skill, Quest quest, List<Quest> quests, Reward bonus,
                              List<ClassSkill> overrides) {

        public Advancement(String id, String label, Material icon, NamedTextColor color,
                           ClassSkill skill, Quest quest, List<Quest> quests, Reward bonus) {
            this(id, label, icon, color, skill, quest, quests, bonus, List.of());
        }

        /** The replacement for {@code base}, or {@code base} itself when nothing overrides it. */
        public ClassSkill apply(ClassSkill base) {
            return overrides.stream().filter(o -> o.id().equals(base.id())).findFirst().orElse(base);
        }
    }

    private final String label;
    private final Material icon;
    private final String blurb;
    private final List<ClassSkill> skills;
    private final List<Quest> quests;
    private final List<Advancement> advancements;

    MagicClass(String label, Material icon, String blurb, List<ClassSkill> skills,
               List<Quest> quests, List<Advancement> advancements) {
        this.label = label;
        this.icon = icon;
        this.blurb = blurb;
        this.skills = skills;
        this.quests = quests;
        this.advancements = advancements;
    }

    public String label() {
        return label;
    }

    public Material icon() {
        return icon;
    }

    public String blurb() {
        return blurb;
    }

    /** Castable skills, handed out as items the moment the class is picked. */
    public List<ClassSkill> skills() {
        return skills;
    }

    public ClassSkill skill(int index) {
        return skills.get(index);
    }

    /** The three quests this class shows before any 전직. */
    public List<Quest> quests() {
        return quests;
    }

    public List<Advancement> advancements() {
        return advancements;
    }
}
