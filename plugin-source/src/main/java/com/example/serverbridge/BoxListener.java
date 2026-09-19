package com.example.serverbridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class BoxListener implements Listener {

    // MainCore tags the main-server-only WorldEdit axe, its 10-minute terraforming brush, and
    // every kind of land deed (land only exists on main-server, so none of these belong over
    // here) with these keys. Read literally by namespace/key rather than depending on the MainCore
    // plugin, since the two plugins are otherwise unrelated.
    private static final NamespacedKey BRUSH_MARKER_KEY = new NamespacedKey("maincore", "terraform_brush");
    private static final NamespacedKey HORSE_LEAD_KEY = new NamespacedKey("maincore", "horse_lead");
    private static final NamespacedKey AXE_MARKER_KEY = new NamespacedKey("maincore", "special_axe");
    private static final NamespacedKey LAND_DEED_KEY = new NamespacedKey("maincore", "land_deed");
    private static final NamespacedKey PENDING_DEED_KEY = new NamespacedKey("maincore", "pending_deed");
    private static final NamespacedKey RESERVATION_BLANK_KEY = new NamespacedKey("maincore", "reservation_blank");
    private static final NamespacedKey RESERVATION_CLAIMED_KEY = new NamespacedKey("maincore", "reservation_claimed");

    private final ServerBridgePlugin plugin;

    public BoxListener(ServerBridgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BoxInventoryHolder holder)) {
            return;
        }

        ItemStack[] snapshot = event.getInventory().getContents().clone();

        // DB access must not happen on the main thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getDatabaseManager().saveBoxContents(holder.getOwnerUuid(), snapshot));
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BoxInventoryHolder)) {
            return;
        }
        boolean placingIntoBox = event.getClickedInventory() == event.getView().getTopInventory();
        ItemStack involved = placingIntoBox ? event.getCursor() : event.getCurrentItem();
        boolean shiftMovingIntoBox = event.isShiftClick() && !placingIntoBox;
        if ((placingIntoBox || shiftMovingIntoBox) && isBlockedFromBox(involved)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(Component.text("이 아이템은 상자에 넣을 수 없습니다.", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BoxInventoryHolder)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean intoTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (intoTop && isBlockedFromBox(event.getOldCursor())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(Component.text("이 아이템은 상자에 넣을 수 없습니다.", NamedTextColor.RED));
            }
        }
    }

    private boolean isBlockedFromBox(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return Boolean.TRUE.equals(pdc.get(BRUSH_MARKER_KEY, PersistentDataType.BOOLEAN))
                || Boolean.TRUE.equals(pdc.get(HORSE_LEAD_KEY, PersistentDataType.BOOLEAN))
                || Boolean.TRUE.equals(pdc.get(AXE_MARKER_KEY, PersistentDataType.BOOLEAN))
                || Boolean.TRUE.equals(pdc.get(LAND_DEED_KEY, PersistentDataType.BOOLEAN))
                || Boolean.TRUE.equals(pdc.get(PENDING_DEED_KEY, PersistentDataType.BOOLEAN))
                || Boolean.TRUE.equals(pdc.get(RESERVATION_BLANK_KEY, PersistentDataType.BOOLEAN))
                || Boolean.TRUE.equals(pdc.get(RESERVATION_CLAIMED_KEY, PersistentDataType.BOOLEAN));
    }
}
