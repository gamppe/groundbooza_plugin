package com.example.serverbridge;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Random;
import java.util.function.Supplier;

/**
 * Replaces vanilla's fishing loot table for everyone. Two 어부 tracks feed in:
 * <ul>
 *   <li>낚시능력증가 (track 0) sets the fish / junk / treasure split - see RATIOS. Non-어부 use
 *       the level-0 split.</li>
 *   <li>섬세한 미끼제작 (track 1) decides which fish, but only through the 수제미끼낚시대; any
 *       other rod (or someone else's rod) only ever catches cod. From level 1 the table depends
 *       on where the hook landed (Climate), and levels 4/5 unlock lava/void fishing, handled
 *       separately by LavaVoidFishingListener with their own tables.</li>
 * </ul>
 * Farm-server copy of MainCore's FishingLoot - identical tables, minus lava/void (farm-server
 * has no Nether or End).
 */
public class FishingLoot implements Listener {

    /** {fish, junk, treasure} percentages per 낚시능력증가 level 0..5. */
    public static final int[][] RATIOS = {
            {60, 40, 0}, {70, 30, 0}, {80, 20, 0}, {75, 15, 10}, {80, 8, 12}, {83, 2, 15}};

    public enum Climate { COLD_LAND, HOT_LAND, TEMPERATE_LAND, COLD_SEA, WARM_SEA, DESERT, SWAMP, PALE_GARDEN }

    private final JobCache jobs;
    private final FishItems fish = new FishItems();
    private final Random random = new Random();

    private final WeightedTable<Supplier<ItemStack>> plainRod;
    private final WeightedTable<Supplier<ItemStack>> base;
    private final WeightedTable<Supplier<ItemStack>> coldLand1, hotLand1;
    private final WeightedTable<Supplier<ItemStack>> coldLand2, hotLand2, coldSea2, warmSea2;
    private final WeightedTable<Supplier<ItemStack>> coldSea3, warmSea3, desert3, swamp3, pale3;
    private final WeightedTable<Supplier<ItemStack>> junk;
    private final WeightedTable<Supplier<ItemStack>> treasure;

    public FishingLoot(JobCache jobs) {
        this.jobs = jobs;

        plainRod = table().add(100, plain(Material.COD));
        base = table().add(78, plain(Material.COD)).add(20, plain(Material.SALMON)).add(2, plain(Material.PUFFERFISH));

        coldLand1 = table().add(68, plain(Material.COD)).add(20, plain(Material.SALMON)).add(10, plain(Material.PUFFERFISH))
                .add(2, custom(fish, FishItems.Kind.SMELT));
        hotLand1 = table().add(53, plain(Material.COD)).add(35, plain(Material.SALMON)).add(10, plain(Material.PUFFERFISH))
                .add(2, plain(Material.TROPICAL_FISH));

        coldLand2 = table().add(65, plain(Material.COD)).add(20, plain(Material.SALMON)).add(10, plain(Material.PUFFERFISH))
                .add(5, custom(fish, FishItems.Kind.SMELT));
        hotLand2 = table().add(40, plain(Material.COD)).add(45, plain(Material.SALMON)).add(10, plain(Material.PUFFERFISH))
                .add(5, plain(Material.TROPICAL_FISH));
        coldSea2 = table().add(58, plain(Material.COD)).add(20, plain(Material.SALMON)).add(15, plain(Material.PUFFERFISH))
                .add(5, custom(fish, FishItems.Kind.OCTOPUS_INK)).add(2, custom(fish, FishItems.Kind.SHEEP_FISH));
        warmSea2 = table().add(43, plain(Material.COD)).add(40, plain(Material.SALMON)).add(15, plain(Material.PUFFERFISH))
                .add(5, custom(fish, FishItems.Kind.OCTOPUS_INK)).add(2, custom(fish, FishItems.Kind.RABBIT_FISH));

        coldSea3 = table().add(55, plain(Material.COD)).add(20, plain(Material.SALMON)).add(15, plain(Material.PUFFERFISH))
                .add(5, custom(fish, FishItems.Kind.OCTOPUS_INK)).add(3, custom(fish, FishItems.Kind.SHEEP_FISH))
                .add(2, plain(Material.TURTLE_EGG));
        warmSea3 = table().add(40, plain(Material.COD)).add(35, plain(Material.SALMON)).add(15, plain(Material.PUFFERFISH))
                .add(5, custom(fish, FishItems.Kind.OCTOPUS_INK)).add(3, custom(fish, FishItems.Kind.RABBIT_FISH))
                .add(2, plain(Material.TURTLE_EGG));
        desert3 = table().add(30, plain(Material.COD)).add(50, plain(Material.SALMON)).add(10, plain(Material.PUFFERFISH))
                .add(5, plain(Material.TROPICAL_FISH)).add(5, custom(fish, FishItems.Kind.CREEPER_FISH));
        swamp3 = table().add(30, plain(Material.COD)).add(50, plain(Material.SALMON)).add(10, plain(Material.PUFFERFISH))
                .add(5, plain(Material.TROPICAL_FISH)).add(5, custom(fish, FishItems.Kind.TADPOLE));
        pale3 = table().add(40, custom(fish, FishItems.Kind.ODD_RESIN)).add(30, plain(Material.COD))
                .add(20, plain(Material.SALMON)).add(9, plain(Material.PUFFERFISH))
                .add(1, custom(fish, FishItems.Kind.CREAKING_EYE));

        // Vanilla's junk pool minus the ink sac and (jungle) bamboo.
        junk = table().add(17, plain(Material.LILY_PAD)).add(10, damaged(Material.LEATHER_BOOTS))
                .add(10, plain(Material.LEATHER)).add(10, plain(Material.BONE)).add(10, this::waterBottle)
                .add(10, plain(Material.BOWL)).add(10, plain(Material.TRIPWIRE_HOOK)).add(10, plain(Material.ROTTEN_FLESH))
                .add(5, plain(Material.STICK)).add(5, plain(Material.STRING)).add(2, damaged(Material.FISHING_ROD));
        // Vanilla's treasure pool minus the enchanted book (enchanting is disabled server-wide,
        // so the bow and rod come out plain).
        treasure = table().add(1, plain(Material.NAME_TAG)).add(1, plain(Material.SADDLE))
                .add(1, plain(Material.NAUTILUS_SHELL)).add(1, damaged(Material.BOW)).add(1, damaged(Material.FISHING_ROD));
    }

