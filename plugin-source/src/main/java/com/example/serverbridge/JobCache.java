package com.example.serverbridge;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Farm-server's read-only view of MainCore's job data: loaded from MainCore's schema when a
 * player joins (they have to pass through a server switch to get here, so that's always fresh
 * enough) and dropped on quit. Jobs are plain strings ("MINER"...) since MainCore's enum isn't
 * available; tool ownership/job locks read the same PDC keys MainCore stamps.
 */
public class JobCache implements Listener {

    public static final String FARMER = "FARMER";
    public static final String FISHER = "FISHER";
    public static final String MINER = "MINER";
    public static final String BUILDER = "BUILDER";
    public static final String ADVENTURER = "ADVENTURER";

    private static final NamespacedKey TOOL_OWNER_KEY = new NamespacedKey("maincore", "tool_owner");
    private static final NamespacedKey HOE_KEY = new NamespacedKey("maincore", "special_hoe");
    private static final NamespacedKey PICKAXE_KEY = new NamespacedKey("maincore", "special_pickaxe");
    private static final NamespacedKey AXE_KEY = new NamespacedKey("maincore", "special_axe");
    private static final NamespacedKey ROD_KEY = new NamespacedKey("maincore", "special_rod");
    private static final NamespacedKey COMPASS_KEY = new NamespacedKey("maincore", "special_compass");
    private static final NamespacedKey BOOTS_KEY = new NamespacedKey("maincore", "special_boots");

    private final ServerBridgePlugin plugin;
    private final String schema;
    private final Map<UUID, DatabaseManager.JobProfile> cache = new ConcurrentHashMap<>();
    /** Run on the main thread after every (re)load so player-side effects can be re-synced. */
    private Consumer<Player> onLoaded = p -> { };

    public JobCache(ServerBridgePlugin plugin, String schema) {
        this.plugin = plugin;
        this.schema = schema;
    }

    public void setOnLoaded(Consumer<Player> onLoaded) {
        this.onLoaded = onLoaded;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadAsync(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    public void loadAsync(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DatabaseManager.JobProfile profile = plugin.getDatabaseManager().loadJobProfile(schema, uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (profile != null) {
                    cache.put(uuid, profile);
                } else {
                    cache.remove(uuid);
                }
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    onLoaded.accept(player);
                }
            });
        });
    }

    public DatabaseManager.JobProfile getProfile(UUID uuid) {
        return cache.get(uuid);
    }

    public String getJob(UUID uuid) {
        DatabaseManager.JobProfile profile = cache.get(uuid);
        return profile == null ? null : profile.job();
    }

    public boolean hasJob(UUID uuid, String job) {
        return job.equals(getJob(uuid));
    }

    /** Track level (0..5) if the player has that job, else 0. */
    public int level(UUID uuid, String job, int track) {
        DatabaseManager.JobProfile profile = cache.get(uuid);
        return profile == null || !profile.job().equals(job) ? 0 : profile.level(track);
    }

    private static boolean hasMarker(ItemStack item, NamespacedKey key) {
        return item != null && item.hasItemMeta()
                && Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BOOLEAN));
    }

    /** Mirrors MainCore's JobManager.toolJob. */
    public static String toolJob(ItemStack item) {
        if (hasMarker(item, HOE_KEY)) return FARMER;
        if (hasMarker(item, ROD_KEY)) return FISHER;
        if (hasMarker(item, PICKAXE_KEY)) return MINER;
        if (hasMarker(item, AXE_KEY)) return BUILDER;
        if (hasMarker(item, COMPASS_KEY) || hasMarker(item, BOOTS_KEY)) return ADVENTURER;
        return null;
    }

    /** Mirrors MainCore's JobManager.canUse: the tool's tagged owner, currently in the tool's job. */
    public boolean canUse(Player player, ItemStack item) {
        String required = toolJob(item);
        if (required == null) {
            return true;
        }
        if (item.hasItemMeta()) {
            String owner = item.getItemMeta().getPersistentDataContainer().get(TOOL_OWNER_KEY, PersistentDataType.STRING);
            if (owner != null && !owner.equals(player.getUniqueId().toString())) {
                return false;
            }
        }
        return required.equals(getJob(player.getUniqueId()));
    }
}
