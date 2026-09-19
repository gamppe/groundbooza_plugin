package com.example.maincore;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TradeManager {

    private final Map<UUID, TradeSession> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> blocked = ConcurrentHashMap.newKeySet();
    // Items that couldn't be delivered immediately (recipient offline, or inventory was full).
    // Held here instead of ever being lost, and handed over next time they run /거래.
    private final Map<UUID, List<ItemStack>> mailbox = new ConcurrentHashMap<>();

    public TradeSession getSession(UUID player) {
        return sessions.get(player);
    }

    public boolean isBlocked(UUID player) {
        return blocked.contains(player);
    }

    public boolean toggleBlocked(UUID player) {
        if (blocked.contains(player)) {
            blocked.remove(player);
            return false;
        }
        blocked.add(player);
        return true;
    }

    public TradeSession startSession(UUID a, UUID b) {
        TradeSession session = new TradeSession(a, b);
        sessions.put(a, session);
        sessions.put(b, session);
        return session;
    }

    /** Cancels the given player's active session (if any), returning staged items to both parties. */
    public void cancelSession(TradeSession session) {
        sessions.remove(session.playerA);
        sessions.remove(session.playerB);
        deliver(session.playerA, session.invA);
        deliver(session.playerB, session.invB);
    }

    /** Both sides accepted: swap contents and clean up. */
    public void completeSession(TradeSession session) {
        sessions.remove(session.playerA);
        sessions.remove(session.playerB);
        // A receives what was staged in invB, and vice versa.
        deliver(session.playerA, session.invB);
        deliver(session.playerB, session.invA);
    }

    private void deliver(UUID uuid, Inventory staged) {
        Player player = Bukkit.getPlayer(uuid);
        for (ItemStack item : staged.getContents()) {
            if (item == null || item.getType().isAir()) continue;
            if (player != null && player.isOnline()) {
                player.getInventory().addItem(item).values().forEach(leftover -> stash(uuid, leftover));
            } else {
                stash(uuid, item);
            }
        }
        staged.clear();
    }

    private void stash(UUID uuid, ItemStack item) {
        mailbox.computeIfAbsent(uuid, k -> new ArrayList<>()).add(item);
    }

    /** Hands over anything waiting for this player, putting back what doesn't fit. Returns how many stacks were delivered. */
    public int collectMailbox(Player player) {
        List<ItemStack> waiting = mailbox.remove(player.getUniqueId());
        if (waiting == null || waiting.isEmpty()) {
            return 0;
        }
        int delivered = 0;
        List<ItemStack> stillWaiting = new ArrayList<>();
        for (ItemStack item : waiting) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (leftover.isEmpty()) {
                delivered++;
            } else {
                stillWaiting.addAll(leftover.values());
            }
        }
        if (!stillWaiting.isEmpty()) {
            mailbox.put(player.getUniqueId(), stillWaiting);
        }
        return delivered;
    }

    public boolean hasMail(UUID uuid) {
        List<ItemStack> waiting = mailbox.get(uuid);
        return waiting != null && !waiting.isEmpty();
    }
}
