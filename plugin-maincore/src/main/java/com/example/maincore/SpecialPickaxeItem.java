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

/** 광부's upgradeable tool: the harmless "explosion" radius grows with the job upgrade level. */
public class SpecialPickaxeItem {

    private final NamespacedKey markerKey;
    private final ToolLevel toolLevel;

    public SpecialPickaxeItem(MainCorePlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "special_pickaxe");
        this.toolLevel = new ToolLevel(plugin);
    }

    /** Per 폭발 효과 track level 0..5: nothing at all until the first upgrade, then the cube
     * grows 2 → 3 → 3 → 4 → 4 and the odds climb on the levels where the size doesn't, so every
     * upgrade changes something. ServerBridge's farm-server copy mirrors both tables. */
    private static final int[] EXPLOSION_SIZES = {0, 2, 3, 3, 4, 4};
    private static final double[] EXPLOSION_CHANCES = {0.0, 0.05, 0.08, 0.12, 0.12, 0.16};

    private static int clampLevel(int level) {
        return Math.max(0, Math.min(EXPLOSION_SIZES.length - 1, level));
    }

    /** Edge length of the blast cube in front of the player (0 = no blast at this level). */
    public static int explosionSize(int level) {
        return EXPLOSION_SIZES[clampLevel(level)];
    }

    /** Chance per broken block that the blast triggers. */
    public static double explosionChance(int level) {
        return EXPLOSION_CHANCES[clampLevel(level)];
    }

    public ItemStack create() {
        return create(0);
    }

    public ItemStack create(int level) {
        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("자석 곡괭이", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        setLevel(item, level);
        return item;
    }

    public void setLevel(ItemStack item, int level) {
        int size = explosionSize(level);
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                Component.text("블록을 캐면 아이템을 바로 획득", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("들고 있으면 주변 아이템을 끌어당김", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(size == 0 ? "폭발 효과 없음 (업그레이드로 해금)"
                                : Math.round(explosionChance(level) * 100) + "% 확률로 바라보는 앞쪽 "
                                        + size + "x" + size + "x" + size + " 블록을 폭파 (피해 없음)", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("흑요석 등 단단한 블록은 폭파되지 않음", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                ToolLevel.loreLine(level),
                Component.text("거래불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        toolLevel.set(item, level);
    }

    public int getLevel(ItemStack item) {
        return toolLevel.get(item);
    }

    public boolean isSpecialPickaxe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }
}
