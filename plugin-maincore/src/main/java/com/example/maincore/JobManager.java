package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-memory cache of every online player's job (loaded on join, dropped on quit), backed by the
 * jobs table. Also owns the upgrade purchase flow (three UpgradeTracks per job), the rank titles
 * derived from total upgrades, and the "which job does this tool belong to" mapping used to lock
 * tools to their job.
 */
public class JobManager {

    public static final long CHANGE_FEE = 100_000L;

    /** Rank titles by *total* upgrades bought across all three tracks (0..15): the title flips
     * each time the total reaches the next threshold. Shown as "신입 광부". */
    public static final String[] TITLES = {"신입", "평범한", "잘나가는", "전설적인"};
    public static final int[] TITLE_THRESHOLDS = {0, 5, 10, 15};
    public static final String NO_JOB_LABEL = "무직";

    private final MainCorePlugin plugin;
    private final Map<UUID, MainDatabase.JobProfile> cache = new ConcurrentHashMap<>();
    /** Players with a job-choice / upgrade / change DB write already in flight. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public JobManager(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    public static String titleFor(int totalUpgrades) {
        String title = TITLES[0];
        for (int i = 0; i < TITLE_THRESHOLDS.length; i++) {
            if (totalUpgrades >= TITLE_THRESHOLDS[i]) {
                title = TITLES[i];
            }
        }
        return title;
    }

    /** "신입 농부", or "무직" for a jobless player. */
    public String displayLabel(UUID uuid) {
        MainDatabase.JobProfile profile = cache.get(uuid);
        if (profile == null) {
            return NO_JOB_LABEL;
        }
        return titleFor(profile.total()) + " " + profile.job().label();
    }

    // ---------- cache ----------

