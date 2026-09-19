package com.example.serverbridge;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * Marker holder so BoxListener can recognize our shared-box GUI
 * (as opposed to any other inventory the player might have open)
 * and know whose box it is.
 */
public class BoxInventoryHolder implements InventoryHolder {

    private final UUID ownerUuid;
    private Inventory inventory;

    public BoxInventoryHolder(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }
}
