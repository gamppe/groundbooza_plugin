package com.example.magicwar;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who picked which class, and how far each of their upgrade lines has come.
 *
 * <p>Deliberately memory only and wiped between rounds: the event server keeps no database (see
 * ServerBridge's features switches), and a class is meant to last one match anyway.
 */
public class ClassManager {

    private static final class Pick {
        final MagicClass magicClass;
        MagicClass.Advancement advancement;

        Pick(MagicClass magicClass) {
            this.magicClass = magicClass;
        }
    }

    private final Map<UUID, Pick> picks = new ConcurrentHashMap<>();

    public MagicClass classOf(UUID uuid) {
        Pick pick = picks.get(uuid);
        return pick == null ? null : pick.magicClass;
    }

    public boolean hasClass(UUID uuid) {
        return picks.containsKey(uuid);
    }

    public void choose(UUID uuid, MagicClass magicClass) {
        picks.put(uuid, new Pick(magicClass));
    }

    public MagicClass.Advancement advancementOf(UUID uuid) {
        Pick pick = picks.get(uuid);
        return pick == null ? null : pick.advancement;
    }

    public boolean hasAdvanced(UUID uuid) {
        return advancementOf(uuid) != null;
    }

    public void advance(UUID uuid, MagicClass.Advancement advancement) {
        Pick pick = picks.get(uuid);
        if (pick != null) {
            pick.advancement = advancement;
        }
    }

    /** Every skill the player has earned: the class's starting ones, plus the 전직 skill. The
     * index in this list is the [N] shown on the item. */
    public java.util.List<ClassSkill> ownedSkills(UUID uuid) {
        Pick pick = picks.get(uuid);
        if (pick == null) {
            return java.util.List.of();
        }
        java.util.List<ClassSkill> owned = new java.util.ArrayList<>(pick.magicClass.skills());
        if (pick.advancement != null) {
            owned.add(pick.advancement.skill());
        }
        return java.util.List.copyOf(owned);
    }

    /** The owned skill with this id, or null if the player never had it. */
    public ClassSkill skillById(UUID uuid, String id) {
        return ownedSkills(uuid).stream().filter(skill -> skill.id().equals(id)).findFirst().orElse(null);
    }

    /** What the player calls themselves now: the tier-2 name once taken, else the base class. */
    public String displayName(UUID uuid) {
        Pick pick = picks.get(uuid);
        if (pick == null) {
            return null;
        }
        return pick.advancement == null ? pick.magicClass.label() : pick.advancement.label();
    }

    public void clear() {
        picks.clear();
    }
}
