package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class MarketListener implements Listener {

    private final MainCorePlugin plugin;

    public MarketListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRootClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MarketRootHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        switch (event.getSlot()) {
            case MarketRootHolder.SLOT_REGISTER -> {
                player.closeInventory();
                player.sendMessage(Component.text(
                        "거래에 등록할 아이템을 주 손에 든 채로 /거래 등록 <가격> \"메모\" 를 입력해주세요.", NamedTextColor.AQUA));
            }
            case MarketRootHolder.SLOT_LAND -> plugin.getMarketController()
                    .openBrowse(player, MainDatabase.MarketCategory.LAND, 0);
            case MarketRootHolder.SLOT_ITEM -> plugin.getMarketController()
                    .openBrowse(player, MainDatabase.MarketCategory.ITEM, 0);
            case MarketRootHolder.SLOT_STATUS -> plugin.getMarketController().openTransactionStatus(player);
            default -> { /* border - nothing there */ }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStatusClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MarketStatusHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (event.getSlot() == MarketStatusHolder.SLOT_BACK) {
            player.closeInventory();
            plugin.getMarketController().openRoot(player);
            return;
        }

        MainDatabase.MarketListing listing = holder.listingInStatusSlot(event.getSlot());
        if (listing == null || listing.status() == MainDatabase.MarketStatus.ACTIVE) {
            return; // either not a status slot, or still pending - no interaction
        }

        if (listing.status() == MainDatabase.MarketStatus.SOLD) {
            player.closeInventory();
            plugin.getMarketController().claimSale(player, listing);
        } else if (listing.status() == MainDatabase.MarketStatus.EXPIRED) {
            player.closeInventory();
            if (event.isRightClick()) {
                plugin.getMarketController().reregisterExpired(player, listing);
            } else {
                plugin.getMarketController().collectExpired(player, listing);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBrowseClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MarketBrowseHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (slot == MarketBrowseHolder.SLOT_BACK) {
            player.closeInventory();
            plugin.getMarketController().openRoot(player);
            return;
        }
        if (slot == MarketBrowseHolder.SLOT_PREV && holder.hasPrev()) {
            plugin.getMarketController().openBrowse(player, holder.getCategory(), holder.getPage() - 1);
            return;
        }
        if (slot == MarketBrowseHolder.SLOT_NEXT && holder.hasNext()) {
            plugin.getMarketController().openBrowse(player, holder.getCategory(), holder.getPage() + 1);
            return;
        }

        MainDatabase.MarketListing listing = holder.listingInSlot(slot);
        if (listing == null) {
            return;
        }

        if (event.isRightClick() && listing.category() == MainDatabase.MarketCategory.LAND) {
            player.closeInventory();
            plugin.getMarketController().promptTeleport(player, listing.landId());
        } else if (event.isLeftClick()) {
            player.closeInventory();
            plugin.getMarketController().promptPurchase(player, listing);
        }
    }
}
