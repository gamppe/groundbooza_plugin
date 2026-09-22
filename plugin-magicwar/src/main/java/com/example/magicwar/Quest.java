package com.example.magicwar;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Set;

/**
 * One objective on the class screen. Two kinds are tracked for now - killing a mob type and
 * breaking blocks - which is enough for every quest currently listed; a new kind means adding
 * a match method here and a listener hook in QuestListener.
 */
public record Quest(String id, QuestCategory category, String title, int target,
                    Material icon, String reward, EntityType killType, Set<Material> mineTypes) {

    public static Quest kill(String id, QuestCategory category, String title, int target,
                             Material icon, String reward, EntityType type) {
        return new Quest(id, category, title, target, icon, reward, type, Set.of());
    }

    public static Quest mine(String id, QuestCategory category, String title, int target,
                             Material icon, String reward, Set<Material> blocks) {
        return new Quest(id, category, title, target, icon, reward, null, blocks);
    }

    public boolean matchesKill(EntityType type) {
        return killType != null && killType == type;
    }

    public boolean matchesMine(Material material) {
        return mineTypes.contains(material);
    }

    /** "[공통] 돼지 4마리 잡기" */
    public String display() {
        return category.tag() + " " + title;
    }
}
