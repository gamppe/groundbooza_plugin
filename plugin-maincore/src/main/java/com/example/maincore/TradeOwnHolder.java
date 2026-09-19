package com.example.maincore;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Marks an editable trade staging inventory belonging to `owner` within `session`. */
public class TradeOwnHolder implements InventoryHolder {

    private final TradeSession session;
    private final UUID owner;

    public TradeOwnHolder(TradeSession session, UUID owner) {
        this.session = session;
        this.owner = owner;
    }

    @Override
    public Inventory getInventory() {
        return session.ownInventory(owner);
    }

    public TradeSession getSession() {
        return session;
    }

    public UUID getOwner() {
        return owner;
    }
}
