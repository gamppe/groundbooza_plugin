package com.example.maincore;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ShopBuyHolder implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int SLOT_BACK = 18; // bottom-left corner

    private Inventory inventory;

    public static int slotFor(ShopCategory category) {
        return switch (category) {
            case NECESSITIES -> 2;
            case DYE -> 4;
            case DYEABLE_BLOCK -> 6;
            case WOOD_STONE -> 20;
            case DECORATION -> 22;
            case TOOL_RENTAL -> 24;
        };
    }

    public static ShopCategory categoryForSlot(int slot) {
        for (ShopCategory category : ShopCategory.values()) {
            if (slotFor(category) == slot) {
                return category;
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
