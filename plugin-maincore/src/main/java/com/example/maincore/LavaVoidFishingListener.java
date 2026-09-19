package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** 대단한 낚시대 exclusive: casting into lava (any world) or off the edge into the void (the End)
 * doesn't work in vanilla at all - a hook has nothing to float on, so no bite ever comes. This
 * fully replaces the bite logic for those two cases: once the hook touches lava or drops below
 * the void threshold in the End, it's pinned in place (vanilla physics would otherwise sink or
 * drop it out of sight) and a custom wait-then-reward flow takes over. */
public class LavaVoidFishingListener implements Listener {

    private enum Loot { LAVA, VOID }

    private static class Tracked {
        final FishHook hook;
        final Player player;
        boolean anchored;
        Location anchorLoc;
        Loot loot;
        long biteAtMillis;

        Tracked(FishHook hook, Player player) {
            this.hook = hook;
            this.player = player;
        }
    }

    private static final double VOID_Y_THRESHOLD = 0.0;
    private static final long MIN_WAIT_MILLIS = 5_000;
    private static final long MAX_WAIT_MILLIS = 15_000;

    private final MainCorePlugin plugin;
    private final Map<UUID, Tracked> tracked = new HashMap<>();
    private final Random random = new Random();

    public LavaVoidFishingListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (event.getState() != PlayerFishEvent.State.FISHING) {
            // Any other state (reeled in, caught, failed...) on a hook we were tracking means
            // vanilla already ended it - stop babysitting it.
            tracked.remove(player.getUniqueId());
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (plugin.getFishingRodTierItem().getTier(hand) != FishingRodTierItem.Tier.GREAT) {
            return;
        }
        tracked.put(player.getUniqueId(), new Tracked(event.getHook(), player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tracked.remove(event.getPlayer().getUniqueId());
    }

    /** Called every tick from MainCorePlugin. */
    public void tick() {
        Iterator<Tracked> it = tracked.values().iterator();
        while (it.hasNext()) {
            Tracked t = it.next();
            if (!t.hook.isValid()) {
                it.remove();
                continue;
            }
            if (!t.anchored) {
                tryAnchor(t);
                continue;
            }
            t.hook.teleport(t.anchorLoc);
            t.hook.setVelocity(new Vector(0, 0, 0));
            if (random.nextInt(15) == 0) {
                Particle particle = t.loot == Loot.LAVA ? Particle.LAVA : Particle.PORTAL;
                t.anchorLoc.getWorld().spawnParticle(particle, t.anchorLoc, 2, 0.1, 0.1, 0.1);
            }
            if (System.currentTimeMillis() >= t.biteAtMillis) {
                completeCatch(t);
                it.remove();
            }
        }
    }

    private void tryAnchor(Tracked t) {
        Location loc = t.hook.getLocation();
        boolean inLava = loc.getBlock().getType() == Material.LAVA;
        boolean inVoid = t.hook.getWorld().getEnvironment() == World.Environment.THE_END && loc.getY() < VOID_Y_THRESHOLD;
        if (!inLava && !inVoid) {
            return;
        }
        t.anchored = true;
        t.loot = inLava ? Loot.LAVA : Loot.VOID;
        t.anchorLoc = loc.clone();
        t.biteAtMillis = System.currentTimeMillis() + MIN_WAIT_MILLIS + random.nextInt((int) (MAX_WAIT_MILLIS - MIN_WAIT_MILLIS));
    }

    private void completeCatch(Tracked t) {
        ItemStack reward = t.loot == Loot.LAVA ? randomLavaLoot() : randomVoidLoot();
        t.player.getInventory().addItem(reward);
        t.player.getWorld().spawnParticle(
                t.loot == Loot.LAVA ? Particle.LAVA : Particle.PORTAL, t.anchorLoc, 15, 0.2, 0.2, 0.2);
        t.player.giveExp(1 + random.nextInt(6));
        t.hook.remove();
        t.player.sendMessage(Component.text(
                (t.loot == Loot.LAVA ? "용암" : "허공") + " 속에서 무언가를 낚았습니다!", NamedTextColor.GREEN));
    }

    private ItemStack randomLavaLoot() {
        double roll = random.nextDouble();
        if (roll < 0.10) return new ItemStack(Material.ANCIENT_DEBRIS);
        if (roll < 0.35) return new ItemStack(Material.BLAZE_ROD);
        if (roll < 0.55) return new ItemStack(Material.NETHER_WART, 1 + random.nextInt(3));
        return new ItemStack(Material.MAGMA_CREAM, 1 + random.nextInt(2));
    }

    private ItemStack randomVoidLoot() {
        double roll = random.nextDouble();
        if (roll < 0.10) return new ItemStack(Material.SHULKER_SHELL);
        if (roll < 0.20) return new ItemStack(Material.POPPED_CHORUS_FRUIT);
        if (roll < 0.55) return new ItemStack(Material.CHORUS_FRUIT, 1 + random.nextInt(2));
        return new ItemStack(Material.ENDER_PEARL, 1 + random.nextInt(2));
    }
}
