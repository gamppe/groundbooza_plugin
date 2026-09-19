package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * 말 보관 목줄: an empty lead bought from 생필품. Right-click your tamed horse with it and the
 * horse is packed into the item (glint + stats in the lore); right-click a block with the packed
 * lead and the same horse comes back out, tamed to you, consuming the lead. Main-server only -
 * it's flagged 차원이동불가 and ServerBridge's box refuses it.
 */
public class HorseLeadItem {

    private final NamespacedKey markerKey;
    private final NamespacedKey colorKey;
    private final NamespacedKey styleKey;
    private final NamespacedKey speedKey;
    private final NamespacedKey jumpKey;
    private final NamespacedKey healthKey;
    private final NamespacedKey nameKey;

    public HorseLeadItem(MainCorePlugin plugin) {
        this.markerKey = new NamespacedKey(plugin, "horse_lead");
        this.colorKey = new NamespacedKey(plugin, "horse_color");
        this.styleKey = new NamespacedKey(plugin, "horse_style");
        this.speedKey = new NamespacedKey(plugin, "horse_speed");
        this.jumpKey = new NamespacedKey(plugin, "horse_jump");
        this.healthKey = new NamespacedKey(plugin, "horse_health");
        this.nameKey = new NamespacedKey(plugin, "horse_name");
    }

    public ItemStack createEmpty() {
        ItemStack item = new ItemStack(Material.LEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("말 보관 목줄", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                gray("길들인 내 말에게 우클릭: 말을 목줄에 보관"),
                gray("보관된 목줄로 블록 우클릭: 말을 다시 꺼냄 (목줄 소모)"),
                Component.text("차원이동불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isLead(ItemStack item) {
        return item != null && item.hasItemMeta()
                && Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN));
    }

    public boolean isStored(ItemStack item) {
        return isLead(item) && item.getItemMeta().getPersistentDataContainer().has(speedKey, PersistentDataType.DOUBLE);
    }

    /** Packs the horse into a fresh single lead (so a stack of empty leads isn't overwritten)
     * and removes it from the world. Saddle/armor are dropped so nothing is lost. */
    public ItemStack store(Horse horse) {
        ItemStack item = createEmpty();
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        double speed = AnimalFeeding.horseSpeed(horse);
        double jump = horse.getJumpStrength();
        AttributeInstance maxHealth = horse.getAttribute(Attribute.MAX_HEALTH);
        double health = maxHealth == null ? 20 : maxHealth.getBaseValue();
        pdc.set(colorKey, PersistentDataType.STRING, horse.getColor().name());
        pdc.set(styleKey, PersistentDataType.STRING, horse.getStyle().name());
        pdc.set(speedKey, PersistentDataType.DOUBLE, speed);
        pdc.set(jumpKey, PersistentDataType.DOUBLE, jump);
        pdc.set(healthKey, PersistentDataType.DOUBLE, health);
        String name = horse.customName() == null ? "" : PlainTextComponentSerializer.plainText().serialize(horse.customName());
        pdc.set(nameKey, PersistentDataType.STRING, name);

        List<Component> lore = new ArrayList<>();
        lore.add(gray("보관된 말" + (name.isEmpty() ? "" : ": " + name)));
        lore.add(gray(String.format("속도 %.4f (%.0f%%)", speed, pct(speed, AnimalFeeding.HORSE_MIN_SPEED, AnimalFeeding.HORSE_MAX_SPEED))));
        lore.add(gray(String.format("점프력 %.2f (%.0f%%)", jump, pct(jump, AnimalFeeding.HORSE_MIN_JUMP, AnimalFeeding.HORSE_MAX_JUMP))));
        lore.add(gray(String.format("체력 %.0f", health)));
        lore.add(gray("블록 우클릭: 말 꺼내기 (목줄 소모)"));
        lore.add(Component.text("차원이동불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);

        Location at = horse.getLocation();
        for (ItemStack gear : horse.getInventory().getContents()) {
            if (gear != null && !gear.getType().isAir()) {
                at.getWorld().dropItemNaturally(at, gear);
            }
        }
        at.getWorld().spawnParticle(Particle.POOF, at.add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.02);
        at.getWorld().playSound(at, Sound.ENTITY_HORSE_SADDLE, 1f, 1f);
        horse.remove();
        return item;
    }

    /** Recreates the packed horse at `where`, tamed to `owner`. */
    public Horse release(ItemStack lead, Player owner, Location where) {
        PersistentDataContainer pdc = lead.getItemMeta().getPersistentDataContainer();
        Horse.Color color = parse(Horse.Color.class, pdc.get(colorKey, PersistentDataType.STRING), Horse.Color.BROWN);
        Horse.Style style = parse(Horse.Style.class, pdc.get(styleKey, PersistentDataType.STRING), Horse.Style.NONE);
        double speed = pdc.getOrDefault(speedKey, PersistentDataType.DOUBLE, AnimalFeeding.HORSE_MIN_SPEED);
        double jump = pdc.getOrDefault(jumpKey, PersistentDataType.DOUBLE, AnimalFeeding.HORSE_MIN_JUMP);
        double health = pdc.getOrDefault(healthKey, PersistentDataType.DOUBLE, 20.0);
        String name = pdc.getOrDefault(nameKey, PersistentDataType.STRING, "");

        Horse horse = where.getWorld().spawn(where, Horse.class, CreatureSpawnEvent.SpawnReason.CUSTOM, true, h -> {
            h.setColor(color);
            h.setStyle(style);
            AttributeInstance speedAttr = h.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.setBaseValue(speed);
            h.setJumpStrength(jump);
            AttributeInstance maxHealth = h.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealth != null) maxHealth.setBaseValue(health);
            h.setHealth(health);
            h.setTamed(true);
            h.setOwner(owner);
            h.setAdult();
            if (!name.isEmpty()) {
                h.customName(Component.text(name));
            }
        });
        where.getWorld().spawnParticle(Particle.POOF, where.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.02);
        where.getWorld().playSound(where, Sound.ENTITY_HORSE_SADDLE, 1f, 1f);
        return horse;
    }

    private static double pct(double value, double min, double max) {
        return Math.max(0, Math.min(100, (value - min) / (max - min) * 100));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback) {
        if (raw == null) return fallback;
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static Component gray(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
