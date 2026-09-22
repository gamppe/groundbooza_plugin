package com.example.magicwar;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who picked which class, and how far each of their upgrade lines has come.
 *
 * <p>Deliberately memory only and wiped between rounds: the event server keeps no database (see
 * ServerBridge's features switches), and a class is meant to last one match anyway.
 */
public class ClassManager {

    private record Pick(MagicClass magicClass, int[] levels) {}

    private final Map<UUID, Pick> picks = new ConcurrentHashMap<>();

    public MagicClass classOf(UUID uuid) {
        Pick pick = picks.get(uuid);
        return pick == null ? null : pick.magicClass();
    }

    public boolean hasClass(UUID uuid) {
        return picks.containsKey(uuid);
    }

    public void choose(UUID uuid, MagicClass magicClass) {
        picks.put(uuid, new Pick(magicClass, new int[3]));
    }

    public int levelOf(UUID uuid, int track) {
        Pick pick = picks.get(uuid);
        return pick == null || track < 0 || track > 2 ? 0 : pick.levels()[track];
    }

    public int totalLevels(UUID uuid) {
        Pick pick = picks.get(uuid);
        if (pick == null) {
            return 0;
        }
        int total = 0;
        for (int level : pick.levels()) {
            total += level;
        }
        return total;
    }

    /** @return the new level, or -1 when the track is maxed or not buyable yet. */
    public int upgrade(UUID uuid, int track) {
        Pick pick = picks.get(uuid);
        if (pick == null || track < 0 || track > 2) {
            return -1;
        }
        if (!pick.magicClass().track(track).available() || pick.levels()[track] >= ClassTrack.MAX_LEVEL) {
            return -1;
        }
        return ++pick.levels()[track];
    }

    public void clear() {
        picks.clear();
    }
}
