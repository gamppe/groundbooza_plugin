package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** A compass that points to the nearest instance of whichever biome the player picks the first
 * time they use it (bound permanently to that physical item after that). */
public class CompassBiomeFinderItem {

    /** Curated, long-stable biome names so the shop's picker list doesn't need constant upkeep. */
    public static final List<Biome> CHOICES = List.of(
            Biome.DESERT, Biome.JUNGLE, Biome.SNOWY_PLAINS, Biome.OCEAN,
            Biome.SWAMP, Biome.SAVANNA, Biome.MUSHROOM_FIELDS, Biome.FOREST);

    public static final int SEARCH_RADIUS = 6400;

    private final NamespacedKey markerKey;
    private final NamespacedKey targetKey;

    public CompassBiomeFinderItem(MainCorePlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "special_compass");
        this.targetKey = new NamespacedKey(plugin, "compass_target_biome");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("바이옴 탐지 나침반", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("우클릭: 원하는 바이옴 선택 (최초 1회)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("이후 우클릭 시 그 바이옴 방향을 가리킵니다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("거래불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSpecialCompass(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }

    public Biome getTargetBiome(ItemStack item) {
        if (!isSpecialCompass(item)) {
            return null;
        }
        String name = item.getItemMeta().getPersistentDataContainer().get(targetKey, PersistentDataType.STRING);
        if (name == null) {
            return null;
        }
        try {
            return Biome.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setTargetBiome(ItemStack item, Biome biome) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(targetKey, PersistentDataType.STRING, biome.name());
        meta.displayName(Component.text("바이옴 탐지 나침반 (" + biome.name() + ")", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
    }
}
