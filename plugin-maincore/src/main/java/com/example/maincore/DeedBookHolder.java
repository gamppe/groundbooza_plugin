package com.example.maincore;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;

/**
 * /땅 문서 is now a read-only *list* of every land the player owns (one chest's worth of
 * slots), not a storage box. Left-clicking an entry hands them a fresh physical copy of that
 * land's deed; the listing itself never changes as a result. Claimed lands fill the first
 * slots, followed by still-unclaimed pending deeds (blank land/reservation deeds already paid
 * for) - the physical paper is disposable, so this DB-backed listing is the real record of what
 * the player owns.
 */
public class DeedBookHolder implements InventoryHolder {

    public static final int SIZE = 27; // one chest

    private final UUID ownerUuid;
    private final List<MainDatabase.Land> lands;
    private final List<MainDatabase.PendingDeed> pendingDeeds;
    private Inventory inventory;

    public DeedBookHolder(UUID ownerUuid, List<MainDatabase.Land> lands, List<MainDatabase.PendingDeed> pendingDeeds) {
        this.ownerUuid = ownerUuid;
        this.lands = lands;
        this.pendingDeeds = pendingDeeds;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public MainDatabase.Land landInSlot(int slot) {
        return slot >= 0 && slot < lands.size() ? lands.get(slot) : null;
    }

    public MainDatabase.PendingDeed pendingDeedInSlot(int slot) {
        int offset = slot - lands.size();
        return offset >= 0 && offset < pendingDeeds.size() ? pendingDeeds.get(offset) : null;
    }
}
