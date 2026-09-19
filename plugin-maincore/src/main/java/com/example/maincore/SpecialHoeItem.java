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

/** 농부's tool. Its working area follows the 범위 업그레이드 track: a single block until the
 * first upgrade, then 3x3 → 7x7. The level is baked into the item's PDC (ToolLevel) so
 * farm-server's ServerBridge copy can read it without any job data of its own. */
public class SpecialHoeItem {

    private final NamespacedKey markerKey;
    private final ToolLevel toolLevel;

    public SpecialHoeItem(MainCorePlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "special_hoe");
        this.toolLevel = new ToolLevel(plugin);
    }

    /** Edge length of the till/plant/harvest square per 범위 level 0..5 (ServerBridge mirrors this). */
    private static final int[] AREA_SIZES = {1, 3, 4, 5, 6, 7};

    public static int areaSize(int level) {
        return AREA_SIZES[Math.max(0, Math.min(AREA_SIZES.length - 1, level))];
    }

    public ItemStack create() {
        return create(0);
    }

    public ItemStack create(int level) {
        ItemStack item = new ItemStack(Material.NETHERITE_HOE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("만능 개간 괭이", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        setLevel(item, level);
        return item;
    }

    /** Rewrites the level tag + lore in place (used when the player buys an upgrade). */
    public void setLevel(ItemStack item, int level) {
        int size = areaSize(level);
        String area = size == 1 ? "1칸" : size + "x" + size + " 범위";
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                Component.text("흙 우클릭: " + area + " 개간", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("왼손에 씨앗을 들고 우클릭: " + area + " 심기", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("다 자란 작물 우클릭: " + area + " 수확", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("흙/잔디 채굴 시 즉시 파괴", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                ToolLevel.loreLine(level),
                Component.text("거래불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        toolLevel.set(item, level);
    }

    public int getLevel(ItemStack item) {
        return toolLevel.get(item);
    }

    public boolean isSpecialHoe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }
}
