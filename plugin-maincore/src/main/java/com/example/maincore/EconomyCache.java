package com.example.maincore;

import org.bukkit.Bukkit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory balance cache so frequent reads (e.g. the scoreboard) don't hit the DB.
 * The DB remains the source of truth; this is refreshed on every write and on join.
 */
public class EconomyCache {

    private final MainCorePlugin plugin;
    private final Map<UUID, Long> cache = new ConcurrentHashMap<>();

    public EconomyCache(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    public long get(UUID uuid) {
        return cache.getOrDefault(uuid, 0L);
    }

    public void refreshAsync(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance = plugin.getMainDatabase().getBalance(uuid);
            cache.put(uuid, balance);
        });
    }

    public void set(UUID uuid, long value) {
        cache.put(uuid, value);
    }

    public void remove(UUID uuid) {
        cache.remove(uuid);
    }
}
