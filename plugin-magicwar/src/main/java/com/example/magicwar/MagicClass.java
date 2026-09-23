package com.example.magicwar;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * The three classes a player picks from when the arena opens, and the tier-2 classes each one
 * branches into at 전직.
 *
 * <p>Every tier-2 class owns the quest that unlocks it: that quest shows on the board tagged
 * with the class's name in its own colour, and the anvil only lets you take the 전직 whose quest
 * you finished. Skill effects are still placeholders, so filling the game in is a matter of
 * editing these lists.
 */
public enum MagicClass {

    BATTLE_MAGE("배틀메이지", Material.STONE_SWORD, "마법을 두른 근접 전사",
            List.of(new ClassSkill("wave_shot", "파동탄", Material.SKULL_BANNER_PATTERN, 10)),
            List.of(
                    new Advancement("monk", "수도사", Material.IRON_SWORD, NamedTextColor.GOLD,
                            new ClassSkill("thunder_strike", "뇌격", Material.CREEPER_BANNER_PATTERN, 20),
                            Quest.kill("adv_monk", "수도사", NamedTextColor.GOLD,
                                    "좀비 5마리 잡기", 5, Material.ROTTEN_FLESH, "수도사 전직 해금",
                                    EntityType.ZOMBIE)),
                    new Advancement("frost_knight", "서리기사", Material.DIAMOND_SWORD, NamedTextColor.AQUA,
                            new ClassSkill("ice_spike", "얼음송곳", Material.PIGLIN_BANNER_PATTERN, 20),
                            Quest.kill("adv_frost_knight", "서리기사", NamedTextColor.AQUA,
                                    "거미 5마리 잡기", 5, Material.STRING, "서리기사 전직 해금",
                                    EntityType.SPIDER),
                            List.of(new ClassSkill("wave_shot", "얼음뭉치",
                                    Material.SKULL_BANNER_PATTERN, 8))))),

    SORCERER("소서러", Material.FIRE_CHARGE, "원거리 주문으로 싸우는 마법사",
            List.of(new ClassSkill("bolt", "볼트마법", Material.FLOW_BANNER_PATTERN, 6, 3)),
            List.of(
                    new Advancement("pyromancer", "파이로맨서", Material.BLAZE_POWDER, NamedTextColor.RED,
                            new ClassSkill("lava_eruption", "용암분출", Material.GLOBE_BANNER_PATTERN, 30),
                            Quest.mine("adv_pyromancer", "파이로맨서", NamedTextColor.RED,
                                    "석탄 10개 캐기", 10, Material.COAL, "파이로맨서 전직 해금",
                                    java.util.Set.of(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE))),
                    new Advancement("electromancer", "일렉트로맨서", Material.LIGHTNING_ROD, NamedTextColor.YELLOW,
                            new ClassSkill("lightning_rod", "피뢰침", Material.FLOWER_BANNER_PATTERN, 15),
                            Quest.kill("adv_electromancer", "일렉트로맨서", NamedTextColor.YELLOW,
                                    "스켈레톤 5마리 잡기", 5, Material.BONE, "일렉트로맨서 전직 해금",
                                    EntityType.SKELETON),
                            List.of(new ClassSkill("bolt", "볼트마법",
                                    Material.FLOW_BANNER_PATTERN, 3, 3))))),

    SUMMONER("서머너", Material.PIG_SPAWN_EGG, "소환수를 부려 싸우는 술사",
            List.of(new ClassSkill("pig_burst", "돼지 소환", Material.GUSTER_BANNER_PATTERN, 40)),
            List.of(
                    new Advancement("necromancer", "네크로맨서", Material.WITHER_SKELETON_SKULL,
                            NamedTextColor.DARK_PURPLE,
                            new ClassSkill("corpse_explosion", "시체폭발",
                                    Material.BORDURE_INDENTED_BANNER_PATTERN, 60),
                            Quest.kill("adv_necromancer", "네크로맨서", NamedTextColor.DARK_PURPLE,
                                    "소 5마리 잡기", 5, Material.LEATHER, "네크로맨서 전직 해금",
                                    EntityType.COW),
                            List.of(new ClassSkill("pig_burst", "돼지 소환",
                                    Material.GUSTER_BANNER_PATTERN, 20))),
                    new Advancement("druid", "드루이드", Material.BONE, NamedTextColor.GREEN,
                            new ClassSkill("track", "흔적 추적", Material.FIELD_MASONED_BANNER_PATTERN, 60),
                            Quest.kill("adv_druid", "드루이드", NamedTextColor.GREEN,
                                    "양 5마리 잡기", 5, Material.WHITE_WOOL, "드루이드 전직 해금",
                                    EntityType.SHEEP),
                            List.of(new ClassSkill("pig_burst", "늑대 소환",
                                    Material.GUSTER_BANNER_PATTERN, 40)))));

    /**
     * A tier-2 class: its own colour, its own extra skill, and the quest that unlocks it.
     *
     * @param overrides replacements for base-class skills, matched by id. This is how an
     *                  advancement renames or re-times a skill it inherits - 서리기사 turns
     *                  파동탄 into 얼음뭉치 on a shorter cooldown without it becoming a
     *                  separate skill, so the item and its cooldown group carry straight over.
     */
    public record Advancement(String id, String label, Material icon, NamedTextColor color,
                              ClassSkill skill, Quest quest, List<ClassSkill> overrides) {

        public Advancement(String id, String label, Material icon, NamedTextColor color,
                           ClassSkill skill, Quest quest) {
            this(id, label, icon, color, skill, quest, List.of());
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
    private final List<Advancement> advancements;

    MagicClass(String label, Material icon, String blurb,
               List<ClassSkill> skills, List<Advancement> advancements) {
        this.label = label;
        this.icon = icon;
        this.blurb = blurb;
        this.skills = skills;
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

    public List<Advancement> advancements() {
        return advancements;
    }
}
