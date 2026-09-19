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

/** Excluded from the shop's "everything is netherite" rule - stays a plain wooden axe.
 * 건축가's upgradeable tool: the max fill volume doubles per job upgrade level. */
public class SpecialAxeItem {

    private static final long BASE_FILL_VOLUME = 10_000;

    private final NamespacedKey markerKey;
    private final ToolLevel toolLevel;

    public SpecialAxeItem(MainCorePlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "special_axe");
        this.toolLevel = new ToolLevel(plugin);
    }

    /** 1만 → 2만 → 4만 → 8만 blocks per fill. */
    public static long maxFillVolume(int level) {
        return BASE_FILL_VOLUME * (1L << Math.max(0, level));
    }

    public ItemStack create() {
        return create(0);
    }

    public ItemStack create(int level) {
        ItemStack item = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("지형 채우기 도끼", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        setLevel(item, level);
        return item;
    }

    public void setLevel(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                Component.text("우클릭으로 두 지점을 지정", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("오프핸드에 든 블록으로 범위를 채웁니다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("필요한 만큼 인벤토리에서 소모됩니다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(String.format("한 번에 최대 %,d블록", maxFillVolume(level)), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                ToolLevel.loreLine(level),
                Component.text("거래불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("차원이동불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        toolLevel.set(item, level);
    }

    public int getLevel(ItemStack item) {
        return toolLevel.get(item);
    }

    public boolean isSpecialAxe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }
}
