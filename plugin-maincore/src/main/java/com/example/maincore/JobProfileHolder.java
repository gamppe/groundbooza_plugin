package com.example.maincore;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** /직업 once a job is chosen: the job card, the upgrade button, the job's own action button
 * (경험치→뼛가루 / 원광→주괴), a shortcut into the job's special shop, and the change-job button. */
public class JobProfileHolder implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int SLOT_JOB = 4;
    public static final int SLOT_UPGRADE = 11;
    public static final int SLOT_ACTION = 13;
    public static final int SLOT_SHOP = 15;
    public static final int SLOT_CHANGE = 22;

    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
