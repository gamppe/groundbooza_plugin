package com.example.magicwar;

import org.bukkit.Material;

import java.util.List;

/** The three classes a player picks from when the arena opens, and the tier-2 classes each one
 * branches into at 전직. Skill effects are still placeholders, so filling the game in is a
 * matter of editing these lists. */
public enum MagicClass {

    BATTLE_MAGE("배틀메이지", Material.STONE_SWORD, "마법을 두른 근접 전사",
            List.of(new ClassSkill("첫번째 스킬", Material.SKULL_BANNER_PATTERN, 10)),
            List.of(new Advancement("룬나이트", Material.IRON_SWORD,
                            new ClassSkill("두번째 스킬", Material.CREEPER_BANNER_PATTERN, 20)),
                    new Advancement("스펠블레이드", Material.DIAMOND_SWORD,
                            new ClassSkill("두번째 스킬", Material.PIGLIN_BANNER_PATTERN, 20)))),
    SORCERER("소서러", Material.FIRE_CHARGE, "원거리 주문으로 싸우는 마법사",
            List.of(new ClassSkill("첫번째 스킬", Material.FLOW_BANNER_PATTERN, 10)),
            List.of(new Advancement("엘리멘탈리스트", Material.BLAZE_POWDER,
                            new ClassSkill("두번째 스킬", Material.GLOBE_BANNER_PATTERN, 20)),
                    new Advancement("네크로맨서", Material.WITHER_SKELETON_SKULL,
                            new ClassSkill("두번째 스킬", Material.FLOWER_BANNER_PATTERN, 20)))),
    SUMMONER("서머너", Material.PIG_SPAWN_EGG, "소환수를 부려 싸우는 술사",
            List.of(new ClassSkill("첫번째 스킬", Material.GUSTER_BANNER_PATTERN, 10)),
            List.of(new Advancement("비스트마스터", Material.WOLF_SPAWN_EGG,
                            new ClassSkill("두번째 스킬", Material.BORDURE_INDENTED_BANNER_PATTERN, 20)),
                    new Advancement("정령술사", Material.ALLAY_SPAWN_EGG,
                            new ClassSkill("두번째 스킬", Material.FIELD_MASONED_BANNER_PATTERN, 20))));

    private final String label;
    private final Material icon;
    private final String blurb;
    private final List<ClassSkill> skills;
    private final List<Advancement> advancements;

    /** A tier-2 class: taken at the anvil once the 2차전직 quest is done. It renames the player's
     * class and brings its own extra skill. */
    public record Advancement(String label, Material icon, ClassSkill skill) {}

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
