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

import java.util.List;

/** No netherite fishing rod exists in vanilla, so these stay plain fishing rods - tiered purely
 * by how much Luck of the Sea is baked in (reuses vanilla's own catch-odds math instead of a
 * custom loot table). 어부's upgrade track adds Lure on top (level 0 → none, 3 → Lure III),
 * the same on every tier. */
public class FishingRodTierItem {

    public enum Tier {
        WORN("낡은 낚시대", 0),
        GOOD("좋은 낚시대", 1),
        GREAT("대단한 낚시대", 3);

        public final String label;
        public final int luckOfTheSeaLevel;

        Tier(String label, int luckOfTheSeaLevel) {
            this.label = label;
            this.luckOfTheSeaLevel = luckOfTheSeaLevel;
        }
    }

    private final NamespacedKey markerKey;
    private final NamespacedKey tierKey;
    private final ToolLevel toolLevel;

    public FishingRodTierItem(MainCorePlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "special_rod");
        this.tierKey = new NamespacedKey(plugin, "special_rod_tier");
        this.toolLevel = new ToolLevel(plugin);
    }

    public static int lureLevel(int level) {
        return Math.max(0, Math.min(3, level));
    }

    public ItemStack create(Tier tier) {
        return create(tier, 0);
    }

    public ItemStack create(Tier tier, int level) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        if (tier.luckOfTheSeaLevel > 0) {
            item.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, tier.luckOfTheSeaLevel);
        }
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(tier.label, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.name());
        item.setItemMeta(meta);
        setLevel(item, level);
        return item;
    }

    public void setLevel(ItemStack item, int level) {
        Tier tier = getTier(item);
        if (tier == null) {
            return;
        }
        int lure = lureLevel(level);
        if (lure > 0) {
            item.addUnsafeEnchantment(Enchantment.LURE, lure);
        } else {
            item.removeEnchantment(Enchantment.LURE);
        }
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new java.util.ArrayList<>(List.of(
                Component.text("보물 확률이 오른 낚시대 (바다의 행운 " + tier.luckOfTheSeaLevel + "레벨)",
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        if (tier == Tier.GREAT) {
            lore.add(Component.text("용암/허공(엔드)에서도 낚시가 가능합니다", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text(lure > 0 ? "미끼 " + lure + "레벨 부여됨" : "업글 시 미끼 인챈트 부여", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
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

    public Tier getTier(ItemStack item) {
        if (!isSpecialRod(item)) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return Tier.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
