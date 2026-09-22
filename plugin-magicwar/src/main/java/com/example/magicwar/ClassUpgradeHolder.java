package com.example.magicwar;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Same shape as MainCore's JobUpgradeHolder (6 rows): row 1 col 5 is the class card, rows 2/4/6
 * each carry one track - its icon in col 1 and MAX_LEVEL wool pips from col 3.
 */
public class ClassUpgradeHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int SLOT_CLASS = 4;
    public static final int PIP_START_COL = 2; // 0-based → 3rd column

    private Inventory inventory;

    /** Row (0-based) that track `index` occupies: 1, 3, 5. */
    private static int rowFor(int track) {
        return 1 + track * 2;
    }

    public static int iconSlot(int track) {
        return rowFor(track) * 9;
    }

    public static int pipSlot(int track, int pip) {
        return rowFor(track) * 9 + PIP_START_COL + pip;
    }

    /** Track index whose icon sits at `slot`, or -1. */
    public static int trackForIconSlot(int slot) {
        for (int t = 0; t < 3; t++) {
            if (iconSlot(t) == slot) {
                return t;
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
