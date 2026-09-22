package com.example.serverbridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Strider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/** 수제미끼낚시대 with 섬세한 미끼제작 level 4 (Nether lava) / 5 (End void). Casting into lava or
 * off the edge into the void doesn't work in vanilla at all - a hook has nothing to float on,
 * so no bite ever comes. This fully replaces the bite logic for those two cases: once the hook
 * touches lava or drops below the void threshold, it's pinned in place (vanilla physics would
 * otherwise sink or drop it out of sight) and a custom wait-then-reward flow takes over. The
 * reward tables here ignore 낚시능력증가's fish/junk/treasure split entirely, and some outcomes
 * aren't items: a mob gets reeled in, the player catches fire, or gets yanked somewhere.
 * 낚시꾼의 행운 (FisherAbilities) cuts the 5~15 s wait down to 1~2 s.
 * Farm-server copy of MainCore's listener - farm-server has its own Nether and End. */
public class LavaVoidFishingListener implements Listener {

    private enum Loot { LAVA, VOID }

    private static class Tracked {
        final FishHook hook;
        final Player player;
        final int baitLevel;
        boolean anchored;
        Location anchorLoc;
        Loot loot;
        long biteAtMillis;

        Tracked(FishHook hook, Player player, int baitLevel) {
            this.hook = hook;
            this.player = player;
            this.baitLevel = baitLevel;
        }
    }

    /** A catch on its way back to the rod's owner. */
    private static class Flying {
        final Entity entity;
        final Player player;
        int ticksLeft = REEL_TIMEOUT_TICKS;

        Flying(Entity entity, Player player) {
            this.entity = entity;
            this.player = player;
        }
    }

    public static final int LAVA_LEVEL = 4;
    public static final int VOID_LEVEL = 5;

    /** The hook counts as "in the void" below this Y - a bit above the End's own floor of 0 so
     * you can fish off an island edge without having to drop the bobber the whole way down. */
    private static final double VOID_Y_THRESHOLD = 15.0;
    private static final long MIN_WAIT_MILLIS = 5_000;
    private static final long MAX_WAIT_MILLIS = 15_000;
    private static final int TELEPORT_RADIUS = 24;

    private final ServerBridgePlugin plugin;
    private final JobCache jobs;
    private final FisherAbilities fisherAbilities;
    private final Map<UUID, Tracked> tracked = new HashMap<>();
    private final List<Flying> flying = new ArrayList<>();
    private final Random random = new Random();
    private final WeightedTable<Consumer<Tracked>> lavaLoot;
    private final WeightedTable<Consumer<Tracked>> voidLoot;

    public LavaVoidFishingListener(ServerBridgePlugin plugin, JobCache jobs, FisherAbilities fisherAbilities) {
        this.plugin = plugin;
        this.jobs = jobs;
        this.fisherAbilities = fisherAbilities;
        FishItems fish = new FishItems();

        lavaLoot = new WeightedTable<Consumer<Tracked>>()
                .add(9, odd(t -> reelMob(t, Strider.class, "스트라이더")))
                .add(0.3, odd(t -> {
                    PigZombie zombie = reelMob(t, PigZombie.class, "화난 새끼 좀비 피글린");
                    zombie.setBaby();
                    zombie.setAngry(true);
                    zombie.setTarget(t.player);
                }))
                .add(40, give(fish.create(FishItems.Kind.LAVA_TROPICAL)))
                .add(15, give(fish.create(FishItems.Kind.LAVA_PUFFERFISH)))
                .add(0.05, odd(t -> reelMob(t, Dolphin.class, "돌고래")))
                .add(0.8, give(Material.DRIED_GHAST))
                .add(5, give(Material.GHAST_TEAR))
                .add(4, give(Material.FIRE_CHARGE))
                .add(3, give(Material.NETHER_BRICK))
                .add(0.2, give(Material.NETHERITE_SCRAP))
                .add(0.01, give(WildDeedItem.create()))
                .add(2.5, give(Material.TWISTING_VINES))
                .add(2.5, give(Material.WEEPING_VINES))
                .add(1, odd(t -> {
                    t.player.setFireTicks(100);
                    t.player.sendMessage(Component.text("용암이 튀어 몸에 불이 붙었습니다!", NamedTextColor.RED));
                }))
                .add(3, give(Material.MAGMA_CREAM))
                .add(10, give(Material.NETHER_WART))
                .add(5, give(Material.GOLD_NUGGET))
                .add(3, give(Material.GOLD_INGOT));

        voidLoot = new WeightedTable<Consumer<Tracked>>()
                .add(0.3, odd(t -> reelMob(t, Endermite.class, "엔더마이트")))
                .add(1, odd(this::yankToRandomGround))
                .add(40, give(fish.create(FishItems.Kind.VOID_SALMON)))
                .add(15, give(fish.create(FishItems.Kind.VOID_PUFFERFISH)))
                .add(0.05, odd(t -> reelMob(t, Dolphin.class, "돌고래")))
                .add(0.2, give(Material.ENCHANTED_GOLDEN_APPLE))
                .add(0.2, give(Material.OMINOUS_TRIAL_KEY))
                .add(0.01, give(WildDeedItem.create()))
                .add(1, odd(effect(PotionEffectType.LEVITATION, 10 * 20, "몸이 떠오릅니다!")))
                .add(0.3, odd(effect(PotionEffectType.DARKNESS, 5 * 20, "눈앞이 어두워집니다...")))
                .add(10, give(Material.ENDER_PEARL))
                .add(5, give(Material.CHORUS_FRUIT))
                .add(5, give(Material.SHULKER_SHELL))
                .add(1, give(Material.END_ROD));
    }

