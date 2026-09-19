package com.example.maincore;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ShopRootHolder implements InventoryHolder {

    public static final int SIZE = 9;
    public static final int SLOT_BUY = 2;
    public static final int SLOT_SPECIAL = 4;
    public static final int SLOT_SELL = 6;

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