    // ---------- event ----------

    // Cast speed is left alone: a faster bobber flies further, but the vanilla hook deletes
    // itself past 32 blocks from its owner (hardcoded, no Bukkit/Paper knob), so the extra
    // range wasn't worth the bobber vanishing on long casts.

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item caught)) {
            return;
        }
        Player player = event.getPlayer();
        int ability = jobs.level(player.getUniqueId(), JobCache.FISHER, 0);
        ItemStack rod = rodInHand(player);
        int bait = JobCache.FISHER.equals(JobCache.toolJob(rod)) && jobs.canUse(player, rod)
                ? jobs.level(player.getUniqueId(), JobCache.FISHER, 1) : -1;
        caught.setItemStack(roll(ability, bait, event.getHook().getLocation()));
    }

    /** @param bait 섬세한 미끼제작 level through the 수제미끼낚시대, or -1 for any other rod. */
    public ItemStack roll(int ability, int bait, Location hook) {
        int[] ratio = RATIOS[Math.max(0, Math.min(RATIOS.length - 1, ability))];
        int r = random.nextInt(100);
        WeightedTable<Supplier<ItemStack>> table;
        if (r < ratio[0]) {
            table = fishTable(bait, classify(hook));
        } else if (r < ratio[0] + ratio[1]) {
            table = junk;
        } else {
            table = treasure;
        }
        return table.roll(random).get();
    }

    private WeightedTable<Supplier<ItemStack>> fishTable(int bait, Climate climate) {
        if (bait < 0) return plainRod;
        if (bait == 0) return base;
        boolean cold = climate == Climate.COLD_LAND || climate == Climate.COLD_SEA;
        boolean hot = climate == Climate.HOT_LAND || climate == Climate.WARM_SEA || climate == Climate.DESERT;
        if (bait == 1) {
            return cold ? coldLand1 : hot ? hotLand1 : base;
        }
        if (bait == 2) {
            return switch (climate) {
                case COLD_SEA -> coldSea2;
                case WARM_SEA -> warmSea2;
                default -> cold ? coldLand2 : hot ? hotLand2 : base;
            };
        }
        return switch (climate) {
            case COLD_SEA -> coldSea3;
            case WARM_SEA -> warmSea3;
            case DESERT -> desert3;
            case SWAMP -> swamp3;
            case PALE_GARDEN -> pale3;
            case COLD_LAND -> coldLand2;
            case HOT_LAND -> hotLand2;
            default -> base;
        };
    }

    /** Named biomes first (any ocean, desert, swamp/mangrove, pale garden), then plain biome
     * temperature for the rest: 0.3 and below is cold (taiga, snowy, peaks), 0.95 and above
     * hot (jungle, savanna, badlands), in between temperate. Oceans are "warm" only if their
     * key says so (warm / lukewarm); frozen, cold and plain oceans all count as cold sea. */
    public static Climate classify(Location loc) {
        String key = loc.getBlock().getBiome().getKey().getKey();
        if (key.contains("ocean")) return key.contains("warm") ? Climate.WARM_SEA : Climate.COLD_SEA;
        if (key.contains("pale_garden")) return Climate.PALE_GARDEN;
        if (key.contains("desert")) return Climate.DESERT;
        if (key.contains("swamp")) return Climate.SWAMP;
        double temp = loc.getWorld().getTemperature(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (temp <= 0.3) return Climate.COLD_LAND;
        if (temp >= 0.95) return Climate.HOT_LAND;
        return Climate.TEMPERATE_LAND;
    }

    /** The rod actually being used: main hand if it's a rod, otherwise the off hand. */
    public static ItemStack rodInHand(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        return main.getType() == Material.FISHING_ROD ? main : player.getInventory().getItemInOffHand();
    }

    // ---------- table helpers ----------

    private static WeightedTable<Supplier<ItemStack>> table() {
        return new WeightedTable<>();
    }

    private static Supplier<ItemStack> plain(Material material) {
        return () -> new ItemStack(material);
    }

    private static Supplier<ItemStack> custom(FishItems fish, FishItems.Kind kind) {
        return () -> fish.create(kind);
    }

    /** Vanilla junk-style: 0~90% of max durability already used. */
    private Supplier<ItemStack> damaged(Material material) {
        return () -> {
            ItemStack item = new ItemStack(material);
            if (item.getItemMeta() instanceof Damageable meta) {
                meta.setDamage((int) (material.getMaxDurability() * random.nextDouble() * 0.9));
                item.setItemMeta(meta);
            }
            return item;
        };
    }

    private ItemStack waterBottle() {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        item.setItemMeta(meta);
        return item;
    }
}
