package com.example.magicwar;

import org.bukkit.Material;

import java.util.List;

/** The three classes a player picks from when the arena opens. Each has three upgrade lines and
 * a list of castable skills - both placeholders for now, so the plumbing is in place and the
 * real content is a matter of editing these lists. */
public enum MagicClass {

    BATTLE_MAGE("배틀메이지", Material.STONE_SWORD, "마법을 두른 근접 전사",
            List.of(ClassTrack.pending("1번 강화"), ClassTrack.pending("2번 강화"), ClassTrack.pending("3번 강화")),
            List.of(new ClassSkill("첫번째 스킬", Material.SKULL_BANNER_PATTERN, 10))),
    SORCERER("소서러", Material.FIRE_CHARGE, "원거리 주문으로 싸우는 마법사",
            List.of(ClassTrack.pending("1번 강화"), ClassTrack.pending("2번 강화"), ClassTrack.pending("3번 강화")),
            List.of(new ClassSkill("첫번째 스킬", Material.FLOW_BANNER_PATTERN, 10))),
    SUMMONER("서머너", Material.PIG_SPAWN_EGG, "소환수를 부려 싸우는 술사",
            List.of(ClassTrack.pending("1번 강화"), ClassTrack.pending("2번 강화"), ClassTrack.pending("3번 강화")),
            List.of(new ClassSkill("첫번째 스킬", Material.GUSTER_BANNER_PATTERN, 10)));

    private final String label;
    private final Material icon;
    private final String blurb;
    private final List<ClassTrack> tracks;
    private final List<ClassSkill> skills;

    MagicClass(String label, Material icon, String blurb, List<ClassTrack> tracks, List<ClassSkill> skills) {
        this.label = label;
        this.icon = icon;
        this.blurb = blurb;
        this.tracks = tracks;
        this.skills = skills;
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

    /** Always exactly three - ClassUpgradeHolder lays them out by index. */
    public List<ClassTrack> tracks() {
        return tracks;
    }

    public ClassTrack track(int index) {
        return tracks.get(index);
    }

    /** Castable skills, handed out as items the moment the class is picked. */
    public List<ClassSkill> skills() {
        return skills;
    }

    public ClassSkill skill(int index) {
        return skills.get(index);
    }
}
