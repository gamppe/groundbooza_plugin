package com.example.maincore;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class ShopBrowseHolder implements InventoryHolder {

    public static final int SIZE = 54; // large chest
    public static final int SLOT_PREV = 0;  // top-left corner
    public static final int SLOT_NEXT = 53; // bottom-right corner
    public static final int SLOT_BACK = 45; // bottom-left corner
    public static final int PAGE_SIZE = 28; // inner 4x7 grid

    private final ShopCategory category;
    /** Only set for 특수구매 (category == null): whose job list this page was built from. */
    private final Job job;
    private final int page;
    private final boolean hasPrev;
    private final boolean hasNext;
    private final Map<Integer, Integer> slotIndex = new HashMap<>();
    private Inventory inventory;

    public ShopBrowseHolder(ShopCategory category, Job job, int page, boolean hasPrev, boolean hasNext) {
        this.category = category;
        this.job = job;
        this.page = page;
        this.hasPrev = hasPrev;
        this.hasNext = hasNext;
    }

    public static int[] innerSlots() {
        int[] slots = new int[PAGE_SIZE];
        int i = 0;
        for (int r = 1; r <= 4; r++) {
            for (int c = 1; c <= 7; c++) {
                slots[i++] = r * 9 + c;
            }
        }
        return slots;
    }

    public void put(int slot, int catalogIndex) {
        slotIndex.put(slot, catalogIndex);
    }

    public Integer catalogIndexInSlot(int slot) {
        return slotIndex.get(slot);
    }

    public Job getJob() {
        return job;
    }

    public ShopCategory getCategory() {
        return category;
    }

    public int getPage() {
        return page;
    }

    public boolean hasPrev() {
        return hasPrev;
    }

    public boolean hasNext() {
        return hasNext;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
