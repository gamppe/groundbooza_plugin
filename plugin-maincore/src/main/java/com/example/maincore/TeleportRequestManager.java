package com.example.maincore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks the "confirm teleport to land?" prompt each player currently has pending, if any. */
public class TeleportRequestManager {

    private final Map<UUID, Integer> pendingLandId = new ConcurrentHashMap<>();

    public void request(UUID player, int landId) {
        pendingLandId.put(player, landId);
    }

    public Integer take(UUID player) {
        return pendingLandId.remove(player);
    }

    public void cancel(UUID player) {
        pendingLandId.remove(player);
    }
}
