package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class DeedBookListener implements Listener {

    private final MainCorePlugin plugin;

    public DeedBookListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof DeedBookHolder holder)) {
            return;
        }
        // Purely a browsing list - nothing can ever be taken out or put in directly.
        event.setCancelled(true);

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        MainDatabase.Land land = holder.landInSlot(event.getSlot());
        if (land != null) {
            if (land.reservation()) {
                player.getInventory().addItem(plugin.getReservationDeedItem().createClaimed(land));
            } else {
                player.getInventory().addItem(plugin.getDeedItem().create(land, player.getName()));
            }
            player.sendMessage(Component.text(land.name() + " 땅문서 사본을 받았습니다.", NamedTextColor.GREEN));
            return;
        }

        MainDatabase.PendingDeed deed = holder.pendingDeedInSlot(event.getSlot());
        if (deed == null) {
            return;
        }
        if (deed.reservation()) {
            player.getInventory().addItem(plugin.getReservationDeedItem().createBlank(deed));
            player.sendMessage(Component.text("빈 토지선점권 문서 사본을 받았습니다.", NamedTextColor.GREEN));
        } else {
            player.getInventory().addItem(plugin.getPendingDeedItem().create(deed));
            player.sendMessage(Component.text("빈 땅문서 '" + deed.name() + "' 사본을 받았습니다.", NamedTextColor.GREEN));
        }
    }
}
