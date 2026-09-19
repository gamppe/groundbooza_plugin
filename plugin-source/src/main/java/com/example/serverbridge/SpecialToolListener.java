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
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Farm-server's copy of MainCore's special-shop tool abilities: range till/sow/harvest + fast-dig (hoe),
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
    /** MainCore's job-upgrade level, baked into each tool (0 when absent). Mirrors
     * SpecialHoeItem.tillRadius / SpecialPickaxeItem.explosionRadius: radius = 1 + level. */
    private static final NamespacedKey TOOL_LEVEL_KEY = new NamespacedKey("maincore", "tool_level");
    private static final int COMPASS_SEARCH_RADIUS = 6400;

    private static final Set<Material> TILLABLE = Set.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.DIRT_PATH, Material.COARSE_DIRT, Material.ROOTED_DIRT);
    private static final Set<Material> FAST_DIG = Set.of(Material.DIRT, Material.GRASS_BLOCK);
    /** Mirrors SpecialPickaxeItem.EXPLOSION_SIZES / EXPLOSION_CHANCES (폭발 효과 level 0..5). */
    private static final int[] EXPLOSION_SIZES = {0, 2, 3, 3, 4, 4};
    private static final double[] EXPLOSION_CHANCES = {0.0, 0.05, 0.08, 0.12, 0.12, 0.16};
    private static final float BLAST_RESISTANCE_CAP = 30f;
    private static final double MAGNET_RADIUS = 6.0;
    private static final double MAGNET_SPEED = 0.18;

    private final Random random = new Random();
    private final JobCache jobs;

    public SpecialToolListener(JobCache jobs) {
        this.jobs = jobs;
    }

    /** Marker present AND the holder is the tagged owner currently in the tool's job. */
    private boolean usable(Player player, ItemStack item, NamespacedKey key) {
        return hasMarker(item, key) && jobs.canUse(player, item);
    }

    private static void denyWrongJob(Player player) {
        player.sendMessage(Component.text("내 직업의 내 도구만 사용할 수 있습니다.", NamedTextColor.RED));
    }

    private boolean hasMarker(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }

    private int toolLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer level = item.getItemMeta().getPersistentDataContainer().get(TOOL_LEVEL_KEY, PersistentDataType.INTEGER);
        return level == null ? 0 : Math.max(0, level);
    }

    /** Mirrors SpecialHoeItem.AREA_SIZES (범위 level 0..5). */
    private static final int[] HOE_AREA_SIZES = {1, 3, 4, 5, 6, 7};

    private int hoeAreaSize(ItemStack item) {
        return HOE_AREA_SIZES[Math.min(toolLevel(item), HOE_AREA_SIZES.length - 1)];
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
            if (!jobs.canUse(player, hand)) {
                denyWrongJob(player);
                return;
            }
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
            if (!jobs.canUse(player, hand)) {
                denyWrongJob(player);
                return;
            }
            handleHoeInteract(player, hand, clicked, hoeAreaSize(hand));
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

    /** Same as MainCore's SpecialToolListener.handleHoeInteract minus land protection: harvest
     * the NxN square (no replant) if a mature crop was clicked, else till it; then sow whatever
     * seed is in the off-hand onto every free farmland block in the square. */
    private void handleHoeInteract(Player player, ItemStack hoe, Block clicked, int size) {
        int lo = -((size - 1) / 2);
        int hi = size / 2;
        boolean harvesting = isMatureCrop(clicked);
        if (!harvesting && !TILLABLE.contains(clicked.getType()) && clicked.getType() != Material.FARMLAND
                && !CropRules.GROWABLE.contains(clicked.getType())) {
            return;
        }
        for (int dx = lo; dx <= hi; dx++) {
            for (int dz = lo; dz <= hi; dz++) {
                Block b = clicked.getRelative(dx, 0, dz);
                if (harvesting) {
                    if (isMatureCrop(b)) {
                        for (ItemStack drop : CropRules.dropsWithoutSeeds(b, hoe)) {
                            b.getWorld().dropItemNaturally(b.getLocation(), drop);
                        }
                        b.setType(Material.AIR, false);
                    }
                } else if (TILLABLE.contains(b.getType()) && b.getRelative(0, 1, 0).getType().isAir()) {
                    b.setType(Material.FARMLAND);
                }
            }
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        Material crop = CropRules.cropFor(offhand);
        if (crop == null) {
            return;
        }
        for (int dx = lo; dx <= hi && offhand.getAmount() > 0; dx++) {
            for (int dz = lo; dz <= hi && offhand.getAmount() > 0; dz++) {
                Block soil = clicked.getRelative(dx, 0, dz);
                if (soil.getType() != Material.FARMLAND) {
                    soil = soil.getRelative(0, -1, 0);
                }
                if (soil.getType() != Material.FARMLAND) continue;
                Block above = soil.getRelative(0, 1, 0);
                if (!above.getType().isAir()) continue;
                above.setType(crop, false);
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                    offhand.setAmount(offhand.getAmount() - 1);
                }
            }
        }
    }

    private static boolean isMatureCrop(Block block) {
        return CropRules.GROWABLE.contains(block.getType())
                && block.getBlockData() instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (!usable(event.getPlayer(), event.getItemInHand(), HOE_KEY)) {
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
        if (!usable(player, player.getInventory().getItemInMainHand(), PICKAXE_KEY)) {
            return;
        }
        for (Item itemEntity : event.getItems()) {
            player.getInventory().addItem(itemEntity.getItemStack());
            itemEntity.remove();
        }
    }

    /** Same blast as MainCore's SpecialToolListener (minus land protection): an NxNxN cube
     * (N from EXPLOSION_SIZES, mirrors SpecialPickaxeItem.explosionSize) starting at the broken block and
     * extending away from the player along their look axis, skipping tough blocks and the
     * column under their feet. */
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!usable(player, tool, PICKAXE_KEY)) {
            return;
        }
        int level = Math.min(toolLevel(tool), EXPLOSION_SIZES.length - 1);
        int size = EXPLOSION_SIZES[level];
        if (size <= 0 || random.nextDouble() >= EXPLOSION_CHANCES[level]) {
            return; // level 0 has no blast at all
        }

        Block origin = event.getBlock();
        Vector facing = dominantAxis(player.getEyeLocation().getDirection());
        int lo = -((size - 1) / 2);
        int hi = size / 2;
        Location feet = player.getLocation();

        for (int depth = 0; depth < size; depth++) {
            for (int a = lo; a <= hi; a++) {
                for (int b = lo; b <= hi; b++) {
                    Block target = blastBlock(origin, facing, depth, a, b);
                    if (target.equals(origin)) continue;
                    if (!isBlastable(target)) continue;
                    if (target.getX() == feet.getBlockX() && target.getZ() == feet.getBlockZ()
                            && target.getY() < feet.getBlockY()) {
                        continue;
                    }
                    target.breakNaturally(tool);
                }
            }
        }
        Location center = blastBlock(origin, facing, size / 2, 0, 0).getLocation().add(0.5, 0.5, 0.5);
        origin.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
        origin.getWorld().playSound(origin.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
    }

    private static Vector dominantAxis(Vector dir) {
        double ax = Math.abs(dir.getX()), ay = Math.abs(dir.getY()), az = Math.abs(dir.getZ());
        if (ax >= ay && ax >= az) return new Vector(Math.signum(dir.getX()), 0, 0);
        if (ay >= az) return new Vector(0, Math.signum(dir.getY()), 0);
        return new Vector(0, 0, Math.signum(dir.getZ()));
    }

    private static Block blastBlock(Block origin, Vector facing, int depth, int a, int b) {
        if (facing.getX() != 0) {
            return origin.getRelative(depth * (int) facing.getX(), a, b);
        }
        if (facing.getY() != 0) {
            return origin.getRelative(a, depth * (int) facing.getY(), b);
        }
        return origin.getRelative(a, b, depth * (int) facing.getZ());
    }

    private static boolean isBlastable(Block block) {
        Material type = block.getType();
        if (type.isAir() || !type.isSolid()) {
            return false;
        }
        return type.getBlastResistance() <= BLAST_RESISTANCE_CAP;
    }

    /** Called every other tick from ServerBridgePlugin: magnet-pickaxe holders reel in nearby
     * dropped items. */
    public void tickMagnetPickaxe() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (!usable(player, hand, PICKAXE_KEY)) {
                continue;
            }
            Location pull = player.getLocation().add(0, 0.8, 0);
            for (org.bukkit.entity.Entity entity : player.getNearbyEntities(MAGNET_RADIUS, MAGNET_RADIUS, MAGNET_RADIUS)) {
                if (!(entity instanceof Item item) || item.isDead()) continue;
                Vector toPlayer = pull.toVector().subtract(item.getLocation().toVector());
                double distance = toPlayer.length();
                if (distance < 0.6 || distance > MAGNET_RADIUS) continue;
                item.setVelocity(toPlayer.normalize().multiply(MAGNET_SPEED).setY(
                        toPlayer.getY() > 0 ? Math.max(0.05, toPlayer.getY() / distance * MAGNET_SPEED) : 0));
            }
        }
    }

}
