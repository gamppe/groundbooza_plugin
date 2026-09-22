package com.example.magicwar;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Class picker: one icon per option across the middle row with a blank slot between each,
 * mirroring MainCore's JobSelectHolder. Doubles as the 전직 screen, where the options are the
 * player's tier-2 classes instead of the three starting ones. */
public class ClassSelectHolder implements InventoryHolder {

    public static final int SIZE = 27;

    /** True when this is the anvil's 전직 screen rather than the opening pick. */
    private final boolean advancing;
    private Inventory inventory;

    public ClassSelectHolder(boolean advancing) {
        this.advancing = advancing;
    }

    public boolean isAdvancing() {
        return advancing;
    }

    /** Spread across the middle row, one empty slot between each: 11, 13, 15 for three options,
     * 12, 14 for two. */
    public static int slotFor(int index, int total) {
        int span = total * 2 - 1;
        int start = 13 - span / 2;
        return start + index * 2;
    }

    /** Which option sits at `slot`, or -1. */
    public static int indexForSlot(int slot, int total) {
        for (int i = 0; i < total; i++) {
            if (slotFor(i, total) == slot) {
                return i;
            }
        }
        return -1;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
