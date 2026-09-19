package com.example.maincore;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** /직업 with no job yet (or the "change job" screen from the profile): one icon per job in
 * the middle row. `changing` flags the paid switch flow vs. the free first pick. */
public class JobSelectHolder implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int SLOT_BACK = 18;

    private final boolean changing;
    private Inventory inventory;

    public JobSelectHolder(boolean changing) {
        this.changing = changing;
    }

    public static int slotFor(Job job) {
        return 11 + job.ordinal(); // 11..15, centered on the middle row
    }

    public static Job jobForSlot(int slot) {
        for (Job job : Job.values()) {
            if (slotFor(job) == slot) return job;
        }
        return null;
    }

    public boolean isChanging() {
        return changing;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
