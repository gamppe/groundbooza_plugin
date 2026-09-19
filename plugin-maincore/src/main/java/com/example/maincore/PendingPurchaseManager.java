package com.example.maincore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks the "buy this blank deed?" prompt each player currently has pending, if any. */
public class PendingPurchaseManager {

    public record Pending(String landName, long price) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public void request(UUID player, String landName, long price) {
        pending.put(player, new Pending(landName, price));
    }

    public Pending take(UUID player) {
        return pending.remove(player);
    }

    public void cancel(UUID player) {
        pending.remove(player);
    }
}