    /** Lava fishing needs bait level 4 and the Nether; void fishing level 5 and the End. */
    public static boolean canFishHere(int baitLevel, World.Environment env) {
        return (env == World.Environment.NETHER && baitLevel >= LAVA_LEVEL)
                || (env == World.Environment.THE_END && baitLevel >= VOID_LEVEL);
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
        ItemStack rod = FishingLoot.rodInHand(player);
        if (!JobCache.FISHER.equals(JobCache.toolJob(rod)) || !jobs.canUse(player, rod)) {
            return; // 어부 only - a leftover rod from a previous job fishes like a plain one
        }
        int baitLevel = jobs.level(player.getUniqueId(), JobCache.FISHER, 1);
        if (!canFishHere(baitLevel, player.getWorld().getEnvironment())) {
            return;
        }
        tracked.put(player.getUniqueId(), new Tracked(event.getHook(), player, baitLevel));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        tracked.remove(event.getPlayer().getUniqueId());
    }

    /** Called every tick from ServerBridgePlugin. */
    public void tick() {
        tickFlying();
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
        World.Environment env = t.hook.getWorld().getEnvironment();
        boolean inLava = env == World.Environment.NETHER && t.baitLevel >= LAVA_LEVEL
                && loc.getBlock().getType() == Material.LAVA;
        boolean inVoid = env == World.Environment.THE_END && t.baitLevel >= VOID_LEVEL
                && loc.getY() < VOID_Y_THRESHOLD;
        if (!inLava && !inVoid) {
            return;
        }
        t.anchored = true;
        t.loot = inLava ? Loot.LAVA : Loot.VOID;
        t.anchorLoc = loc.clone();
        boolean lucky = fisherAbilities.isActive(t.player.getUniqueId());
        long min = lucky ? FisherAbilities.BUFFED_LAVA_MIN_WAIT_MILLIS : MIN_WAIT_MILLIS;
        long max = lucky ? FisherAbilities.BUFFED_LAVA_MAX_WAIT_MILLIS : MAX_WAIT_MILLIS;
        t.biteAtMillis = System.currentTimeMillis() + min + random.nextInt((int) (max - min));
    }

    private void completeCatch(Tracked t) {
        t.player.getWorld().spawnParticle(
                t.loot == Loot.LAVA ? Particle.LAVA : Particle.PORTAL, t.anchorLoc, 15, 0.2, 0.2, 0.2);
        t.player.giveExp(1 + random.nextInt(6));
        t.hook.remove();
        t.player.sendMessage(Component.text(
                (t.loot == Loot.LAVA ? "용암" : "허공") + " 속에서 무언가를 낚았습니다!", NamedTextColor.GREEN));
        (t.loot == Loot.LAVA ? lavaLoot : voidLoot).roll(random).accept(t);
    }

    // ---------- outcomes ----------

    /** Reeling something in is announced by ear: an item gets vanilla's catch ping, anything
     * else (a mob, a mishap, a yank) gets one shared low "that wasn't a fish" tone. */
    private static final float CATCH_VOLUME = 0.7f;
    private static final float ODD_VOLUME = 0.5f;
    /** Blocks per tick a catch travels on its way back (1.2 = ~24 blocks/second). */
    private static final double REEL_SPEED = 1.2;
    /** Close enough to the player to stop steering and let it drop. */
    private static final double REEL_ARRIVE_DISTANCE = 1.2;
    /** Give up steering after this long, so nothing is left hanging with gravity off. */
    private static final int REEL_TIMEOUT_TICKS = 100;
    /** A landed catch stays damage-proof this long, so it cannot burn beside the lava. */
    private static final long FIRE_GRACE_TICKS = 20L;

    private Consumer<Tracked> give(Material material) {
        return t -> reelItem(t, new ItemStack(material));
    }

    private Consumer<Tracked> give(ItemStack prototype) {
        return t -> reelItem(t, prototype.clone());
    }

