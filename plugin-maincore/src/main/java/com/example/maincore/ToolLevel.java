package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** The single "maincore:tool_level" PDC tag every upgradeable job tool carries. Kept as one
 * shared key so ServerBridge's farm-server copy only has to know one literal name. */
public class ToolLevel {

    public static final String KEY_NAME = "tool_level";

    private final NamespacedKey key;

    public ToolLevel(MainCorePlugin plugin) {
        this.key = new NamespacedKey(plugin, KEY_NAME);
    }

    public void set(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, Math.max(0, level));
        item.setItemMeta(meta);
    }

    /** 0 for anything untagged (tools created before upgrades existed). */
    public int get(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer level = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return level == null ? 0 : level;
    }

    public static Component loreLine(int level) {
        return Component.text("업그레이드 " + level + " / " + UpgradeTrack.MAX_LEVEL + "단계", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false);
    }
}
