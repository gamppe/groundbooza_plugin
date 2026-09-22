package com.example.magicwar;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The board and everyone's progress on it. Like ClassManager this is memory only and wiped
 * between rounds - a match is the whole lifetime of a quest.
 *
 * <p>A board is five quests, because that is how many slots the class screen lays out: the
 * player's two 전직 quests on the second row, then the three shared ones on the fourth. Which
 * 전직 quests you see therefore depends on the class you started as.
 */
public class QuestManager {

    public static final List<Quest> COMMON = List.of(
            Quest.kill("pig", Quest.COMMON_TAG, NamedTextColor.WHITE, "돼지 4마리 잡기", 4,
                    Material.PORKCHOP, "경험치 10", EntityType.PIG),
            Quest.mine("log", Quest.COMMON_TAG, NamedTextColor.WHITE, "나무 5개 캐기", 5,
                    Material.OAK_LOG, "경험치 10", Tag.LOGS.getValues()),
            Quest.mine("stone", Quest.COMMON_TAG, NamedTextColor.WHITE, "돌 30개 캐기", 30,
                    Material.COBBLESTONE, "경험치 20", Set.of(Material.STONE, Material.COBBLESTONE,
                            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE)));

    /** Board order == slot order in ClassUpgradeHolder: 전직 quests first, then the shared ones. */
    public static List<Quest> boardFor(MagicClass magicClass) {
        if (magicClass == null) {
            return COMMON;
        }
        List<Quest> board = new ArrayList<>();
        magicClass.advancements().forEach(advancement -> board.add(advancement.quest()));
        board.addAll(COMMON);
        return List.copyOf(board);
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

    /** @return false when it was already claimed or is not finished yet. */
    public boolean claim(UUID uuid, Quest quest) {
        if (!isComplete(uuid, quest) || isClaimed(uuid, quest)) {
            return false;
        }
        of(uuid).claimed().put(quest.id(), true);
        return true;
    }

    public void onKill(UUID uuid, MagicClass magicClass, EntityType type, BiConsumer<Quest, Boolean> onProgress) {
        for (Quest quest : boardFor(magicClass)) {
            if (quest.matchesKill(type) && advance(uuid, quest, onProgress)) {
                return;
            }
        }
    }

    public void onMine(UUID uuid, MagicClass magicClass, Material material, BiConsumer<Quest, Boolean> onProgress) {
        for (Quest quest : boardFor(magicClass)) {
            if (quest.matchesMine(material) && advance(uuid, quest, onProgress)) {
                return;
            }
        }
    }

    /** One kill or block only ever counts toward one quest - the first unfinished match. */
    private boolean advance(UUID uuid, Quest quest, BiConsumer<Quest, Boolean> onProgress) {
        if (isComplete(uuid, quest)) {
            return false;
        }
        Progress p = of(uuid);
        int now = p.counts().getOrDefault(quest.id(), 0) + 1;
        p.counts().put(quest.id(), now);
        onProgress.accept(quest, now >= quest.target());
        return true;
    }

    public void clear() {
        progress.clear();
    }
}
