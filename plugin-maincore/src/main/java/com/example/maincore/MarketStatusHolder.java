package com.example.maincore;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * 거래현황: up to MAX_LISTINGS_PER_PLAYER (5) of the seller's listings are shown as a single
 * centered row in the middle of the double chest, with each item's status (pending/claimable)
 * directly below it.
 */
public class MarketStatusHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int SLOT_BACK = 45; // bottom-left corner
    public static final int[] ITEM_SLOTS = {20, 21, 22, 23, 24};
    public static final int[] STATUS_SLOTS = {29, 30, 31, 32, 33};

    private final Map<Integer, MainDatabase.MarketListing> statusSlotListings = new HashMap<>();
    private Inventory inventory;

    public void put(int statusSlot, MainDatabase.MarketListing listing) {
        statusSlotListings.put(statusSlot, listing);
    }

    public MainDatabase.MarketListing listingInStatusSlot(int slot) {
        return statusSlotListings.get(slot);
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
