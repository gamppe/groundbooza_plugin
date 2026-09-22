package com.example.maincore;

import org.bukkit.Material;

import java.util.List;

/** The five player jobs. Each one gates its own slice of the special shop and has three
 * upgrade tracks (UpgradeTrack); exactly one of those tracks is baked into the job's tool item
 * (`itemTrack`, see ToolLevel). Only 광부 has all three designed so far - the others keep their
 * original single tool upgrade as track 1 with the rest marked 준비 중. */
public enum Job {
    FARMER("농부", Material.NETHERITE_HOE, List.of(
            "씨앗/동물 스폰알 구매, 흙·울타리·발광석 무료",
            "경험치를 뼛가루로 전환",
            "만능 개간 괭이 / 행운 / 기우제"),
            List.of(
                    UpgradeTrack.of("행운", Material.RABBIT_FOOT,
                            List.of("작물 수확 시 드롭이 2배가 될 확률이 붙습니다", "동물 먹이 효과의 확률도 올라갑니다", "(플레이어에게 붙는 효과)"),
                            level -> level == 0 ? "없음" : "2배 드롭 " + Math.round(FarmerAbilities.luckChance(level) * 100) + "%"),
                    UpgradeTrack.of("범위 업그레이드", Material.GOLDEN_HOE,
                            List.of("만능 개간 괭이의 개간/심기/수확 범위가 넓어집니다", "(들고 있는 괭이에 바로 적용)"),
                            level -> {
                                int size = SpecialHoeItem.areaSize(level);
                                return size == 1 ? "1칸 (범위 없음)" : size + "x" + size;
                            }),
                    UpgradeTrack.of("기우제", Material.FLOW_BANNER_PATTERN,
                            List.of("웅크린 채 5번 점프하면 서 있는 땅의 작물이 2단계 자랍니다 (사탕무 등 짧은 작물은 1단계)",
                                    "1단계에서 해금, 이후 단계마다 쿨타임 5분 단축"),
                            level -> level == 0 ? "잠김"
                                    : "쿨타임 " + FarmerAbilities.rainCooldownMinutes(level) + "분")),
            1),
    FISHER("어부", Material.FISHING_ROD, List.of(
            "수제미끼낚시대 구매",
            "낚시능력증가: 물고기/쓰레기/보물 비율이 좋아짐",
            "섬세한 미끼제작: 지역별 희귀 물고기, 용암/허공 낚시",
            "낚시꾼의 행운: 웅크리고 우클릭 → 즉시 입질 버프"),
            List.of(
                    UpgradeTrack.of("낚시능력증가", Material.COD,
                            List.of("좋은 물고기가 잡힐 확률이 올라갑니다.", "보물이 낚일 수도 있습니다.", "(플레이어에게 붙는 효과)"),
                            FishingLoot::abilityEffect),
                    UpgradeTrack.of("섬세한 미끼제작", Material.STRING,
                            List.of("미끼를 제작하는 노하우를 얻어 색다른 물고기를 얻을 수 있을 것 같습니다.", "(수제미끼낚시대에만 적용)"),
                            FishingLoot::baitEffect),
                    UpgradeTrack.of("낚시꾼의 행운", Material.HEART_OF_THE_SEA,
                            List.of("웅크린 채 낚시대를 우클릭하면 잠시 동안 찌가 물에 닿자마자 입질이 옵니다",
                                    "네더/허공 낚시의 대기시간도 대폭 줄어듭니다",
                                    "1단계에서 해금 (지속 5초, 쿨타임 30분), 이후 단계마다 쿨타임 5분 단축·지속 1.5초 증가"),
                            FisherAbilities::effect)),
            1),
    MINER("광부", Material.NETHERITE_PICKAXE, List.of(
            "횃불 무료",
            "원광을 주괴로 즉시 전환",
            "자석 곡괭이 / 효율 증가 / 지상 텔레포트"),
            List.of(
                    UpgradeTrack.of("효율 증가", Material.GOLDEN_PICKAXE,
                            List.of("모든 블록을 캐는 속도가 빨라집니다", "(도구가 아니라 플레이어에게 붙는 효과)"),
                            level -> level == 0 ? "기본 속도"
                                    : "채굴 속도 +" + Math.round(MinerAbilities.efficiencyBonus(level) * 100) + "%"),
                    UpgradeTrack.of("폭발 효과", Material.TNT,
                            List.of("자석 곡괭이의 폭발 범위와 확률이 커집니다", "(들고 있는 곡괭이에 바로 적용)"),
                            level -> {
                                int size = SpecialPickaxeItem.explosionSize(level);
                                if (size == 0) return "비활성";
                                return Math.round(SpecialPickaxeItem.explosionChance(level) * 100) + "% 확률로 "
                                        + size + "x" + size + "x" + size + " 폭파";
                            }),
                    UpgradeTrack.of("지상 텔레포트", Material.ENDER_PEARL,
                            List.of("3초간 웅크린 뒤 점프하면 지상 맨 위로 순간이동",
                                    "1단계에서 해금, 이후 단계마다 쿨타임 5분 단축"),
                            level -> level == 0 ? "잠김"
                                    : "쿨타임 " + MinerAbilities.teleportCooldownMinutes(level) + "분")),
            1),
    BUILDER("건축가", Material.BRICKS, List.of(
            "무료 땅문서를 하나 더 받음",
            "염료 무료",
            "지형 채우기 도끼 (업글: 한 번에 채우는 블록 수 증가)"),
            List.of(
                    UpgradeTrack.of("채우기 용량", Material.WOODEN_AXE,
                            List.of("지형 채우기 도끼로 한 번에 채울 수 있는 블록 수가 늘어납니다"),
                            level -> String.format("한 번에 %,d블록", SpecialAxeItem.maxFillVolume(level))),
                    UpgradeTrack.unavailable(),
                    UpgradeTrack.unavailable()),
            0),
    ADVENTURER("모험가", Material.COMPASS, List.of(
            "토지선점권 문서 구매 (최대 5개)",
            "바이옴 탐지 나침반 구매",
            "이동속도 신발 (업글: 이동/수영 속도, 오르는 높이 증가)"),
            List.of(
                    UpgradeTrack.of("신발 강화", Material.NETHERITE_BOOTS,
                            List.of("이동속도 신발의 이동/수영 속도와 오르는 높이가 늘어납니다"),
                            level -> {
                                double step = SpeedBootsItem.stepHeightBonus(level);
                                int strider = SpeedBootsItem.depthStriderLevel(level);
                                return "이동속도 +" + Math.round(SpeedBootsItem.speedBonus(level) * 100) + "%"
                                        + (strider > 0 ? ", 수영 " + strider : "")
                                        + (step > 0 ? ", " + (step >= 1.4 ? "2칸" : "1칸") + " 오르기" : "");
                            }),
                    UpgradeTrack.unavailable(),
                    UpgradeTrack.unavailable()),
            0);

    private final String label;
    private final Material icon;
    private final List<String> perks;
    private final List<UpgradeTrack> tracks;
    private final int itemTrack;

    Job(String label, Material icon, List<String> perks, List<UpgradeTrack> tracks, int itemTrack) {
        this.label = label;
        this.icon = icon;
        this.perks = perks;
        this.tracks = tracks;
        this.itemTrack = itemTrack;
    }

    public String label() {
        return label;
    }

    public Material icon() {
        return icon;
    }

    public List<String> perks() {
        return perks;
    }

    /** Always exactly three (JobUpgradeHolder lays them out by index). */
    public List<UpgradeTrack> tracks() {
        return tracks;
    }

    public UpgradeTrack track(int index) {
        return tracks.get(index);
    }

    /** Index of the track whose level is stamped onto the job's tool item. */
    public int itemTrack() {
        return itemTrack;
    }

    /** Null-safe lookup of a stored name (a stale/unknown DB value just reads as "no job"). */
    public static Job fromName(String raw) {
        if (raw == null) return null;
        try {
            return valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
