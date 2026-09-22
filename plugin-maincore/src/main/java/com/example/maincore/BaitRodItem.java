package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** 어부's one special rod, 수제미끼낚시대. It carries no enchantments at all - what it catches is
 * decided entirely by FishingLoot from the 섬세한 미끼제작 track level baked into the item (see
 * ToolLevel). Replaced the old three-tier Luck-of-the-Sea rods; a leftover tiered rod still
 * passes isSpecialRod (same marker key) and is normalised the next time setLevel runs. */
public class BaitRodItem {

    public static final String LABEL = "수제미끼낚시대";

    private final NamespacedKey markerKey;
    private final ToolLevel toolLevel;

    public BaitRodItem(MainCorePlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "special_rod");
        this.toolLevel = new ToolLevel(plugin);
    }

    public ItemStack create() {
        return create(0);
    }

    public ItemStack create(int level) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        setLevel(item, level);
        return item;
    }

    public void setLevel(ItemStack item, int level) {
        if (!isSpecialRod(item)) {
            return;
        }
        // Old tiered rods had Luck of the Sea / Lure baked in - strip them so vanilla odds
        // never leak past FishingLoot.
        item.removeEnchantment(Enchantment.LUCK_OF_THE_SEA);
        item.removeEnchantment(Enchantment.LURE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(LABEL, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("직접 만든 미끼로 색다른 물고기를 낚습니다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("미끼: " + FishingLoot.baitEffect(level), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(ToolLevel.loreLine(level));
        lore.add(Component.text("거래불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        toolLevel.set(item, level);
    }

    public int getLevel(ItemStack item) {
        return toolLevel.get(item);
    }

    public boolean isSpecialRod(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }
}
