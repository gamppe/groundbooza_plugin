package com.example.serverbridge;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
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

/** Farm-server copy of MainCore's FarmerAbilities (행운 double crop drops + 기우제). There's no land
 * system here, so 기우제 just uses the same 48x48 grid cell math with no ownership check. */
public class FarmerAbilities implements Listener {

    private static final double LUCK_CHANCE_PER_LEVEL = 0.2;
    private static final int RAIN_JUMPS = 5;
    private static final long RAIN_JUMP_TIMEOUT_MILLIS = 3_000L;
    private static final int RAIN_BASE_COOLDOWN_MIN = 30;
    private static final int RAIN_COOLDOWN_STEP_MIN = 5;
    private static final int RAIN_GROWTH = 2;
    private static final int RAIN_GROWTH_SHORT = 1;
    private static final int SHORT_CROP_MAX_AGE = 3;
    private static final int CELL_SIZE = 48;

    private static final NamespacedKey COOLDOWN_KEY = new NamespacedKey("maincore", "farmer_rain_ready_at");

    private static class Dance {
        int jumps;
        long lastJumpAt;
    }

    private final JobCache jobs;
    private final Map<UUID, Dance> dances = new HashMap<>();
    private final Random random = new Random();

    public FarmerAbilities(JobCache jobs) {
        this.jobs = jobs;
    }

    /** 행운 multiplier for other systems (AnimalFeeding): 1 + 20% × level. */
    public double luckMultiplier(Player player) {
        return 1 + LUCK_CHANCE_PER_LEVEL * jobs.level(player.getUniqueId(), JobCache.FARMER, 0);
    }

    private double luckChance(Player player) {
        return LUCK_CHANCE_PER_LEVEL * jobs.level(player.getUniqueId(), JobCache.FARMER, 0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCropDrop(BlockDropItemEvent event) {
        if (!CropRules.GROWABLE.contains(event.getBlockState().getType())) {
            return;
        }
        if (!(event.getBlockState().getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }
        double chance = luckChance(event.getPlayer());
        if (chance <= 0 || random.nextDouble() >= chance) {
            return;
        }
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            stack.setAmount(Math.min(stack.getMaxStackSize(), stack.getAmount() * 2));
            item.setItemStack(stack);
        }
    }

    private long readyAt(Player player) {
        Long value = player.getPersistentDataContainer().get(COOLDOWN_KEY, PersistentDataType.LONG);
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
        int level = jobs.level(player.getUniqueId(), JobCache.FARMER, 2);
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

        Location loc = player.getLocation();
        int grown = growCropsInCell(loc.getWorld(), Math.floorDiv(loc.getBlockX(), CELL_SIZE), Math.floorDiv(loc.getBlockZ(), CELL_SIZE));
        int cooldownMin = RAIN_BASE_COOLDOWN_MIN - RAIN_COOLDOWN_STEP_MIN * (level - 1);
        player.getPersistentDataContainer().set(COOLDOWN_KEY, PersistentDataType.LONG, now + cooldownMin * 60_000L);
        loc.getWorld().spawnParticle(Particle.RAIN, loc.add(0, 3, 0), 400, 20, 4, 20, 0);
        loc.getWorld().playSound(loc, Sound.WEATHER_RAIN_ABOVE, 1f, 1f);
        player.sendMessage(Component.text(
                "기우제 성공! 작물 " + grown + "개가 자랐습니다. (쿨타임 " + cooldownMin + "분)", NamedTextColor.GREEN));
    }

    private int growCropsInCell(World world, int cellX, int cellZ) {
        int grown = 0;
        int minX = cellX * CELL_SIZE;
        int minZ = cellZ * CELL_SIZE;
        int lo = world.getMinHeight();
        int hi = world.getMaxHeight() - 1;
        for (int cx = minX >> 4; cx <= (minX + CELL_SIZE - 1) >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= (minZ + CELL_SIZE - 1) >> 4; cz++) {
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
