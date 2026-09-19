package com.example.maincore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks the "really give up this land?" prompt each player currently has pending, if any. */
public class AbandonRequestManager {

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
