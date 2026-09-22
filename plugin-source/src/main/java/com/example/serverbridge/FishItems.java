package com.example.serverbridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Farm-server copy of MainCore's FishItems: the made-up fish 어부's 수제미끼낚시대 can pull up -
 * a vanilla item wearing a custom name, an enchantment glint and a `fish_kind` tag. Kinds
 * flagged `inert` are display/sale goods only - they can't be placed, thrown, poured or
 * dispensed (a tadpole bucket that never becomes a tadpole, an ender eye that never flies).
 * Edible ones stay edible. */
public class FishItems implements Listener {

    public enum Kind {
        SMELT("빙어", Material.COD, false),
        OCTOPUS_INK("문어 먹물", Material.INK_SAC, true),
        SHEEP_FISH("양물고기", Material.MUTTON, false),
        RABBIT_FISH("토끼물고기", Material.RABBIT, false),
        CREEPER_FISH("크리퍼물고기", Material.CREEPER_HEAD, true),
        TADPOLE("올챙이", Material.TADPOLE_BUCKET, true),
        ODD_RESIN("특이한 수지", Material.RESIN_CLUMP, true),
        CREAKING_EYE("크리킹의 눈", Material.ENDER_EYE, true),
        LAVA_TROPICAL("용암 열대어", Material.TROPICAL_FISH, false),
        LAVA_PUFFERFISH("용암복어", Material.PUFFERFISH, false),
        VOID_PUFFERFISH("공허복어", Material.PUFFERFISH, false),
        VOID_SALMON("공허의 연어", Material.COOKED_SALMON, false);

        public final String label;
        public final Material material;
        /** True = no placing / right-click use, ever. */
        public final boolean inert;

        Kind(String label, Material material, boolean inert) {
            this.label = label;
            this.material = material;
            this.inert = inert;
        }
    }

    /** Same key MainCore stamps, so a fish caught on either server reads the same everywhere. */
    private final NamespacedKey kindKey = new NamespacedKey("maincore", "fish_kind");

    public ItemStack create(Kind kind) {
        return create(kind, 1);
    }

    public ItemStack create(Kind kind, int amount) {
        ItemStack item = new ItemStack(kind.material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(kind.label, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        meta.lore(List.of(Component.text("수제미끼낚시대로 낚은 희귀한 물고기", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind.name());
        item.setItemMeta(meta);
        return item;
    }

    public Kind kindOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return Kind.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isInert(ItemStack item) {
        Kind kind = kindOf(item);
        return kind != null && kind.inert;
    }

    // ---------- keep inert kinds inert ----------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isInert(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (isInert(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isInert(event.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (isInert(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
