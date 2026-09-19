package com.example.maincore;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of the land grid, backed by MainDatabase.
 * Block break/place and movement checks are frequent, so we avoid hitting
 * the DB synchronously for every one of them.
 */
public class LandManager {

    private final MainCorePlugin plugin;
    private final MainDatabase db;
    private final Map<Long, MainDatabase.Land> cellCache = new ConcurrentHashMap<>();
    private final Map<Integer, MainDatabase.PendingDeed> pendingDeedCache = new ConcurrentHashMap<>();

    public LandManager(MainCorePlugin plugin, MainDatabase db) {
        this.plugin = plugin;
        this.db = db;
    }

    private static long key(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xFFFFFFFFL);
    }

    /** Blocking - call during startup only. */
    public void loadAll() {
        for (MainDatabase.Land land : db.getAllLandsBlocking()) {
            cellCache.put(key(land.cellX(), land.cellZ()), land);
        }
        plugin.getLogger().info("Loaded " + cellCache.size() + " claimed land cells.");
        for (MainDatabase.PendingDeed deed : db.getAllPendingDeedsBlocking()) {
            pendingDeedCache.put(deed.id(), deed);
        }
        plugin.getLogger().info("Loaded " + pendingDeedCache.size() + " outstanding pending deeds.");
    }

    public MainDatabase.Land getLandAt(Location loc) {
        return cellCache.get(key(LandGrid.cellX(loc), LandGrid.cellZ(loc)));
    }

    public MainDatabase.Land getLandAt(int cellX, int cellZ) {
        return cellCache.get(key(cellX, cellZ));
    }

    /** Cache lookup only - safe to call on the main thread every tick. */
    public MainDatabase.Land getLandById(int id) {
        for (MainDatabase.Land land : cellCache.values()) {
            if (land.id() == id) return land;
        }
        return null;
    }

    public boolean isOwner(Location loc, UUID player) {
        MainDatabase.Land land = getLandAt(loc);
        return land != null && land.owner().equals(player);
    }

    public boolean canBuild(Location loc, UUID player) {
        MainDatabase.Land land = getLandAt(loc);
        return land == null || land.owner().equals(player);
    }

    /** True if `viewer` is the effective controller of this land for /땅 문서 purposes: the real
     * owner for a normal land, or the holder for a still-unsold 토지선점권. */
    private boolean controlledBy(MainDatabase.Land land, UUID viewer) {
        return land.reservation() ? viewer.equals(land.holder()) : land.owner().equals(viewer);
    }

    public int countOwned(UUID owner) {
        int count = 0;
        for (MainDatabase.Land land : cellCache.values()) {
            if (controlledBy(land, owner)) count++;
        }
        return count;
    }

    public List<MainDatabase.Land> getOwned(UUID owner) {
        return cellCache.values().stream().filter(l -> controlledBy(l, owner)).toList();
    }

    /** Async purchase: does the DB write off-thread, then updates the cache and calls back on the main thread. */
    public void claimAsync(UUID owner, String name, int cellX, int cellZ, java.util.function.Consumer<MainDatabase.Land> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int id = db.claimLand(owner, name, cellX, cellZ);
            MainDatabase.Land result = id == -1 ? null : new MainDatabase.Land(id, owner, name, cellX, cellZ, false, null, null);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result != null) {
                    cellCache.put(key(cellX, cellZ), result);
                }
                callback.accept(result);
            });
        });
    }

    /** Async 토지선점권 claim: owned by nobody (RESERVATION_OWNER), with its own expiry. `holder`
     * is who claimed it - it shows in their /땅 문서 and only they can trade/list it. */
    public void claimReservationAsync(UUID holder, String name, int cellX, int cellZ, long expiresAtMillis,
                                       java.util.function.Consumer<MainDatabase.Land> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int id = db.claimReservationLand(holder, name, cellX, cellZ, expiresAtMillis);
            MainDatabase.Land result = id == -1 ? null
                    : new MainDatabase.Land(id, MainDatabase.RESERVATION_OWNER, name, cellX, cellZ, true, expiresAtMillis, holder);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result != null) {
                    cellCache.put(key(cellX, cellZ), result);
                }
                callback.accept(result);
            });
        });
    }

    /** Async ownership transfer (trades). Updates the cache immediately after the DB write succeeds. */
    public void transferOwnershipAsync(int landId, UUID newOwner, Runnable onDone) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            db.updateLandOwner(landId, newOwner);
            Bukkit.getScheduler().runTask(plugin, () -> {
                MainDatabase.Land old = getLandById(landId);
                if (old != null) {
                    cellCache.put(key(old.cellX(), old.cellZ()), new MainDatabase.Land(
                            old.id(), newOwner, old.name(), old.cellX(), old.cellZ(),
                            old.reservation(), old.reservationExpiresAt(), old.holder()));
                }
                if (onDone != null) onDone.run();
            });
        });
    }

    /** Async 토지선점권 -> real deed conversion (a completed sale). Clears the reservation flag,
     * expiry and holder, and hands real ownership to whoever just bought/received it. */
    public void convertReservationToNormalAsync(int landId, UUID newOwner, Runnable onDone) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            db.convertReservationToNormal(landId, newOwner);
            Bukkit.getScheduler().runTask(plugin, () -> {
                MainDatabase.Land old = getLandById(landId);
                if (old != null) {
                    cellCache.put(key(old.cellX(), old.cellZ()), new MainDatabase.Land(
                            old.id(), newOwner, old.name(), old.cellX(), old.cellZ(), false, null, null));
                }
                if (onDone != null) onDone.run();
            });
        });
    }

    /** Async rename (/땅 이름변경). Updates the cache immediately after the DB write succeeds. */
    public void renameAsync(int landId, String newName, Runnable onDone) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            db.updateLandName(landId, newName);
            Bukkit.getScheduler().runTask(plugin, () -> {
                MainDatabase.Land old = getLandById(landId);
                if (old != null) {
                    cellCache.put(key(old.cellX(), old.cellZ()), new MainDatabase.Land(
                            old.id(), old.owner(), newName, old.cellX(), old.cellZ(),
                            old.reservation(), old.reservationExpiresAt(), old.holder()));
                }
                if (onDone != null) onDone.run();
            });
        });
    }

    /** Async abandon (/땅 파기). Removes the land from the DB and the cache. */
    public void abandonAsync(int landId, Runnable onDone) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            db.deleteLand(landId);
            Bukkit.getScheduler().runTask(plugin, () -> {
                MainDatabase.Land old = getLandById(landId);
                if (old != null) {
                    cellCache.remove(key(old.cellX(), old.cellZ()));
                }
                if (onDone != null) onDone.run();
            });
        });
    }

    // ---------- pending deeds ----------
    // An unclaimed deed's DB row is the actual entitlement (mirrors how a claimed Land works) -
    // the physical paper item handed to the player is just a disposable, regenerable copy of it.

    public MainDatabase.PendingDeed getPendingDeedById(int id) {
        return pendingDeedCache.get(id);
    }

    public List<MainDatabase.PendingDeed> getPendingDeeds(UUID owner) {
        return pendingDeedCache.values().stream().filter(d -> d.owner().equals(owner)).toList();
    }

    public int countPendingDeeds(UUID owner, boolean reservation) {
        int count = 0;
        for (MainDatabase.PendingDeed deed : pendingDeedCache.values()) {
            if (deed.owner().equals(owner) && deed.reservation() == reservation) count++;
        }
        return count;
    }

    /** Async: records the purchase as a DB-backed entitlement before any physical item exists. */
    public void createPendingDeedAsync(UUID owner, String name, boolean reservation,
                                        java.util.function.Consumer<MainDatabase.PendingDeed> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int id = db.createPendingDeed(owner, name, reservation);
            MainDatabase.PendingDeed result = id == -1 ? null : new MainDatabase.PendingDeed(id, owner, name, reservation);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result != null) {
                    pendingDeedCache.put(id, result);
                }
                callback.accept(result);
            });
        });
    }

    /** Async: consumes a pending deed (claimed into real land). */
    public void deletePendingDeedAsync(int id, Runnable onDone) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            db.deletePendingDeed(id);
            Bukkit.getScheduler().runTask(plugin, () -> {
                pendingDeedCache.remove(id);
                if (onDone != null) onDone.run();
            });
        });
    }
}
