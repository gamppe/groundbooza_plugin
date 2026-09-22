package com.example.serverbridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Farm-server copy of MainCore's 주인 없는 땅문서 - the same item (same maincore:wild_deed key), so
 * one fished up here is the real thing. Activating it needs the land system, which only exists
 * on main-server, so here a right-click just says where to take it. It is intentionally absent
 * from BoxListener's blocklist, which is what lets a player carry it across.
 */
public class WildDeedItem implements Listener {

    private static final NamespacedKey MARKER_KEY = new NamespacedKey("maincore", "wild_deed");
    public static final String LABEL = "주인 없는 땅문서";

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(LABEL, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.setEnchantmentGlintOverride(true);
        meta.lore(List.of(
                Component.text("누군가의 땅문서입니다. 활성화하면 오버월드의 무작위 비어있는 땅을 얻습니다.",
                        NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(MARKER_KEY, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isWildDeed(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return Boolean.TRUE.equals(
                item.getItemMeta().getPersistentDataContainer().get(MARKER_KEY, PersistentDataType.BOOLEAN));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!isWildDeed(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("이 문서는 메인 서버에서만 활성화할 수 있습니다.", NamedTextColor.RED));
    }
}
