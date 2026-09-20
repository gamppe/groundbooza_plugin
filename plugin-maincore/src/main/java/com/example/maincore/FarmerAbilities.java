package com.example.maincore;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 농부's two non-item upgrade tracks. 행운: a chance for crop harvests to drop double (and a
 * bonus on every AnimalFeeding roll). 기우제 (rain dance): crouch and jump 5 times (rain particles on each hop) to grow every
 * crop in the land cell you're standing in by one stage; un-crouching or pausing 3s between
 * jumps cancels. Cooldown lives in the player's PDC like the miner teleport.
 */
public class FarmerAbilities implements Listener {

    public static final double LUCK_CHANCE_PER_LEVEL = 0.2;
    public static final int RAIN_JUMPS = 5;
    public static final long RAIN_JUMP_TIMEOUT_MILLIS = 3_000L;
    public static final int RAIN_BASE_COOLDOWN_MIN = 30;
    public static final int RAIN_COOLDOWN_STEP_MIN = 5;
    /** Stages a crop jumps per 기우제. Short crops (beetroot, nether wart, cocoa... max age ≤ 3)
     * only get one so a single dance doesn't take them from seed to done. */
    public static final int RAIN_GROWTH = 2;
    public static final int RAIN_GROWTH_SHORT = 1;
    private static final int SHORT_CROP_MAX_AGE = 3;

    private static class Dance {
        int jumps;
        long lastJumpAt;
    }

    private final MainCorePlugin plugin;
    private final NamespacedKey cooldownKey;
    private final Map<UUID, Dance> dances = new HashMap<>();
    private final Random random = new Random();

    public FarmerAbilities(MainCorePlugin plugin) {
        this.plugin = plugin;
        this.cooldownKey = new NamespacedKey(plugin, "farmer_rain_ready_at");
    }

    public static double luckChance(int level) {
        return LUCK_CHANCE_PER_LEVEL * Math.max(0, level);
    }

    /** -1 while locked (level 0); 30 → 25 → 20 → 15 → 10 minutes for levels 1..5. */
    public static int rainCooldownMinutes(int level) {
        if (level <= 0) return -1;
        return RAIN_BASE_COOLDOWN_MIN - RAIN_COOLDOWN_STEP_MIN * (level - 1);
    }

    private int trackLevel(Player player, int track) {
        MainDatabase.JobProfile profile = plugin.getJobManager().getProfile(player.getUniqueId());
        return profile == null || profile.job() != Job.FARMER ? 0 : profile.level(track);
    }

    // ---------- 행운 ----------

    @EventHandler(ignoreCancelled = true)
    public void onCropDrop(BlockDropItemEvent event) {
        Material type = event.getBlockState().getType();
        if (!CropRules.GROWABLE.contains(type)) {
            return;
        }
        if (!(event.getBlockState().getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
            return; // only a real harvest counts, not tearing out seedlings
        }
        double chance = luckChance(trackLevel(event.getPlayer(), 0));
        if (chance <= 0 || random.nextDouble() >= chance) {
            return;
        }
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() * 2));
            item.setItemStack(stack);
        }
    }

    // ---------- 기우제 ----------

    private long readyAt(Player player) {
        Long value = player.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            Dance dance = dances.remove(event.getPlayer().getUniqueId());
            if (dance != null && dance.jumps > 0) {
                event.getPlayer().sendMessage(Component.text("기우제가 취소되었습니다.", NamedTextColor.GRAY));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dances.remove(event.getPlayer().getUniqueId());
    }

    /** Called every 5 ticks from MainCorePlugin: drops dances whose last hop was too long ago. */
    public void tick() {
        long now = System.currentTimeMillis();
        dances.entrySet().removeIf(entry -> {
            if (now - entry.getValue().lastJumpAt <= RAIN_JUMP_TIMEOUT_MILLIS) {
                return false;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.sendMessage(Component.text("기우제가 취소되었습니다. (3초 안에 다시 점프해야 합니다)", NamedTextColor.GRAY));
            }
            return true;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        int level = trackLevel(player, 2);
        if (level <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long ready = readyAt(player);
        if (ready > now) {
            if (!dances.containsKey(player.getUniqueId())) {
                long left = (ready - now + 59_999) / 60_000;
                player.sendActionBar(Component.text("기우제 쿨타임 " + left + "분 남음", NamedTextColor.GRAY));
            }
            return;
        }
        Dance dance = dances.computeIfAbsent(player.getUniqueId(), k -> new Dance());
        dance.jumps++;
        dance.lastJumpAt = now;
        Location center = player.getLocation().add(0, 1.5, 0);
        player.getWorld().spawnParticle(Particle.RAIN, center, 30 + dance.jumps * 15, 1.5, 0.8, 1.5, 0);
        player.playSound(player.getLocation(), Sound.WEATHER_RAIN, 0.4f, 1.2f);
        if (dance.jumps < RAIN_JUMPS) {
            player.sendActionBar(Component.text("기우제 " + dance.jumps + " / " + RAIN_JUMPS, NamedTextColor.AQUA));
            return;
        }
        dances.remove(player.getUniqueId());
        performRain(player, level);
    }

    private void performRain(Player player, int level) {
        Location loc = player.getLocation();
        if (!player.isOp() && !plugin.getLandManager().canBuild(loc, player.getUniqueId())) {
            player.sendMessage(Component.text("다른 사람의 땅에서는 기우제를 지낼 수 없습니다.", NamedTextColor.RED));
            return;
        }
        int grown = growCropsInCell(loc.getWorld(), LandGrid.cellX(loc), LandGrid.cellZ(loc));
        int cooldownMin = rainCooldownMinutes(level);
        player.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG,
                System.currentTimeMillis() + cooldownMin * 60_000L);
        loc.getWorld().spawnParticle(Particle.RAIN, loc.add(0, 3, 0), 400, 20, 4, 20, 0);
        loc.getWorld().playSound(loc, Sound.WEATHER_RAIN_ABOVE, 1f, 1f);
        player.sendMessage(Component.text(
                "기우제 성공! 작물 " + grown + "개가 자랐습니다. (쿨타임 " + cooldownMin + "분)", NamedTextColor.GREEN));
    }

    /** Ages every growable crop in the 48x48 cell (RAIN_GROWTH stages, 1 for short crops);
     * returns how many actually grew. */
    private int growCropsInCell(World world, int cellX, int cellZ) {
        int grown = 0;
        int minX = LandGrid.minX(cellX);
        int minZ = LandGrid.minZ(cellZ);
        int lo = world.getMinHeight();
        int hi = world.getMaxHeight() - 1;
        for (int cx = minX >> 4; cx <= (minX + LandGrid.CELL_SIZE - 1) >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= (minZ + LandGrid.CELL_SIZE - 1) >> 4; cz++) {
                ChunkSnapshot snap = world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = lo; y <= hi; y++) {
                            if (!CropRules.GROWABLE.contains(snap.getBlockType(x, y, z))) continue;
                            Block block = world.getBlockAt((cx << 4) + x, y, (cz << 4) + z);
                            if (block.getBlockData() instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
                                int step = ageable.getMaximumAge() <= SHORT_CROP_MAX_AGE ? RAIN_GROWTH_SHORT : RAIN_GROWTH;
                                ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + step));
                                block.setBlockData(ageable, false);
                                grown++;
                            }
                        }
                    }
                }
            }
        }
        return grown;
    }
}
