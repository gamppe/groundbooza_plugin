package com.example.maincore;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** 상점 > 구매 > 테라포밍: one icon per TerraformController.Op across the middle row. */
public class TerraformHolder implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int SLOT_BACK = 18;
    private static final int FIRST_SLOT = 10; // middle row, second column

    private Inventory inventory;

    public static int slotFor(TerraformController.Op op) {
        return FIRST_SLOT + op.ordinal();
    }

    public static TerraformController.Op opForSlot(int slot) {
        for (TerraformController.Op op : TerraformController.Op.values()) {
            if (slotFor(op) == slot) return op;
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
