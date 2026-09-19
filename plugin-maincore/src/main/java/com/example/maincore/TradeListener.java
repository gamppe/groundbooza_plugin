package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class TradeListener implements Listener {

    private final MainCorePlugin plugin;

    public TradeListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeOwnHolder holder)) {
            return;
        }

        if (!event.getWhoClicked().getUniqueId().equals(holder.getOwner())) {
            // Someone viewing the other party's staging inventory via /거래 품목확인 - read only.
            // Blocking every click here (not just clicks on the top inventory itself) is what
            // stops a shift-click from their own inventory sneaking an item into the other
            // person's box.
            event.setCancelled(true);
            return;
        }

        boolean placingIntoBox = event.getClickedInventory() == event.getView().getTopInventory();
        ItemStack involved = placingIntoBox ? event.getCursor() : event.getCurrentItem();
        boolean shiftMovingIntoBox = event.isShiftClick() && !placingIntoBox;
        if ((placingIntoBox || shiftMovingIntoBox) && plugin.isUntradeable(involved)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(Component.text("거래 불가능한 아이템입니다.", NamedTextColor.RED));
            }
            return;
        }

        if (placingIntoBox) {
            onOwnInventoryChanged(holder.getSession());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TradeOwnHolder holder)) {
            return;
        }
        if (!event.getWhoClicked().getUniqueId().equals(holder.getOwner())) {
            event.setCancelled(true);
            return;
        }
        if (plugin.isUntradeable(event.getOldCursor())) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(Component.text("거래 불가능한 아이템입니다.", NamedTextColor.RED));
            }
            return;
        }
        onOwnInventoryChanged(holder.getSession());
    }

    private void onOwnInventoryChanged(TradeSession session) {
        if (!session.acceptedA && !session.acceptedB) {
            return;
        }
        session.resetAccepted();
        Component msg = Component.text("상대방이 아이템을 변경하여 수락이 취소되었습니다. 다시 확인해주세요.", NamedTextColor.YELLOW);
        Player a = Bukkit.getPlayer(session.playerA);
        Player b = Bukkit.getPlayer(session.playerB);
        if (a != null) a.sendMessage(msg);
        if (b != null) b.sendMessage(msg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        TradeSession session = plugin.getTradeManager().getSession(event.getPlayer().getUniqueId());
        if (session != null) {
            plugin.getTradeManager().cancelSession(session);
        }
    }
}
