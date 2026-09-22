package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The castable items a class hands out, named "[1] 첫번째 스킬" and tagged with which class
 * and which slot they belong to. */
public class SkillItem {

    private final NamespacedKey classKey;
    private final NamespacedKey indexKey;

    public SkillItem(MagicWarPlugin plugin) {
        this.classKey = new NamespacedKey(plugin, "skill_class");
        this.indexKey = new NamespacedKey(plugin, "skill_index");
    }

    public ItemStack create(MagicClass magicClass, int index) {
        ClassSkill skill = magicClass.skill(index);
        ItemStack item = new ItemStack(skill.icon());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("[" + (index + 1) + "] " + skill.name(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        meta.lore(List.of(
                Component.text(magicClass.label() + " 스킬", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("우클릭하여 사용 · 쿨타임 " + skill.cooldownSeconds() + "초", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(classKey, PersistentDataType.STRING, magicClass.name());
        meta.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, index);
        item.setItemMeta(meta);
        return item;
    }

    public MagicClass classOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(classKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return MagicClass.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Slot the item casts, or -1 when it is not a skill item at all. */
    public int indexOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return -1;
        }
        Integer index = item.getItemMeta().getPersistentDataContainer().get(indexKey, PersistentDataType.INTEGER);
        return index == null ? -1 : index;
    }
}
