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

public class CreditItem {

    private final NamespacedKey amountKey;
    private final NamespacedKey markerKey;

    public CreditItem(MainCorePlugin plugin) {
        this.amountKey = new NamespacedKey(plugin, "credit_amount");
        this.markerKey = new NamespacedKey(plugin, "credit_note");
    }

    public ItemStack create(long amount) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(String.format("%,d 크레딧", amount), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("우클릭하여 크레딧으로 환원", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));

        meta.getPersistentDataContainer().set(amountKey, PersistentDataType.LONG, amount);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);

        item.setItemMeta(meta);
        return item;
    }

    public boolean isCreditNote(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }

    public Long getAmount(ItemStack item) {
        if (!isCreditNote(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(amountKey, PersistentDataType.LONG);
    }
}
