package com.example.magicwar;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * The class screen: the skill list in the top-left corner and the class icon on the top row, then the quest board - two quests on
 * row 2 (cols 4 and 6) and three on row 4 (cols 3, 5 and 7), each with its claim wool directly
 * underneath. The anvil in the bottom-right corner is 전직.
 */
public class ClassUpgradeHolder implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int SLOT_SKILLS = 0;
    public static final int SLOT_CLASS = 4;
    public static final int SLOT_ADVANCE = 53;

    /** Icon slots in board order, matching QuestManager.BOARD. */
    private static final int[] QUEST_SLOTS = {12, 14, 29, 31, 33};

    private Inventory inventory;

    public static int questSlot(int index) {
        return QUEST_SLOTS[index];
    }

    /** The claim wool sits one row below its quest icon. */
    public static int woolSlot(int index) {
        return QUEST_SLOTS[index] + 9;
    }

    public static int questForSlot(int slot) {
        for (int i = 0; i < QUEST_SLOTS.length; i++) {
            if (QUEST_SLOTS[i] == slot || woolSlot(i) == slot) {
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
