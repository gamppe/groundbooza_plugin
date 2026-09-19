package com.example.maincore;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

/**
 * A deed is only meaningful to its current rightful owner (checked live against the land's
 * DB record, not anything baked into the item). Anyone can pick up a deed whose land no
 * longer exists or belongs to someone else - it's just scrap paper by that point. Blank/unclaimed
 * documents (normal or 토지선점권) are never pickup-restricted either - using one you're not
 * entitled to just fails outright at claim time instead (see LandBuyListener).
 */
public class DeedPickupListener implements Listener {

    private final MainCorePlugin plugin;

    public DeedPickupListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Item itemEntity = event.getItem();
        Integer landId = plugin.getDeedItem().getLandId(itemEntity.getItemStack());
        if (landId == null) {
            return;
        }
        MainDatabase.Land land = plugin.getLandManager().getLandById(landId);
        if (land == null) {
            return; // scrap paper - anyone can pick it up
        }
        if (!land.owner().equals(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
