package com.example.maincore;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Rental/special tools are locked to whoever bought them - unlike documents, which stay
 * pickupable by anyone but simply fail to work for the wrong person, a dropped tool can't even
 * be picked up by someone else. */
public class ToolPickupListener implements Listener {

    private final MainCorePlugin plugin;

    public ToolPickupListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack item = event.getItem().getItemStack();
        if (!plugin.isOwnerLockedTool(item)) {
            return;
        }
        UUID owner = plugin.getToolOwner(item);
        if (owner != null && !owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
