package com.example.magicwar;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** The skill list behind the paper icon: every skill the player has earned so far, laid out
 * along the middle row. */
public class SkillListHolder implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int SLOT_BACK = 18;

    private Inventory inventory;

    /** Middle row, left to right. */
    public static int slotFor(int index) {
        return 10 + index;
    }

    public static int indexForSlot(int slot, int total) {
        int index = slot - 10;
        return index >= 0 && index < total ? index : -1;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
