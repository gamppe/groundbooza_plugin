package com.example.magicwar;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 * <p>A board is always five quests, because that is how many slots the class screen lays out:
 * three on the second row and two on the fourth. Before 전직 the fourth row holds the two
 * unlock quests; after it, the third row is the tier-2 class's own three and the fourth row is
 * where tier 3 will go.
 */
public class QuestManager {

    /** The tier-3 row: laid out, not written. They never advance and never pay out. */
    public static final List<Quest> TIER_3 = List.of(
            Quest.todo("tier3_a", "3차전직", NamedTextColor.DARK_GRAY),
            Quest.todo("tier3_b", "3차전직", NamedTextColor.DARK_GRAY));

    /** Board order == slot order in ClassUpgradeHolder: the three 공통 quests, then the two on
     * the 전직 row. */
    public static List<Quest> boardFor(MagicClass magicClass, MagicClass.Advancement advancement) {
        if (magicClass == null) {
            return List.of();
        }
        List<Quest> board = new ArrayList<>();
        if (advancement == null) {
            board.addAll(magicClass.quests());
            magicClass.advancements().forEach(option -> board.add(option.quest()));
        } else {
            board.addAll(advancement.quests());
            board.addAll(TIER_3);
        }
        return List.copyOf(board);
    }

    /**
     * @param counts how far each quest has come, by quest id.
     * @param seen   the tokens a quest has already been credited for, by quest id - which mob
     *               types have been met for 서로 다른 종류, and how many of each type a quota
     *               quest still wants.
     */
    private record Progress(Map<String, Integer> counts, Map<String, Boolean> claimed,
                            Map<String, Set<String>> seen, Map<String, Map<String, Integer>> quotas) {}

    private final Map<UUID, Progress> progress = new ConcurrentHashMap<>();

    private Progress of(UUID uuid) {
        return progress.computeIfAbsent(uuid, u -> new Progress(new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>()));
    }

    public int count(UUID uuid, Quest quest) {
        return Math.min(quest.target(), of(uuid).counts().getOrDefault(quest.id(), 0));
    }

    public boolean isComplete(UUID uuid, Quest quest) {
        return !quest.isPlaceholder() && count(uuid, quest) >= quest.target();
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

    /**
     * Credits whatever just happened to the first unfinished quest on the board that is
     * watching for it. One kill, one block, one cast only ever counts once - a board never has
     * two quests after the same thing, and if it ever did, letting both tick would make the
     * order they happen to sit in matter.
     *
     * @param name  what the goal is being narrowed by: a mob type, a material, a skill id. May
     *              be null for goals that take no filter.
     * @param value how much - an amount to add, or for a {@link Quest#best()} quest the result
     *              of this one attempt.
     */
    public void fire(UUID uuid, MagicClass magicClass, MagicClass.Advancement advancement,
                     String goal, String name, int value, BiConsumer<Quest, Boolean> onProgress) {
        if (value <= 0) {
            return;
        }
        for (Quest quest : boardFor(magicClass, advancement)) {
            if (!quest.goal().equals(goal) || isComplete(uuid, quest) || !wants(quest, name)) {
                continue;
            }
            if (apply(uuid, quest, name, value, onProgress)) {
                return;
            }
        }
    }

    /** A quota quest keeps its counts inside the filter ("VILLAGER:4"), so a bare mob name
     * would never match it the ordinary way. */
    private static boolean wants(Quest quest, String name) {
        if (name == null) {
            return true;
        }
        return Quest.Goal.KILL_QUOTA.equals(quest.goal()) ? quest.quotaFor(name) > 0 : quest.matches(name);
    }

    private boolean apply(UUID uuid, Quest quest, String name, int value,
                          BiConsumer<Quest, Boolean> onProgress) {
        Progress p = of(uuid);
        int now;
        if (quest.best()) {
            // Only the best single attempt counts, so a worse one is not progress at all.
            int previous = p.counts().getOrDefault(quest.id(), 0);
            if (value <= previous) {
                return false;
            }
            now = value;
        } else if (Quest.Goal.DISTINCT_MOB.equals(quest.goal())) {
            Set<String> seen = p.seen().computeIfAbsent(quest.id(), id -> new HashSet<>());
            if (!seen.add(name)) {
                return false; // this kind has already been counted
            }
            now = seen.size();
        } else if (Quest.Goal.KILL_QUOTA.equals(quest.goal())) {
            int quota = quest.quotaFor(name);
            Map<String, Integer> mine = p.quotas().computeIfAbsent(quest.id(), id -> new HashMap<>());
            int done = mine.getOrDefault(name, 0);
            if (quota == 0 || done >= quota) {
                return false; // this part of the quota is already filled
            }
            mine.put(name, done + 1);
            now = mine.values().stream().mapToInt(Integer::intValue).sum();
        } else {
            now = p.counts().getOrDefault(quest.id(), 0) + value;
        }
        p.counts().put(quest.id(), now);
        onProgress.accept(quest, now >= quest.target());
        return true;
    }

    /** Marks a quest finished outright - the operator's shortcut past the grind. */
    public void complete(UUID uuid, Quest quest) {
        if (!quest.isPlaceholder()) {
            of(uuid).counts().put(quest.id(), quest.target());
        }
    }

    public void reset(UUID uuid) {
        progress.remove(uuid);
    }

    public void clear() {
        progress.clear();
    }
}
