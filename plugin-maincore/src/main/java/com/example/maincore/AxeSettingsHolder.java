package com.example.maincore;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marker holder for the WorldEdit axe's settings panel (opened by crouch + right-click). */
public class AxeSettingsHolder implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int SLOT_FILL_MODE = 11;      // row 2, col 3
    public static final int SLOT_PLACEMENT_MODE = 13; // row 2, col 5
    public static final int SLOT_UNDO = 15;            // row 2, col 7

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
