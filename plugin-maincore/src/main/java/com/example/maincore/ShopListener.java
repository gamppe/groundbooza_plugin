package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class ShopListener implements Listener {

    private final MainCorePlugin plugin;

    public ShopListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRootClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopRootHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        switch (event.getSlot()) {
            case ShopRootHolder.SLOT_BUY -> plugin.getShopController().openBuyMenu(player);
            case ShopRootHolder.SLOT_SPECIAL -> plugin.getShopController().openSpecialBuy(player, 0);
            case ShopRootHolder.SLOT_SELL -> {
                player.closeInventory();
                player.sendMessage(Component.text("아직 준비 중인 기능입니다.", NamedTextColor.GRAY));
            }
            default -> { /* border - nothing there */ }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBuyMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopBuyHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (slot == ShopBuyHolder.SLOT_BACK) {
            plugin.getShopController().openRoot(player);
            return;
        }
        ShopCategory category = ShopBuyHolder.categoryForSlot(slot);
        if (category != null) {
            plugin.getShopController().openCategory(player, category, 0);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrowseClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopBrowseHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (slot == ShopBrowseHolder.SLOT_BACK) {
            if (holder.getCategory() == null) {
                plugin.getShopController().openRoot(player);
            } else {
                plugin.getShopController().openBuyMenu(player);
            }
            return;
        }
        if (slot == ShopBrowseHolder.SLOT_PREV && holder.hasPrev()) {
            plugin.getShopController().openCategory(player, holder.getCategory(), holder.getPage() - 1);
            return;
        }
        if (slot == ShopBrowseHolder.SLOT_NEXT && holder.hasNext()) {
            plugin.getShopController().openCategory(player, holder.getCategory(), holder.getPage() + 1);
            return;
        }

        Integer index = holder.catalogIndexInSlot(slot);
        if (index == null) {
            return;
        }
        ShopItem item = plugin.getShopController().getItem(holder.getCategory(), index);
        if (item == null) {
            return;
        }
        if (plugin.getReservationDeedItem().isBlank(item.icon())) {
            plugin.getShopController().buyReservationDeed(player);
            return;
        }
        int quantity = event.isShiftClick() ? item.icon().getMaxStackSize() : 1;
        plugin.getShopController().buy(player, holder.getCategory(), index, quantity);
    }
}
