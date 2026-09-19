package com.example.maincore;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public class MarketBrowseHolder implements InventoryHolder {

    public static final int SIZE = 54; // large chest
    public static final int SLOT_PREV = 0;  // top-left corner
    public static final int SLOT_NEXT = 53; // bottom-right corner
    public static final int SLOT_BACK = 45; // bottom-left corner
    public static final int PAGE_SIZE = 28; // inner 4x7 grid

    private final MainDatabase.MarketCategory category;
    private final int page;
    private final boolean hasPrev;
    private final boolean hasNext;
    private final Map<Integer, MainDatabase.MarketListing> slotListings = new HashMap<>();
    private Inventory inventory;

    public MarketBrowseHolder(MainDatabase.MarketCategory category, int page, boolean hasPrev, boolean hasNext) {
        this.category = category;
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

    public void put(int slot, MainDatabase.MarketListing listing) {
        slotListings.put(slot, listing);
    }

    public MainDatabase.MarketListing listingInSlot(int slot) {
        return slotListings.get(slot);
    }

    public MainDatabase.MarketCategory getCategory() {
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
