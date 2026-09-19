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

/** Builds and tracks the shop's rentable tools: untradeable, cannot leave via the cross-server
 * box, and expire (disappear) a fixed time after purchase. */
public class RentalToolItem {

    public static final long DURATION_MILLIS = 2L * 60 * 60 * 1000;

    private final NamespacedKey markerKey;
    private final NamespacedKey expiresKey;

    public RentalToolItem(MainCorePlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "rental_marker");
        this.expiresKey = new NamespacedKey(plugin, "rental_expires_at");
    }

    /** The real, live item handed to a buyer - starts counting down immediately. */
    public ItemStack createRental(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        applyEnchants(item, material);
        ItemMeta meta = item.getItemMeta();
        if (material == Material.FISHING_ROD) {
            meta.setUnbreakable(true);
        }
        meta.displayName(Component.text(displayName, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        long expiresAt = System.currentTimeMillis() + DURATION_MILLIS;
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(expiresKey, PersistentDataType.LONG, expiresAt);
        meta.lore(buildLiveLore(DURATION_MILLIS));
        item.setItemMeta(meta);
        return item;
    }

    /** A display-only preview for the shop browse menu - same look, no marker, no countdown. */
    public ItemStack createPreview(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        applyEnchants(item, material);
        ItemMeta meta = item.getItemMeta();
        if (material == Material.FISHING_ROD) {
            meta.setUnbreakable(true);
        }
        meta.displayName(Component.text(displayName, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("거래불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("차원이동불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("대여 시간: 2시간", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private void applyEnchants(ItemStack item, Material material) {
        if (material == Material.FISHING_ROD) {
            item.addUnsafeEnchantment(Enchantment.LURE, 3);
        } else {
            item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
            item.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        }
    }

    public boolean isRentalTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }

    public Long getExpiresAt(ItemStack item) {
        if (!isRentalTool(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(expiresKey, PersistentDataType.LONG);
    }

    /** Rewrites the countdown line to match the time actually remaining. Call periodically. */
    public void refreshLore(ItemStack item) {
        Long expiresAt = getExpiresAt(item);
        if (expiresAt == null) {
            return;
        }
        long remaining = Math.max(0, expiresAt - System.currentTimeMillis());
        ItemMeta meta = item.getItemMeta();
        meta.lore(buildLiveLore(remaining));
        item.setItemMeta(meta);
    }

    private List<Component> buildLiveLore(long remainingMillis) {
        return List.of(
                Component.text("거래불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("차원이동불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text(formatRemaining(remainingMillis), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
        );
    }

    private String formatRemaining(long remainingMillis) {
        long totalMinutes = remainingMillis / 60000;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0 && minutes > 0) {
            return hours + "시간 " + minutes + "분 남음";
        }
        if (hours > 0) {
            return hours + "시간 남음";
        }
        if (minutes > 0) {
            return minutes + "분 남음";
        }
        return "곧 만료";
    }
}
