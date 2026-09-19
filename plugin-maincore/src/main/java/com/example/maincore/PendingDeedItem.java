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

/** The "blank" deed you get from /땅 구매 <이름> before you've actually claimed a cell with it. */
public class PendingDeedItem {

    private final NamespacedKey nameKey;
    private final NamespacedKey markerKey;
    private final NamespacedKey pendingIdKey;

    public PendingDeedItem(MainCorePlugin plugin) {
        this.nameKey = new NamespacedKey(plugin, "pending_land_name");
        this.markerKey = new NamespacedKey(plugin, "pending_deed");
        this.pendingIdKey = new NamespacedKey(plugin, "pending_deed_id");
    }

    /** Physical copy of a DB-backed pending deed - just a printout, not the entitlement itself.
     * Losing/dropping/handing it off doesn't matter: /땅 문서 can always reprint one from the
     * DB row, and only the row's owner can actually claim land with it. */
    public ItemStack create(MainDatabase.PendingDeed deed) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("빈 땅문서: " + deed.name(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("우클릭한 위치의 구역을 구매합니다.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("거래불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("차원이동불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));

        meta.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, deed.name());
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(pendingIdKey, PersistentDataType.INTEGER, deed.id());

        item.setItemMeta(meta);
        return item;
    }

    public boolean isPending(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }

    /** Which pending_deeds DB row this physical copy points to - the actual source of truth. */
    public Integer getPendingDeedId(ItemStack item) {
        if (!isPending(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(pendingIdKey, PersistentDataType.INTEGER);
    }
}