    /** Drops the catch as a real item entity at the bobber and reels it in, instead of quietly
     * appending it to the inventory. */
    private void reelItem(Tracked t, ItemStack stack) {
        Player player = t.player;
        Item entity = t.anchorLoc.getWorld().dropItem(t.anchorLoc, stack);
        entity.setPickupDelay(0);
        entity.setCanMobPickup(false);
        entity.setOwner(player.getUniqueId()); // nobody else can snatch the catch
        entity.setInvulnerable(true); // it starts out sitting in lava; land() ends this
        flying.add(new Flying(entity, player));
        // Vanilla plays this for a normal catch; the custom flow has to do it itself.
        player.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, CATCH_VOLUME, 1f);
    }

    /** Vanilla can throw a catch once and let it arc over, because its bobber floats at about
     * the player's own height. Ours sits in a lava pool below or tens of blocks down a void
     * shaft, so instead of one throw the catch is steered every tick: gravity off, velocity
     * re-aimed at the player, until it arrives. Reaches from any distance, still reads as a
     * reel-in. */
    private void tickFlying() {
        Iterator<Flying> it = flying.iterator();
        while (it.hasNext()) {
            Flying f = it.next();
            if (!f.entity.isValid() || !f.player.isOnline() || --f.ticksLeft <= 0
                    || !f.entity.getWorld().equals(f.player.getWorld())) {
                land(f);
                it.remove();
                continue;
            }
            Vector pull = f.player.getLocation().add(0, 0.6, 0).toVector()
                    .subtract(f.entity.getLocation().toVector());
            if (pull.lengthSquared() <= REEL_ARRIVE_DISTANCE * REEL_ARRIVE_DISTANCE) {
                land(f);
                it.remove();
                continue;
            }
            f.entity.setGravity(false);
            f.entity.setVelocity(pull.normalize().multiply(REEL_SPEED));
        }
    }

    /** Hands the catch back to ordinary physics once it has arrived (or given up). */
    private void land(Flying f) {
        if (!f.entity.isValid()) {
            return;
        }
        f.entity.setGravity(true);
        f.entity.setVelocity(new Vector(0, 0, 0));
        if (f.entity instanceof Item item) {
            // Stays fire-proof a moment longer, so landing beside the lava it came out of
            // cannot undo the catch; after that it burns like any other dropped item.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (item.isValid()) {
                    item.setInvulnerable(false);
                }
            }, FIRE_GRACE_TICKS);
        } else {
            f.entity.setInvulnerable(false);
        }
    }

    /** Marks an outcome that isn't an item: plays the shared tone, then runs it. */
    private static Consumer<Tracked> odd(Consumer<Tracked> outcome) {
        return t -> {
            t.player.playSound(t.player, Sound.BLOCK_BEACON_DEACTIVATE, ODD_VOLUME, 1f);
            outcome.accept(t);
        };
    }

    private static Consumer<Tracked> effect(PotionEffectType type, int ticks, String message) {
        return t -> {
            t.player.addPotionEffect(new PotionEffect(type, ticks, 0));
            t.player.sendMessage(Component.text(message, NamedTextColor.LIGHT_PURPLE));
        };
    }

    /** Spawns the mob at the hook and reels it in along the same flight path as an item. */
    private <T extends LivingEntity> T reelMob(Tracked t, Class<T> type, String label) {
        T mob = t.anchorLoc.getWorld().spawn(t.anchorLoc, type);
        mob.setInvulnerable(true); // spawned inside lava; land() clears this
        flying.add(new Flying(mob, t.player));
        t.player.sendMessage(Component.text(label + "이(가) 딸려 나왔습니다!", NamedTextColor.RED));
        return mob;
    }

    /** "무작위 근처 지상 위치 텔레포트": a random solid spot within TELEPORT_RADIUS of the player.
     * Announced on the action bar, like the other job-skill notices.
     * Standing on an End island there's always at least the ground under their feet nearby;
     * if the dice somehow only land in the void, they stay put. */
    private void yankToRandomGround(Tracked t) {
        Player player = t.player;
        World world = player.getWorld();
        Location from = player.getLocation();
        for (int i = 0; i < 16; i++) {
            int x = from.getBlockX() + random.nextInt(TELEPORT_RADIUS * 2 + 1) - TELEPORT_RADIUS;
            int z = from.getBlockZ() + random.nextInt(TELEPORT_RADIUS * 2 + 1) - TELEPORT_RADIUS;
            Block top = world.getHighestBlockAt(x, z);
            if (top.getY() <= world.getMinHeight() || !top.getType().isSolid()) {
                continue;
            }
            Location to = top.getLocation().add(0.5, 1, 0.5);
            to.setYaw(from.getYaw());
            to.setPitch(from.getPitch());
            player.teleport(to);
            break;
        }
        world.spawnParticle(Particle.PORTAL, player.getLocation(), 40, 0.5, 1, 0.5);
        player.sendActionBar(Component.text("알 수 없는 힘이 끌어당겼습니다.", NamedTextColor.DARK_PURPLE));
    }
}
