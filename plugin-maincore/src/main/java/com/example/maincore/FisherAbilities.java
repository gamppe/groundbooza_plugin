package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 어부's third track, 낚시꾼의 행운: crouch and right-click with any fishing rod to start a short
 * buff during which every cast bites the moment the hook lands (wait and lure timers forced to
 * a tick), and lava/void fishing waits only a second or two instead of 5~15 s. Unlocked at
 * level 1 (5 s / 30 min); each level after takes 5 min off the cooldown and adds 1.5 s. Cooldown lives
 * in the player's PDC like the miner teleport; the buff itself is in-memory (a relog ends it).
 * ServerBridge carries a copy for farm-server.
 */
public class FisherAbilities implements Listener {

    public static final int BASE_COOLDOWN_MIN = 30;
    public static final int COOLDOWN_STEP_MIN = 5;
    public static final double BASE_BUFF_SECONDS = 5.0;
    public static final double BUFF_STEP_SECONDS = 1.5;
    /** Lava/void bite window while the buff is up (normally 5~15 s). */
    public static final long BUFFED_LAVA_MIN_WAIT_MILLIS = 1_000;
    public static final long BUFFED_LAVA_MAX_WAIT_MILLIS = 2_000;

    private final MainCorePlugin plugin;
    private final NamespacedKey cooldownKey;
    private final Map<UUID, Long> activeUntil = new HashMap<>();

    public FisherAbilities(MainCorePlugin plugin) {
        this.plugin = plugin;
        this.cooldownKey = new NamespacedKey(plugin, "fisher_luck_ready_at");
    }

    public static int cooldownMinutes(int level) {
        return Math.max(COOLDOWN_STEP_MIN, BASE_COOLDOWN_MIN - COOLDOWN_STEP_MIN * (level - 1));
    }

    public static double buffSeconds(int level) {
        return BASE_BUFF_SECONDS + BUFF_STEP_SECONDS * (level - 1);
    }

    public static String effect(int level) {
        if (level <= 0) {
            return "잠김";
        }
        String seconds = buffSeconds(level) % 1 == 0 ? String.valueOf((int) buffSeconds(level)) : String.valueOf(buffSeconds(level));
        return "지속 " + seconds + "초 / 쿨타임 " + cooldownMinutes(level) + "분";
    }

    public boolean isActive(UUID uuid) {
        Long until = activeUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    private long readyAt(Player player) {
        Long value = player.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    // ---------- activation ----------

    /** Deliberately NOT ignoreCancelled: right-clicking air with a rod arrives here already
     * cancelled (the cast consumes the interaction), so filtering cancelled events would drop
     * every activation except the ones aimed at a block. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // the event fires once per hand; the off-hand copy would double-trigger
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking() || event.getItem() == null || event.getItem().getType() != Material.FISHING_ROD) {
            return;
        }
        tryActivate(player);
    }

    /** Level in the 낚시꾼의 행운 track, or 0 for anyone who can't use it. */
    private int trackLevel(UUID uuid) {
        MainDatabase.JobProfile profile = plugin.getJobManager().getProfile(uuid);
        if (profile == null || profile.job() != Job.FISHER) {
            return 0;
        }
        return profile.level(2);
    }

    /** Starts the buff if it's unlocked, not already running and off cooldown. Silent when the
     * player simply hasn't unlocked it, so an ordinary sneaking cast stays quiet. */
    private void tryActivate(Player player) {
        UUID uuid = player.getUniqueId();
        int level = trackLevel(uuid);
        if (level <= 0 || isActive(uuid)) {
            return;
        }
        long now = System.currentTimeMillis();
        long ready = readyAt(player);
        if (ready > now) {
            long leftSec = (ready - now + 999) / 1_000;
            player.sendActionBar(Component.text("낚시꾼의 행운 쿨타임: " + leftSec / 60 + "분 " + leftSec % 60 + "초",
                    NamedTextColor.RED));
            return;
        }
        double seconds = buffSeconds(level);
        activeUntil.put(uuid, now + (long) (seconds * 1_000));
        player.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG, now + cooldownMinutes(level) * 60_000L);
        playActivation(player);
        player.sendMessage(Component.text("낚시꾼의 행운 발동! " + seconds + "초 동안 찌가 닿자마자 입질이 옵니다. (쿨타임 "
                + cooldownMinutes(level) + "분)", NamedTextColor.AQUA));
    }

    // ---------- instant bite ----------

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking()) {
            tryActivate(player); // backup trigger: a sneaking cast always lands here
        }
        if (!isActive(player.getUniqueId())) {
            return;
        }
        // Wait = time until a fish starts approaching; lure = the approach itself. Both to a
        // single tick so the bobber dips the moment it settles.
        event.getHook().setWaitTime(1, 1);
        event.getHook().setLureTime(1, 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        activeUntil.remove(event.getPlayer().getUniqueId());
    }

    // ---------- feedback ----------

    /** A splash burst at the feet, a ring of bobber ripples around the player, and a bobber
     * splash + chime so it reads as "the water woke up". */
    private void playActivation(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        world.playSound(at, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 0.8f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        world.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
        world.spawnParticle(Particle.SPLASH, at.clone().add(0, 0.3, 0), 60, 0.6, 0.2, 0.6, 0.1);
        world.spawnParticle(Particle.BUBBLE_POP, at.clone().add(0, 1, 0), 30, 0.5, 0.6, 0.5, 0.05);
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24;
            world.spawnParticle(Particle.FISHING, at.clone().add(Math.cos(angle) * 1.2, 0.2, Math.sin(angle) * 1.2), 1, 0, 0, 0, 0);
        }
    }

    /** Called every 5 ticks from MainCorePlugin: announces expiry. */
    public void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = activeUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (entry.getValue() > now) {
                if (player != null) { // a faint bubble aura for as long as the buff lasts
                    player.getWorld().spawnParticle(Particle.BUBBLE_POP, player.getLocation().add(0, 1, 0), 3, 0.4, 0.5, 0.4, 0.02);
                }
                continue;
            }
            it.remove();
            if (player != null) {
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f, 0.7f);
                player.sendActionBar(Component.text("낚시꾼의 행운이 끝났습니다.", NamedTextColor.GRAY));
            }
        }
    }
}