    public void loadAsync(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            MainDatabase.JobProfile profile = plugin.getMainDatabase().getJobProfile(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (profile != null) {
                    cache.put(uuid, profile);
                } else {
                    cache.remove(uuid);
                }
                syncPlayerEffects(uuid);
            });
        });
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
        inFlight.remove(uuid);
    }

    /** Player-side (non-item) upgrade effects have to be re-applied whenever the profile changes. */
    private void syncPlayerEffects(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            plugin.getMinerAbilities().syncEfficiency(player);
        }
    }

    /** Cache lookup only - safe on the main thread. Null until the join-time load lands or if the
     * player never picked a job. */
    public MainDatabase.JobProfile getProfile(UUID uuid) {
        return cache.get(uuid);
    }

    public Job getJob(UUID uuid) {
        MainDatabase.JobProfile profile = cache.get(uuid);
        return profile == null ? null : profile.job();
    }

    public boolean hasJob(UUID uuid, Job job) {
        return job != null && job == getJob(uuid);
    }

    /** Level that should be stamped onto the job's tool item for this player (0 if jobless). */
    public int getItemLevel(UUID uuid) {
        MainDatabase.JobProfile profile = cache.get(uuid);
        return profile == null ? 0 : profile.itemLevel();
    }

    // ---------- choosing / changing ----------

    /** First-time pick: free. Fails (callback false) if the player already has a job. */
    public void chooseAsync(Player player, Job job, Consumer<Boolean> callback) {
        UUID uuid = player.getUniqueId();
        if (getJob(uuid) != null || !inFlight.add(uuid)) {
            callback.accept(false);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getMainDatabase().setJob(uuid, job);
            Bukkit.getScheduler().runTask(plugin, () -> {
                cache.put(uuid, new MainDatabase.JobProfile(job, 0, 0, 0));
                inFlight.remove(uuid);
                syncPlayerEffects(uuid);
                callback.accept(true);
            });
        });
    }

    /** Switching jobs costs CHANGE_FEE and resets every upgrade track. The old job's tools stay
     * in the inventory but stop working (see canUse). Callback gets null on failure with the
     * reason already messaged, or the new profile. */
    public void changeAsync(Player player, Job job, Consumer<MainDatabase.JobProfile> callback) {
        UUID uuid = player.getUniqueId();
        Job current = getJob(uuid);
        if (current == null || current == job || !inFlight.add(uuid)) {
            callback.accept(null);
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance = plugin.getMainDatabase().getBalance(uuid);
            if (balance < CHANGE_FEE) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    inFlight.remove(uuid);
                    player.sendMessage(Component.text(
                            String.format("크레딧이 부족합니다. 필요: %,d, 보유: %,d", CHANGE_FEE, balance),
                            NamedTextColor.RED));
                    callback.accept(null);
                });
                return;
            }
            long updated = plugin.getMainDatabase().addBalance(uuid, -CHANGE_FEE);
            plugin.getEconomyCache().set(uuid, updated);
            plugin.getMainDatabase().setJob(uuid, job);
            Bukkit.getScheduler().runTask(plugin, () -> {
                MainDatabase.JobProfile profile = new MainDatabase.JobProfile(job, 0, 0, 0);
                cache.put(uuid, profile);
                inFlight.remove(uuid);
                syncPlayerEffects(uuid);
                callback.accept(profile);
            });
        });
    }

    /** OP reset (/직업 초기화). */
    public void resetAsync(UUID uuid, Runnable onDone) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getMainDatabase().deleteJob(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                cache.remove(uuid);
                syncPlayerEffects(uuid);
                if (onDone != null) onDone.run();
            });
        });
    }

    // ---------- upgrades ----------

    /** Buys the next level on one track. If it's the job's item track, every matching tool the
     * player is carrying is re-tagged so they don't have to buy a fresh one; otherwise the
     * player-side effect is re-synced. Callback gets the new level, or -1 on failure (reason
     * already messaged). */
    public void upgradeAsync(Player player, int track, Consumer<Integer> callback) {
        UUID uuid = player.getUniqueId();
        MainDatabase.JobProfile profile = getProfile(uuid);
        if (profile == null || track < 0 || track >= profile.job().tracks().size()) {
            callback.accept(-1);
            return;
        }
        UpgradeTrack definition = profile.job().track(track);
        if (!definition.available()) {
            player.sendMessage(Component.text("아직 준비 중인 업그레이드입니다.", NamedTextColor.RED));
            callback.accept(-1);
            return;
        }
        int current = profile.level(track);
        long price = UpgradeTrack.price(current);
        if (current >= UpgradeTrack.MAX_LEVEL || price < 0) {
            player.sendMessage(Component.text("이미 최대 단계입니다.", NamedTextColor.RED));
            callback.accept(-1);
            return;
        }
        if (!inFlight.add(uuid)) {
            player.sendMessage(Component.text("이미 처리 중입니다.", NamedTextColor.RED));
            callback.accept(-1);
            return;
        }
        int next = current + 1;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance = plugin.getMainDatabase().getBalance(uuid);
            if (balance < price) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    inFlight.remove(uuid);
                    player.sendMessage(Component.text(
                            String.format("크레딧이 부족합니다. 필요: %,d, 보유: %,d", price, balance),
                            NamedTextColor.RED));
                    callback.accept(-1);
                });
                return;
            }
            long updated = plugin.getMainDatabase().addBalance(uuid, -price);
            plugin.getEconomyCache().set(uuid, updated);
            plugin.getMainDatabase().setJobUpgradeLevel(uuid, track, next);
            Bukkit.getScheduler().runTask(plugin, () -> {
                MainDatabase.JobProfile fresh = profile.withLevel(track, next);
                cache.put(uuid, fresh);
                inFlight.remove(uuid);
                if (track == fresh.job().itemTrack()) {
                    retagCarriedTools(player, fresh.job(), next);
                }
                syncPlayerEffects(uuid);
                callback.accept(next);
            });
        });
    }

    /** Applies the level to every tool of that job in the player's inventory + ender chest that
     * they actually own (someone else's dropped tool can't be picked up anyway, but be safe). */
    private void retagCarriedTools(Player player, Job job, int level) {
        for (ItemStack item : player.getInventory().getContents()) {
            retagIfOwned(player, item, job, level);
        }
        for (ItemStack item : player.getEnderChest().getContents()) {
            retagIfOwned(player, item, job, level);
        }
    }

    private void retagIfOwned(Player player, ItemStack item, Job job, int level) {
        if (item == null || toolJob(item) != job) {
            return;
        }
        UUID owner = plugin.getToolOwner(item);
        if (owner != null && !owner.equals(player.getUniqueId())) {
            return;
        }
        applyLevel(item, level);
    }

    /** Which job a special tool belongs to, or null for anything that isn't one. */
    public Job toolJob(ItemStack item) {
        if (plugin.getSpecialHoeItem().isSpecialHoe(item)) return Job.FARMER;
        if (plugin.getFishingRodTierItem().isSpecialRod(item)) return Job.FISHER;
        if (plugin.getSpecialPickaxeItem().isSpecialPickaxe(item)) return Job.MINER;
        if (plugin.getSpecialAxeItem().isSpecialAxe(item)) return Job.BUILDER;
        if (plugin.getCompassBiomeFinderItem().isSpecialCompass(item)) return Job.ADVENTURER;
        if (plugin.getSpeedBootsItem().isSpecialBoots(item)) return Job.ADVENTURER;
        return null;
    }

    /** Rewrites a tool's baked-in upgrade level (no-op for non-upgradeable items like the compass). */
    public void applyLevel(ItemStack item, int level) {
        if (plugin.getSpecialHoeItem().isSpecialHoe(item)) {
            plugin.getSpecialHoeItem().setLevel(item, level);
        } else if (plugin.getFishingRodTierItem().isSpecialRod(item)) {
            plugin.getFishingRodTierItem().setLevel(item, level);
        } else if (plugin.getSpecialPickaxeItem().isSpecialPickaxe(item)) {
            plugin.getSpecialPickaxeItem().setLevel(item, level);
        } else if (plugin.getSpecialAxeItem().isSpecialAxe(item)) {
            plugin.getSpecialAxeItem().setLevel(item, level);
        } else if (plugin.getSpeedBootsItem().isSpecialBoots(item)) {
            plugin.getSpeedBootsItem().setLevel(item, level);
        }
    }

    /** A job tool only works for its tagged owner (tool_owner, stamped at purchase) who is also
     * currently in that job - after a job change the old tools become inert, and someone else's
     * tool never works at all. Non-job items always pass. */
    public boolean canUse(Player player, ItemStack item) {
        Job required = toolJob(item);
        if (required == null) {
            return true;
        }
        UUID owner = plugin.getToolOwner(item);
        if (owner != null && !owner.equals(player.getUniqueId())) {
            return false;
        }
        return required == getJob(player.getUniqueId());
    }

    /** True if `inventory` currently holds at least one of this job's tools (used to tell the
     * player after a change that their old tools are now inert). */
    public boolean carriesToolsOf(PlayerInventory inventory, Job job) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && toolJob(item) == job) return true;
        }
        return false;
    }
}
