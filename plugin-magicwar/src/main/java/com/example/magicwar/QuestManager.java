package com.example.magicwar;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The quest board and everyone's progress on it. Like ClassManager this is memory only and
 * wiped between rounds - a match is the whole lifetime of a quest.
 *
 * <p>The board is fixed at five, because that is how many slots the class screen lays out:
 * two on the second row, three on the fourth.
 */
public class QuestManager {

    /** Index in this list == slot in ClassUpgradeHolder.questSlot(). */
    public static final List<Quest> BOARD = List.of(
            Quest.kill("pig", QuestCategory.COMMON, "돼지 4마리 잡기", 4,
                    Material.PORKCHOP, "경험치 10 · 금 주괴 2", EntityType.PIG),
            Quest.mine("log", QuestCategory.COMMON, "나무 5개 캐기", 5,
                    Material.OAK_LOG, "경험치 10 · 빵 5", Tag.LOGS.getValues()),
            Quest.mine("stone", QuestCategory.COMMON, "돌 30개 캐기", 30,
                    Material.COBBLESTONE, "경험치 20 · 철 주괴 3", Set.of(Material.STONE, Material.COBBLESTONE,
                            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE)),
            Quest.kill("zombie", QuestCategory.SECOND, "좀비 5마리 잡기", 5,
                    Material.ROTTEN_FLESH, "2차 전직 해금", EntityType.ZOMBIE),
            Quest.mine("iron", QuestCategory.THIRD, "철 광석 5개 캐기", 5,
                    Material.RAW_IRON, "경험치 30 · 다이아몬드 1",
                    Set.of(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE)));

    /** The quest that gates 전직 - the anvil stays locked until this one is done. */
    public static Quest advancementQuest() {
        return BOARD.stream().filter(q -> q.category() == QuestCategory.SECOND).findFirst().orElse(null);
    }

    private record Progress(Map<String, Integer> counts, Map<String, Boolean> claimed) {}

    private final Map<UUID, Progress> progress = new ConcurrentHashMap<>();

    private Progress of(UUID uuid) {
        return progress.computeIfAbsent(uuid, u -> new Progress(new HashMap<>(), new HashMap<>()));
    }

    public int count(UUID uuid, Quest quest) {
        return Math.min(quest.target(), of(uuid).counts().getOrDefault(quest.id(), 0));
    }

    public boolean isComplete(UUID uuid, Quest quest) {
        return count(uuid, quest) >= quest.target();
    }

    public boolean isClaimed(UUID uuid, Quest quest) {
        return of(uuid).claimed().getOrDefault(quest.id(), false);
    }

    /** @return true when this tick of progress is the one that completed the quest. */
    public boolean advance(UUID uuid, Quest quest, int amount) {
        Progress p = of(uuid);
        int before = p.counts().getOrDefault(quest.id(), 0);
        if (before >= quest.target()) {
            return false;
        }
        p.counts().put(quest.id(), before + amount);
        return before + amount >= quest.target();
    }

    /** @return false when it was already claimed or is not finished yet. */
    public boolean claim(UUID uuid, Quest quest) {
        if (!isComplete(uuid, quest) || isClaimed(uuid, quest)) {
            return false;
        }
        of(uuid).claimed().put(quest.id(), true);
        return true;
    }

    public void onKill(UUID uuid, EntityType type, java.util.function.BiConsumer<Quest, Boolean> onProgress) {
        for (Quest quest : BOARD) {
            if (quest.matchesKill(type) && advanceAndReport(uuid, quest, onProgress)) {
                return;
            }
        }
    }

    public void onMine(UUID uuid, Material material, java.util.function.BiConsumer<Quest, Boolean> onProgress) {
        for (Quest quest : BOARD) {
            if (quest.matchesMine(material) && advanceAndReport(uuid, quest, onProgress)) {
                return;
            }
        }
    }

    private boolean advanceAndReport(UUID uuid, Quest quest,
                                     java.util.function.BiConsumer<Quest, Boolean> onProgress) {
        if (isComplete(uuid, quest)) {
            return false;
        }
        boolean finished = advance(uuid, quest, 1);
        onProgress.accept(quest, finished);
        return true;
    }

    public void clear() {
        progress.clear();
    }
}
