package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * 주인 없는 땅문서 - the rarest thing in the lava/void fishing tables. Unlike every other deed
 * this one is NOT backed by a pending_deeds row and has no owner: whoever right-clicks it gets
 * a random unclaimed overworld cell (see WildDeedListener). That also means it is deliberately
 * left out of ServerBridge's box blocklist, so one fished up on farm-server can be carried over
 * to main-server, where the land system actually lives.
 */
public class WildDeedItem {

    public static final String KEY_NAME = "wild_deed";
    public static final String LABEL = "주인 없는 땅문서";

    private final NamespacedKey markerKey;

    public WildDeedItem(MainCorePlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, KEY_NAME);
    }

    public ItemStack create() {
        return build(markerKey);
    }

    /** Shared with ServerBridge's copy so both servers stamp an identical item. */
    static ItemStack build(NamespacedKey markerKey) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(LABEL, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        meta.lore(List.of(
                Component.text("누군가의 땅문서입니다. 활성화하면 오버월드의 무작위 비어있는 땅을 얻습니다.",
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWildDeed(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return Boolean.TRUE.equals(
                item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN));
    }
}
