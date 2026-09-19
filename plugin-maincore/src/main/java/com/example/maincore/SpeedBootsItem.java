package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** 모험가's upgradeable item. Everything it does is plain vanilla item data (attribute modifiers
 * + Depth Strider), so it also works on farm-server with no extra code there. */
public class SpeedBootsItem {

    private final NamespacedKey markerKey;
    private final NamespacedKey speedModifierKey;
    private final NamespacedKey stepModifierKey;
    private final ToolLevel toolLevel;

    public SpeedBootsItem(MainCorePlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "special_boots");
        this.speedModifierKey = new NamespacedKey(plugin, "boots_speed");
        this.stepModifierKey = new NamespacedKey(plugin, "boots_step");
        this.toolLevel = new ToolLevel(plugin);
    }

    /** +10% per level starting at +10%: 0.1 → 0.2 → 0.3 → 0.4 (multiplicative on base speed). */
    public static double speedBonus(int level) {
        return 0.1 * (Math.max(0, level) + 1);
    }

    /** Depth Strider level doubles as the "swim speed" upgrade. */
    public static int depthStriderLevel(int level) {
        return Math.max(0, Math.min(3, level));
    }

    /** Added on top of vanilla's 0.6 step height: level 1-2 climb a full block, level 3 two. */
    public static double stepHeightBonus(int level) {
        if (level >= 3) return 1.4;
        if (level >= 1) return 0.4;
        return 0.0;
    }

    public ItemStack create() {
        return create(0);
    }

    public ItemStack create(int level) {
        ItemStack item = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("이동속도 신발", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        setLevel(item, level);
        return item;
    }

    public void setLevel(ItemStack item, int level) {
        int strider = depthStriderLevel(level);
        if (strider > 0) {
            item.addUnsafeEnchantment(Enchantment.DEPTH_STRIDER, strider);
        } else {
            item.removeEnchantment(Enchantment.DEPTH_STRIDER);
        }
        ItemMeta meta = item.getItemMeta();
        meta.removeAttributeModifier(Attribute.MOVEMENT_SPEED);
        meta.removeAttributeModifier(Attribute.STEP_HEIGHT);
        meta.addAttributeModifier(Attribute.MOVEMENT_SPEED, new AttributeModifier(
                speedModifierKey, speedBonus(level), AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.FEET));
        double step = stepHeightBonus(level);
        if (step > 0) {
            meta.addAttributeModifier(Attribute.STEP_HEIGHT, new AttributeModifier(
                    stepModifierKey, step, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET));
        }
        List<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("이동속도 +" + Math.round(speedBonus(level) * 100) + "%", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(strider > 0 ? "수영속도 증가 (깊은 바다 탐험가 " + strider + ")" : "업글 시 수영속도 증가",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(step > 0 ? "한 번에 " + (step >= 1.4 ? "2칸" : "1칸") + " 높이까지 오름" : "업글 시 오르는 높이 증가",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(ToolLevel.loreLine(level));
        lore.add(Component.text("거래불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        toolLevel.set(item, level);
    }

    public int getLevel(ItemStack item) {
        return toolLevel.get(item);
    }

    public boolean isSpecialBoots(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }
}
