package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** The 가이드 book handed out with the arena kit. Right-clicking it is the same as /클래스.
 * The name carries whichever class the holder picked, so it is rewritten when they choose. */
public class ClassGuideItem {

    private final NamespacedKey markerKey;

    public ClassGuideItem(MagicWarPlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "class_guide");
    }

    /** @param magicClass the holder's class, or null before they have picked one. */
    public ItemStack create(MagicClass magicClass) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("[" + (magicClass == null ? "클래스" : magicClass.label()) + "] 가이드",
                NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(magicClass == null ? "우클릭하여 클래스를 선택하세요" : "우클릭하여 클래스를 강화하세요",
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("/클래스 로도 열 수 있습니다", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGuide(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return Boolean.TRUE.equals(
                item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN));
    }

    /** Retitles every guide the player is carrying - called right after they pick a class. */
    public void refresh(Player player, MagicClass magicClass) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isGuide(contents[i])) {
                ItemStack replacement = create(magicClass);
                replacement.setAmount(contents[i].getAmount());
                player.getInventory().setItem(i, replacement);
            }
        }
    }
}
