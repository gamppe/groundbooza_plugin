package com.example.serverbridge;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Farm-server copy of MainCore's CropRules: seeds never drop from anything, and MainCore's
 * tagged 당근/감자 씨앗 (a wheat seed with a "maincore:crop_seed" tag) plants the right crop.
 * Only registered when MainCore isn't on this server. The "can't plant raw carrots/potatoes"
 * rule is deliberately main-server only.
 */
public class CropRules implements Listener {

    private static final NamespacedKey SEED_CROP_KEY = new NamespacedKey("maincore", "crop_seed");
    private static final String SPAWN_OK = "maincore_seed_ok";

    private final ServerBridgePlugin plugin;

    public CropRules(ServerBridgePlugin plugin) {
        this.plugin = plugin;
    }

    public static final Set<Material> SEED_ITEMS = EnumSet.of(
            Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS,
            Material.TORCHFLOWER_SEEDS, Material.PITCHER_POD);

    public static final Map<Material, Material> CROP_TO_SEED = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.MELON_STEM, Material.MELON_SEEDS,
            Material.PUMPKIN_STEM, Material.PUMPKIN_SEEDS,
            Material.TORCHFLOWER_CROP, Material.TORCHFLOWER_SEEDS,
            Material.PITCHER_CROP, Material.PITCHER_POD);

    public static final Set<Material> GROWABLE = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.MELON_STEM, Material.PUMPKIN_STEM, Material.TORCHFLOWER_CROP, Material.PITCHER_CROP,
            Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH);

    public static Material cropFor(ItemStack seed) {
        if (seed == null) {
            return null;
        }
        if (seed.hasItemMeta()) {
            String tagged = seed.getItemMeta().getPersistentDataContainer().get(SEED_CROP_KEY, PersistentDataType.STRING);
            if (tagged != null) {
                return Material.matchMaterial(tagged);
            }
        }
        for (Map.Entry<Material, Material> entry : CROP_TO_SEED.entrySet()) {
            if (entry.getValue() == seed.getType()) return entry.getKey();
        }
        return null;
    }

    public static boolean isCustomSeed(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(SEED_CROP_KEY, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Iterator<Item> it = event.getItems().iterator();
        while (it.hasNext()) {
            Item item = it.next();
            if (SEED_ITEMS.contains(item.getItemStack().getType())) {
                it.remove();
            } else {
                item.setMetadata(SPAWN_OK, new FixedMetadataValue(plugin, true));
            }
        }
    }

    /** Mirrors MainCore: seeds only enter the world thrown by a player or via onBlockDrop. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (!SEED_ITEMS.contains(item.getItemStack().getType())) {
            return;
        }
        if (item.getThrower() != null || item.hasMetadata(SPAWN_OK)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        if (!isCustomSeed(inHand)) {
            return;
        }
        Material crop = cropFor(inHand);
        if (crop != null && event.getBlockPlaced().getType() == Material.WHEAT) {
            event.getBlockPlaced().setType(crop, false);
        }
    }

    /** Farmland can't be trampled at all - by players or mobs - so crops never get stomped. */
    @EventHandler(ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && isFarmland(event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTrample(EntityInteractEvent event) {
        if (isFarmland(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private static boolean isFarmland(Block block) {
        return block != null && block.getType() == Material.FARMLAND;
    }

    public static List<ItemStack> dropsWithoutSeeds(Block block, ItemStack tool) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack drop : block.getDrops(tool)) {
            if (!SEED_ITEMS.contains(drop.getType())) {
                out.add(drop);
            }
        }
        return out;
    }
}
