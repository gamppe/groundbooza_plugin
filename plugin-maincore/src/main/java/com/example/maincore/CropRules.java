package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The server's seed economy: seeds are never dropped by anything (crops, grass...) - the only
 * source is 농부's shop. Carrots and potatoes can't be planted as food items either; instead
 * there are dedicated 당근 씨앗 / 감자 씨앗 items (a tagged wheat-seed lookalike that places the
 * right crop block). ServerBridge carries a copy of the drop filter + custom-seed placement
 * for farm-server.
 */
public class CropRules implements Listener {

    /** Items that count as "seeds" and therefore never drop. */
    public static final Set<Material> SEED_ITEMS = EnumSet.of(
            Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS,
            Material.TORCHFLOWER_SEEDS, Material.PITCHER_POD);

    /** Farmland crops the hoe can plant/harvest and 기우제 can grow: crop block → its seed item.
     * Carrots/potatoes map to the custom seed items (see CropRules.createSeed). */
    public static final Map<Material, Material> CROP_TO_SEED = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.BEETROOTS, Material.BEETROOT_SEEDS,
            Material.MELON_STEM, Material.MELON_SEEDS,
            Material.PUMPKIN_STEM, Material.PUMPKIN_SEEDS,
            Material.TORCHFLOWER_CROP, Material.TORCHFLOWER_SEEDS,
            Material.PITCHER_CROP, Material.PITCHER_POD);

    /** Every Ageable block 기우제 advances (farmland crops plus the other growers). */
    public static final Set<Material> GROWABLE = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.MELON_STEM, Material.PUMPKIN_STEM, Material.TORCHFLOWER_CROP, Material.PITCHER_CROP,
            Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH);

    private final MainCorePlugin plugin;
    private final NamespacedKey seedCropKey;

    public CropRules(MainCorePlugin plugin) {
        this.plugin = plugin;
        this.seedCropKey = new NamespacedKey(plugin, "crop_seed");
    }

    // ---------- custom 당근/감자 seeds ----------

    /** `crop` is CARROTS or POTATOES. Looks like a wheat seed, labelled as the real thing. */
    public ItemStack createSeed(Material crop) {
        ItemStack item = new ItemStack(Material.WHEAT_SEEDS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(seedLabel(crop), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("경작지에 심으면 " + (crop == Material.CARROTS ? "당근" : "감자") + "이 자랍니다",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(seedCropKey, PersistentDataType.STRING, crop.name());
        item.setItemMeta(meta);
        return item;
    }

    public static String seedLabel(Material crop) {
        return crop == Material.CARROTS ? "당근 씨앗" : "감자 씨앗";
    }

    /** The crop block a seed item plants, or null if it's not a seed at all. Custom carrot/potato
     * seeds resolve to CARROTS/POTATOES; plain seeds to their vanilla crop. */
    public Material cropFor(ItemStack seed) {
        if (seed == null) {
            return null;
        }
        if (seed.hasItemMeta()) {
            String tagged = seed.getItemMeta().getPersistentDataContainer().get(seedCropKey, PersistentDataType.STRING);
            if (tagged != null) {
                return Material.matchMaterial(tagged);
            }
        }
        for (Map.Entry<Material, Material> entry : CROP_TO_SEED.entrySet()) {
            if (entry.getValue() == seed.getType()) return entry.getKey();
        }
        return null;
    }

    public boolean isCustomSeed(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(seedCropKey, PersistentDataType.STRING);
    }

    // ---------- events ----------

    /** Strips seed items out of every player-caused block drop (crops, grass, ferns...). Runs
     * early so later listeners (농부's 행운) only ever see the filtered list. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Iterator<Item> it = event.getItems().iterator();
        while (it.hasNext()) {
            if (SEED_ITEMS.contains(it.next().getItemStack().getType())) {
                it.remove();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();
        Material placed = event.getBlockPlaced().getType();
        if (isCustomSeed(inHand)) {
            // A tagged wheat seed just placed WHEAT - swap it for the crop it actually is.
            Material crop = cropFor(inHand);
            if (crop != null && placed == Material.WHEAT) {
                event.getBlockPlaced().setType(crop, false);
            }
            return;
        }
        if (placed == Material.CARROTS || placed == Material.POTATOES) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text(
                    "당근/감자는 직접 심을 수 없습니다. 농부 특수상점의 " + seedLabel(placed) + "을 사용하세요.", NamedTextColor.RED));
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

    /** Vanilla drops for a block with seeds removed - for code paths that drop items directly
     * (the hoe's range harvest) instead of going through BlockDropItemEvent. */
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
