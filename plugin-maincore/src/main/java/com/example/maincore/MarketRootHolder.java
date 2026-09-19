package com.example.maincore;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MarketRootHolder implements InventoryHolder {

    public static final int SIZE = 9;
    public static final int SLOT_REGISTER = 0;
    public static final int SLOT_LAND = 2;
    public static final int SLOT_ITEM = 6;
    public static final int SLOT_STATUS = 8;

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
