package com.example.magicwar;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Set;

/**
 * One objective on the class board. The tag is what sits in the brackets: "공통" for the shared
 * ones, and otherwise the name of the tier-2 class this quest unlocks, in that class's own
 * colour - finishing it is what lets you take that 전직.
 *
 * <p>Two kinds are tracked, killing a mob type and breaking blocks, which covers every quest
 * currently listed; a new kind means a match method here and a hook in QuestListener.
 */
public record Quest(String id, String tag, NamedTextColor color, String title, int target,
                    Material icon, String reward, EntityType killType, Set<Material> mineTypes) {

    public static final String COMMON_TAG = "공통";

    public static Quest kill(String id, String tag, NamedTextColor color, String title, int target,
                             Material icon, String reward, EntityType type) {
        return new Quest(id, tag, color, title, target, icon, reward, type, Set.of());
    }

    public static Quest mine(String id, String tag, NamedTextColor color, String title, int target,
                             Material icon, String reward, Set<Material> blocks) {
        return new Quest(id, tag, color, title, target, icon, reward, null, blocks);
    }

    public boolean matchesKill(EntityType type) {
        return killType != null && killType == type;
    }

    public boolean matchesMine(Material material) {
        return mineTypes.contains(material);
    }

    /** "[공통] 돼지 4마리 잡기", "[룬나이트] 좀비 5마리 잡기" */
    public String display() {
        return "[" + tag + "] " + title;
    }
}
