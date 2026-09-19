package com.example.serverbridge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Farm-server's copy of MainCore's special-shop tool abilities: till/harvest/fast-dig (hoe),
 * item magnet + harmless "explosion" (pickaxe), and the nearest-biome compass - read purely by
 * literal PDC namespace so this plugin has no real dependency on MainCore. Only registered when
 * MainCore is NOT present on this server (see ServerBridgePlugin.onEnable) - main-server's own
 * MainCore copy already covers those events there (with land-protection awareness this simpler
 * copy doesn't have), and running both at once would double every effect.
 *
 * The WorldEdit-style fill axe is deliberately NOT included here - it's main-server only, and
 * BoxListener also blocks it from ever reaching this server via the shared box.
 */
public class SpecialToolListener implements Listener {

    private static final NamespacedKey HOE_KEY = new NamespacedKey("maincore", "special_hoe");
    private static final NamespacedKey PICKAXE_KEY = new NamespacedKey("maincore", "special_pickaxe");
    private static final NamespacedKey COMPASS_KEY = new NamespacedKey("maincore", "special_compass");
    private static final NamespacedKey COMPASS_TARGET_KEY = new NamespacedKey("maincore", "compass_target_biome");
    private static final int COMPASS_SEARCH_RADIUS = 6400;

    private static final Set<Material> TILLABLE = Set.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.DIRT_PATH, Material.COARSE_DIRT, Material.ROOTED_DIRT);
    private static final Set<Material> FAST_DIG = Set.of(Material.DIRT, Material.GRASS_BLOCK);
    private static final double EXPLOSION_CHANCE = 0.08;

    private final Random random = new Random();

    private boolean hasMarker(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }

    // ---------- hoe: till / harvest / fast dig ----------

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // avoid double-firing (once for main hand, once for off hand)
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = event.getItem();

        if (hasMarker(hand, COMPASS_KEY)) {
            event.setCancelled(true);
            handleCompassInteract(player, hand);
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        if (hasMarker(hand, HOE_KEY)) {
            event.setCancelled(true);
            handleHoeInteract(clicked);
        }
    }

    // ---------- compass: nearest-biome finder ----------
    // Uses its own command name (groundbuza_compass_set_farm, not MainCore's groundbuza_compass_set)
    // so the two plugins never fight over registering the same command name on main-server.

    public static final List<Biome> CHOICES = List.of(
            Biome.DESERT, Biome.JUNGLE, Biome.SNOWY_PLAINS, Biome.OCEAN,
            Biome.SWAMP, Biome.SAVANNA, Biome.MUSHROOM_FIELDS, Biome.FOREST);

    private static final Map<Biome, String> BIOME_LABELS = Map.of(
            Biome.DESERT, "사막", Biome.JUNGLE, "정글", Biome.SNOWY_PLAINS, "눈지대", Biome.OCEAN, "바다",
            Biome.SWAMP, "늪", Biome.SAVANNA, "사바나", Biome.MUSHROOM_FIELDS, "버섯섬", Biome.FOREST, "숲");

    private String biomeLabel(Biome biome) {
        return BIOME_LABELS.getOrDefault(biome, biome.name());
    }

    private void handleCompassInteract(Player player, ItemStack compass) {
        Biome target = getTargetBiome(compass);
        if (target == null) {
            Component message = Component.text("찾을 바이옴을 선택하세요: ", NamedTextColor.AQUA);
            for (Biome biome : CHOICES) {
                message = message.append(Component.text("[" + biomeLabel(biome) + "]", NamedTextColor.GREEN)
                                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(
                                        "/groundbuza_compass_set_farm " + biome.name())))
                        .append(Component.text(" "));
            }
            player.sendMessage(message);
            return;
        }
        pointCompass(player, compass, target);
    }

    private Biome getTargetBiome(ItemStack compass) {
        String name = compass.getItemMeta().getPersistentDataContainer().get(COMPASS_TARGET_KEY, PersistentDataType.STRING);
        if (name == null) {
            return null;
        }
        try {
            return Biome.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Also called by CompassSetBiomeCommand right after the target is first chosen. */
    public void pointCompass(Player player, ItemStack compass, Biome target) {
        Location origin = player.getLocation();
        Location found = origin.getWorld().locateNearestBiome(origin, target, COMPASS_SEARCH_RADIUS);
        if (found == null) {
            player.sendMessage(Component.text(
                    "근처(" + COMPASS_SEARCH_RADIUS + "블록 내)에서 " + biomeLabel(target) + "을 찾지 못했습니다.", NamedTextColor.RED));
            return;
        }
        ItemMeta meta = compass.getItemMeta();
        if (meta instanceof CompassMeta compassMeta) {
            compassMeta.setLodestoneTracked(false);
            compassMeta.setLodestone(found);
            compass.setItemMeta(compassMeta);
        }
        player.sendMessage(Component.text("가장 가까운 " + biomeLabel(target) + " 방향을 가리킵니다.", NamedTextColor.GREEN));
    }

    public boolean isSpecialCompass(ItemStack item) {
        return hasMarker(item, COMPASS_KEY);
    }

    public void setTargetBiome(ItemStack item, Biome biome) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(COMPASS_TARGET_KEY, PersistentDataType.STRING, biome.name());
        item.setItemMeta(meta);
    }

    private void handleHoeInteract(Block clicked) {
        if (clicked.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge()) {
            harvest(clicked);
            return;
        }
        if (TILLABLE.contains(clicked.getType())) {
            till3x3(clicked);
        }
    }

    private void harvest(Block block) {
        Material cropType = block.getType();
        for (ItemStack drop : block.getDrops()) {
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        }
        block.setType(cropType);
    }

    private void till3x3(Block center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = center.getRelative(dx, 0, dz);
                if (TILLABLE.contains(b.getType())) {
                    b.setType(Material.FARMLAND);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (!hasMarker(event.getItemInHand(), HOE_KEY)) {
            return;
        }
        if (FAST_DIG.contains(event.getBlock().getType())) {
            event.setInstaBreak(true);
        }
    }

    // ---------- pickaxe: magnet / safe explosion ----------

    @EventHandler(ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Player player = event.getPlayer();
        if (!hasMarker(player.getInventory().getItemInMainHand(), PICKAXE_KEY)) {
            return;
        }
        for (Item itemEntity : event.getItems()) {
            player.getInventory().addItem(itemEntity.getItemStack());
            itemEntity.remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hasMarker(tool, PICKAXE_KEY)) {
            return;
        }
        if (random.nextDouble() >= EXPLOSION_CHANCE) {
            return;
        }

        Block origin = event.getBlock();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Block target = origin.getRelative(dx, dy, dz);
                    if (target.getType().isAir() || target.getType() == Material.BEDROCK) continue;
                    target.breakNaturally(tool);
                }
            }
        }
        origin.getWorld().spawnParticle(Particle.EXPLOSION, origin.getLocation().add(0.5, 0.5, 0.5), 1);
        origin.getWorld().playSound(origin.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
    }

}
