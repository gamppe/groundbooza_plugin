package com.example.serverbridge;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Farm-server copy of MainCore's MinerAbilities (효율 증가 attribute + 지상 텔레포트). The
 * teleport cooldown is per-server player data, so it doesn't carry over from main-server. */
public class MinerAbilities implements Listener {

    private static final double EFFICIENCY_PER_LEVEL = 0.2;
    private static final int TELEPORT_BASE_COOLDOWN_MIN = 30;
    private static final int TELEPORT_COOLDOWN_STEP_MIN = 5;
    private static final long CHARGE_MILLIS = 3_000L;

    private static final NamespacedKey EFFICIENCY_KEY = new NamespacedKey("maincore", "miner_efficiency");
    private static final NamespacedKey COOLDOWN_KEY = new NamespacedKey("maincore", "miner_tp_ready_at");

    private final JobCache jobs;
    private final Map<UUID, Long> sneakStart = new HashMap<>();

    public MinerAbilities(JobCache jobs) {
        this.jobs = jobs;
    }

    public void syncEfficiency(Player player) {
        AttributeInstance instance = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (instance == null) {
            return;
        }
        if (instance.getModifier(EFFICIENCY_KEY) != null) {
            instance.removeModifier(EFFICIENCY_KEY);
        }
        double bonus = EFFICIENCY_PER_LEVEL * jobs.level(player.getUniqueId(), JobCache.MINER, 0);
        if (bonus > 0) {
            instance.addModifier(new org.bukkit.attribute.AttributeModifier(EFFICIENCY_KEY, bonus,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR));
        }
    }

    private long readyAt(Player player) {
        Long value = player.getPersistentDataContainer().get(COOLDOWN_KEY, PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (event.isSneaking()) {
            sneakStart.put(uuid, System.currentTimeMillis());
        } else {
            sneakStart.remove(uuid);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sneakStart.remove(event.getPlayer().getUniqueId());
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : sneakStart.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isSneaking() || jobs.level(player.getUniqueId(), JobCache.MINER, 2) <= 0
                    || readyAt(player) > now) {
                continue;
            }
            long held = now - entry.getValue();
            Location center = player.getLocation().add(0, 1, 0);
            boolean charged = held >= CHARGE_MILLIS;
            int count = charged ? 25 : (int) (3 + held * 10 / CHARGE_MILLIS);
            player.getWorld().spawnParticle(Particle.PORTAL, center, count, 0.4, 0.6, 0.4, charged ? 0.6 : 0.2);
            if (charged && held - CHARGE_MILLIS < 250) {
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
                player.sendActionBar(Component.text("점프하면 지상으로 순간이동합니다", NamedTextColor.LIGHT_PURPLE));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        Long start = sneakStart.get(player.getUniqueId());
        if (start == null || System.currentTimeMillis() - start < CHARGE_MILLIS) {
            return;
        }
        int level = jobs.level(player.getUniqueId(), JobCache.MINER, 2);
        if (level <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long ready = readyAt(player);
        if (ready > now) {
            long left = (ready - now + 59_999) / 60_000;
            player.sendMessage(Component.text("지상 텔레포트 쿨타임이 " + left + "분 남았습니다.", NamedTextColor.RED));
            return;
        }
        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            player.sendMessage(Component.text("이 차원에서는 지상 텔레포트를 쓸 수 없습니다.", NamedTextColor.RED));
            return;
        }
        Location from = player.getLocation();
        Block top = world.getHighestBlockAt(from);
        Location to = top.getLocation().add(0.5, 1, 0.5);
        to.setYaw(from.getYaw());
        to.setPitch(from.getPitch());
        if (to.getY() <= from.getY() + 1) {
            player.sendMessage(Component.text("이미 지상입니다.", NamedTextColor.RED));
            return;
        }
        sneakStart.remove(player.getUniqueId());
        world.spawnParticle(Particle.PORTAL, from.add(0, 1, 0), 60, 0.5, 0.8, 0.5, 1.0);
        world.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        player.teleport(to);
        world.spawnParticle(Particle.PORTAL, to.clone().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 1.0);
        world.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        int cooldownMin = TELEPORT_BASE_COOLDOWN_MIN - TELEPORT_COOLDOWN_STEP_MIN * (level - 1);
        player.getPersistentDataContainer().set(COOLDOWN_KEY, PersistentDataType.LONG, now + cooldownMin * 60_000L);
        player.sendMessage(Component.text("지상으로 순간이동했습니다. (쿨타임 " + cooldownMin + "분)", NamedTextColor.GREEN));
    }
}
