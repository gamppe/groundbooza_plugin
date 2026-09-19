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

public class SpecialHoeItem {

    private final NamespacedKey markerKey;

    public SpecialHoeItem(MainCorePlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "special_hoe");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHERITE_HOE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("만능 개간 괭이", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("우클릭: 3x3 범위 개간", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("다 자란 작물 우클릭: 즉시 수확", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("흙/잔디 채굴 시 즉시 파괴", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("거래불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSpecialHoe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }
}
