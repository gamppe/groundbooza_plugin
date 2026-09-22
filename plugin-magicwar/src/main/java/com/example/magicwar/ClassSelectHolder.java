package com.example.magicwar;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Class picker: one icon per class across the middle row, mirroring MainCore's JobSelectHolder. */
public class ClassSelectHolder implements InventoryHolder {

    public static final int SIZE = 27;

    private Inventory inventory;

    public static int slotFor(MagicClass magicClass) {
        return 12 + magicClass.ordinal(); // 12..14, centred on the middle row
    }

    public static MagicClass classForSlot(int slot) {
        for (MagicClass magicClass : MagicClass.values()) {
            if (slotFor(magicClass) == slot) {
                return magicClass;
            }
        }
        return null;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
