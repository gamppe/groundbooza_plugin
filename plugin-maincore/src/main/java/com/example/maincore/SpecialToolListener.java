package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Abilities for the special-shop tools: 3x3 till + insta-harvest + fast dig (hoe), item magnet +
 * occasional harmless "explosion" (pickaxe), a WorldEdit-style two-click fill (axe), and a
 * nearest-biome compass. Runs on main-server; ServerBridge carries an equivalent copy for
 * farm-server for the hoe/pickaxe/compass (the axe is intentionally main-server only). */
public class SpecialToolListener implements Listener {

    private static final Set<Material> TILLABLE = Set.of(
            Material.DIRT, Material.GRASS_BLOCK, Material.DIRT_PATH, Material.COARSE_DIRT, Material.ROOTED_DIRT);
    private static final Set<Material> FAST_DIG = Set.of(Material.DIRT, Material.GRASS_BLOCK);
    private static final double EXPLOSION_CHANCE = 0.08;
    private static final long MAX_FILL_VOLUME = 10_000;
    private static final double AXE_REACH_FALLBACK = 5.5;
    private static final double AXE_REACH_BOOST = 1.0;

    private record LastFill(Map<Location, Material> originalBlocks, Material filledMaterial, int amount) {}

    private record GhostBarrier(Location location, Material originalType) {}

    private enum FillMode { REPLACE, KEEP }

    private enum PlacementMode { ALWAYS, ONLY_IF_POSSIBLE }

    private final MainCorePlugin plugin;
    private final NamespacedKey reachModifierKey;
    private final Map<UUID, Location> pendingPointA = new HashMap<>();
    private final Map<UUID, GhostBarrier> ghostBarriers = new HashMap<>();
    private final Map<UUID, LastFill> lastFills = new HashMap<>();
    private final Map<UUID, FillMode> fillModes = new HashMap<>();
    private final Map<UUID, PlacementMode> placementModes = new HashMap<>();
    private final Random random = new Random();

    public SpecialToolListener(MainCorePlugin plugin) {
        this.plugin = plugin;
        this.reachModifierKey = new NamespacedKey(plugin, "axe_reach_boost");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        boolean holdingAxe = plugin.getSpecialAxeItem().isSpecialAxe(mainHandItem);

        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            if (holdingAxe) {
                event.setCancelled(true); // axe holder can't place whatever is in their offhand
            }
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();

        if (holdingAxe && (action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR)) {
            cancelPendingSelection(player, true);
            return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.RIGHT_CLICK_AIR) {
            return;
        }
        ItemStack hand = event.getItem();

        if (plugin.getCompassBiomeFinderItem().isSpecialCompass(hand)) {
            event.setCancelled(true);
            handleCompassInteract(player, hand);
            return;
        }
        if (holdingAxe && player.isSneaking()) {
            event.setCancelled(true);
            openAxeSettings(player);
            return;
        }
        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        if (plugin.getSpecialHoeItem().isSpecialHoe(hand)) {
            event.setCancelled(true);
            handleHoeInteract(clicked);
        } else if (plugin.getSpecialAxeItem().isSpecialAxe(hand)) {
            event.setCancelled(true);
            handleAxeInteract(player, clicked);
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack previous = player.getInventory().getItem(event.getPreviousSlot());
        if (plugin.getSpecialAxeItem().isSpecialAxe(previous)) {
            cancelPendingSelection(player, true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        pendingPointA.remove(uuid);
        lastFills.remove(uuid);
        fillModes.remove(uuid);
        placementModes.remove(uuid);
        GhostBarrier ghost = ghostBarriers.remove(uuid);
        if (ghost != null) {
            revertGhostBarrierBlock(ghost);
        }
        AttributeInstance instance = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (instance != null && instance.getModifier(reachModifierKey) != null) {
            instance.removeModifier(reachModifierKey);
        }
    }

    private void cancelPendingSelection(Player player, boolean notify) {
        boolean hadPending = pendingPointA.remove(player.getUniqueId()) != null;
        if (hadPending && notify) {
            player.sendMessage(Component.text("지점 설정이 취소되었습니다.", NamedTextColor.RED));
        }
    }

    // ---------- compass: nearest-biome finder ----------

    private static final Map<Biome, String> BIOME_LABELS = Map.of(
            Biome.DESERT, "사막", Biome.JUNGLE, "정글", Biome.SNOWY_PLAINS, "눈지대", Biome.OCEAN, "바다",
            Biome.SWAMP, "늪", Biome.SAVANNA, "사바나", Biome.MUSHROOM_FIELDS, "버섯섬", Biome.FOREST, "숲");

    private String biomeLabel(Biome biome) {
        return BIOME_LABELS.getOrDefault(biome, biome.name());
    }

    private void handleCompassInteract(Player player, ItemStack compass) {
        Biome target = plugin.getCompassBiomeFinderItem().getTargetBiome(compass);
        if (target == null) {
            Component message = Component.text("찾을 바이옴을 선택하세요: ", NamedTextColor.AQUA);
            for (Biome biome : CompassBiomeFinderItem.CHOICES) {
                message = message.append(Component.text("[" + biomeLabel(biome) + "]", NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.runCommand("/groundbuza_compass_set " + biome.name())))
                        .append(Component.text(" "));
            }
            player.sendMessage(message);
            return;
        }
        pointCompass(player, compass, target);
    }

    /** Also called by CompassSetBiomeCommand right after the target is first chosen. */
    public void pointCompass(Player player, ItemStack compass, Biome target) {
        Location origin = player.getLocation();
        Location found = origin.getWorld().locateNearestBiome(origin, target, CompassBiomeFinderItem.SEARCH_RADIUS);
        if (found == null) {
            player.sendMessage(Component.text(
                    "근처(" + CompassBiomeFinderItem.SEARCH_RADIUS + "블록 내)에서 " + biomeLabel(target) + "을 찾지 못했습니다.",
                    NamedTextColor.RED));
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

    // ---------- hoe: till / harvest / fast dig ----------

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
        if (!plugin.getSpecialHoeItem().isSpecialHoe(event.getItemInHand())) {
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
        if (!plugin.getSpecialPickaxeItem().isSpecialPickaxe(player.getInventory().getItemInMainHand())) {
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
        if (!plugin.getSpecialPickaxeItem().isSpecialPickaxe(tool)) {
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
                    if (!player.isOp() && !plugin.getLandManager().canBuild(target.getLocation(), player.getUniqueId())) {
                        continue;
                    }
                    target.breakNaturally(tool);
                }
            }
        }
        origin.getWorld().spawnParticle(Particle.EXPLOSION, origin.getLocation().add(0.5, 0.5, 0.5), 1);
        origin.getWorld().playSound(origin.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
    }

    // ---------- axe: worldedit-style fill ----------

    /** Called every tick from MainCorePlugin for every online player: keeps a temporary,
     * per-player-visible BARRIER at wherever an axe-holder is aiming through open air (so they
     * have something to right-click), and draws the selection-preview particle line once a first
     * point is set. */
    public void tickWorldEditAxe() {
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            boolean holdingAxe = plugin.getSpecialAxeItem().isSpecialAxe(hand);
            double reach = updateReachModifier(player, holdingAxe);
            if (!holdingAxe) {
                clearGhostBarrier(player);
                continue;
            }

            Location eye = player.getEyeLocation();
            Vector direction = eye.getDirection();
            RayTraceResult trace = player.getWorld().rayTraceBlocks(
                    eye, direction, reach, FluidCollisionMode.NEVER, true);

            Location target;
            if (trace != null && trace.getHitBlock() != null) {
                target = trace.getHitBlock().getLocation();
                GhostBarrier currentGhost = ghostBarriers.get(player.getUniqueId());
                // The ray may just be hitting the ghost barrier we placed last tick - that's not
                // a real block, so don't tear it back down (was causing it to flicker on/off
                // every other tick).
                if (currentGhost == null || !currentGhost.location().equals(target)) {
                    clearGhostBarrier(player);
                }
            } else {
                Location endPoint = eye.clone().add(direction.clone().multiply(reach));
                target = endPoint.getBlock().getLocation();
                placeGhostBarrier(player, target);
            }

            Location pointA = pendingPointA.get(player.getUniqueId());
            if (pointA != null) {
                drawSelectionParticles(player, pointA, target);
            }
        }
    }

    /** Adds/removes the +1 block-reach modifier to match whether the axe is currently held, and
     * returns the player's current (possibly boosted) block interaction range. */
    private double updateReachModifier(Player player, boolean holdingAxe) {
        AttributeInstance instance = player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (instance == null) {
            return AXE_REACH_FALLBACK;
        }
        boolean hasModifier = instance.getModifier(reachModifierKey) != null;
        if (holdingAxe && !hasModifier) {
            instance.addModifier(new AttributeModifier(reachModifierKey, AXE_REACH_BOOST, AttributeModifier.Operation.ADD_NUMBER));
        } else if (!holdingAxe && hasModifier) {
            instance.removeModifier(reachModifierKey);
        }
        return instance.getValue();
    }

    private void placeGhostBarrier(Player player, Location target) {
        UUID uuid = player.getUniqueId();
        GhostBarrier current = ghostBarriers.get(uuid);
        if (current != null && current.location().equals(target)) {
            return;
        }
        if (current != null) {
            revertGhostBarrierBlock(current);
        }
        // Remember what was really there (water included) so removing the ghost restores it
        // exactly, instead of always dropping back to AIR - carving a hole out of a water body.
        Material original = target.getBlock().getType();
        target.getBlock().setType(Material.BARRIER, false);
        ghostBarriers.put(uuid, new GhostBarrier(target.clone(), original));
    }

    private void clearGhostBarrier(Player player) {
        GhostBarrier current = ghostBarriers.remove(player.getUniqueId());
        if (current != null) {
            revertGhostBarrierBlock(current);
        }
    }

    private void revertGhostBarrierBlock(GhostBarrier ghost) {
        Block block = ghost.location().getBlock();
        if (block.getType() == Material.BARRIER) {
            block.setType(ghost.originalType(), false);
        }
    }

    private void drawSelectionParticles(Player player, Location a, Location b) {
        Location centerA = a.clone().add(0.5, 0.5, 0.5);
        Location centerB = b.clone().add(0.5, 0.5, 0.5);
        double distance = centerA.distance(centerB);
        int steps = Math.max(1, (int) (distance * 4));
        Vector delta = centerB.toVector().subtract(centerA.toVector());
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Location point = centerA.clone().add(delta.clone().multiply(t));
            player.spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
        }
    }

    private void handleAxeInteract(Player player, Block clicked) {
        UUID uuid = player.getUniqueId();
        Location clickedLoc = clicked.getLocation();

        // The clicked block might be our own temporary ghost barrier - it's served its purpose.
        GhostBarrier ghost = ghostBarriers.get(uuid);
        if (ghost != null && ghost.location().equals(clickedLoc)) {
            ghostBarriers.remove(uuid);
            revertGhostBarrierBlock(ghost);
        }

        Location a = pendingPointA.get(uuid);
        if (a == null) {
            pendingPointA.put(uuid, clickedLoc);
            player.sendMessage(Component.text("1번째 지점을 지정했습니다. 반대쪽 모서리를 우클릭하세요.", NamedTextColor.AQUA));
            return;
        }
        pendingPointA.remove(uuid);
        Location b = clickedLoc;
        if (!a.getWorld().equals(b.getWorld())) {
            player.sendMessage(Component.text("같은 월드 안에서만 지정할 수 있습니다.", NamedTextColor.RED));
            return;
        }

        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand.getType().isAir() || !offhand.getType().isBlock()) {
            player.sendMessage(Component.text("왼손(오프핸드)에 채울 블록을 들어주세요.", NamedTextColor.RED));
            return;
        }
        Material material = offhand.getType();

        int minX = Math.min(a.getBlockX(), b.getBlockX());
        int maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY());
        int maxY = Math.max(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        int maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);

        if (volume > MAX_FILL_VOLUME) {
            player.sendMessage(Component.text(
                    "범위가 너무 큽니다. (최대 " + MAX_FILL_VOLUME + "블록, 선택: " + volume + "블록)", NamedTextColor.RED));
            return;
        }

        FillMode fillMode = fillModes.getOrDefault(uuid, FillMode.REPLACE);
        PlacementMode placementMode = placementModes.getOrDefault(uuid, PlacementMode.ONLY_IF_POSSIBLE);

        World world = a.getWorld();
        List<Block> candidates = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (!player.isOp() && !plugin.getLandManager().canBuild(block.getLocation(), player.getUniqueId())) {
                        player.sendMessage(Component.text(
                                "선택한 범위에 다른 사람의 땅이 포함되어 있어 채우기에 실패했습니다.", NamedTextColor.RED));
                        return;
                    }
                    if (fillMode == FillMode.KEEP && !block.getType().isAir()) {
                        continue; // keep whatever's already there
                    }
                    candidates.add(block);
                }
            }
        }

        int needed = candidates.size();
        int have = countInInventory(player, material);

        if (placementMode == PlacementMode.ONLY_IF_POSSIBLE && have < needed) {
            player.sendMessage(Component.text(
                    String.format("재료가 부족합니다. 필요: %d, 보유: %d (%s)", needed, have, material.name()),
                    NamedTextColor.RED));
            return;
        }

        if (have < needed) {
            // ALWAYS mode with insufficient materials: place as many as we can, closest to
            // point A first.
            Location centerA = a.clone().add(0.5, 0.5, 0.5);
            candidates.sort(Comparator.comparingDouble(
                    block -> block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(centerA)));
            candidates = candidates.subList(0, have);
        }

        int toPlace = candidates.size();
        if (toPlace == 0) {
            player.sendMessage(Component.text("채울 곳이 없습니다.", NamedTextColor.RED));
            return;
        }

        consumeFromInventory(player, material, toPlace);
        Map<Location, Material> originalBlocks = new HashMap<>();
        for (Block block : candidates) {
            originalBlocks.put(block.getLocation(), block.getType());
            block.setType(material, false);
        }
        lastFills.put(uuid, new LastFill(originalBlocks, material, toPlace));
        player.sendMessage(Component.text(
                String.format("%d개의 %s로 범위를 채웠습니다.", toPlace, material.name()), NamedTextColor.GREEN));
    }

    // ---------- axe: settings panel (crouch + right-click) ----------

    private void openAxeSettings(Player player) {
        AxeSettingsHolder holder = new AxeSettingsHolder();
        Inventory inv = org.bukkit.Bukkit.createInventory(holder, AxeSettingsHolder.SIZE, Component.text("지형 채우기 도끼 설정"));
        inv.setItem(AxeSettingsHolder.SLOT_FILL_MODE, buildFillModeIcon(player.getUniqueId()));
        inv.setItem(AxeSettingsHolder.SLOT_PLACEMENT_MODE, buildPlacementModeIcon(player.getUniqueId()));
        inv.setItem(AxeSettingsHolder.SLOT_UNDO, buildUndoIcon());
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAxeSettingsClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AxeSettingsHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        Inventory topInventory = event.getView().getTopInventory();
        switch (event.getSlot()) {
            case AxeSettingsHolder.SLOT_FILL_MODE -> {
                toggleFillMode(player);
                topInventory.setItem(AxeSettingsHolder.SLOT_FILL_MODE, buildFillModeIcon(player.getUniqueId()));
            }
            case AxeSettingsHolder.SLOT_PLACEMENT_MODE -> {
                togglePlacementMode(player);
                topInventory.setItem(AxeSettingsHolder.SLOT_PLACEMENT_MODE, buildPlacementModeIcon(player.getUniqueId()));
            }
            case AxeSettingsHolder.SLOT_UNDO -> {
                player.closeInventory();
                undoLastFill(player);
            }
            default -> { /* border - nothing there */ }
        }
    }

    private void toggleFillMode(Player player) {
        UUID uuid = player.getUniqueId();
        FillMode current = fillModes.getOrDefault(uuid, FillMode.REPLACE);
        fillModes.put(uuid, current == FillMode.REPLACE ? FillMode.KEEP : FillMode.REPLACE);
    }

    private void togglePlacementMode(Player player) {
        UUID uuid = player.getUniqueId();
        PlacementMode current = placementModes.getOrDefault(uuid, PlacementMode.ONLY_IF_POSSIBLE);
        placementModes.put(uuid, current == PlacementMode.ONLY_IF_POSSIBLE ? PlacementMode.ALWAYS : PlacementMode.ONLY_IF_POSSIBLE);
    }

    private ItemStack buildFillModeIcon(UUID uuid) {
        FillMode mode = fillModes.getOrDefault(uuid, FillMode.REPLACE);
        if (mode == FillMode.REPLACE) {
            return settingsIcon(Material.BRICKS, "Fill 방식: replace",
                    "범위 내 모든 블록을 덮어씁니다", "좌클릭: keep으로 전환");
        }
        return settingsIcon(Material.GLASS, "Fill 방식: keep",
                "빈 공간(공기)만 채웁니다", "좌클릭: replace로 전환");
    }

    private ItemStack buildPlacementModeIcon(UUID uuid) {
        PlacementMode mode = placementModes.getOrDefault(uuid, PlacementMode.ONLY_IF_POSSIBLE);
        if (mode == PlacementMode.ONLY_IF_POSSIBLE) {
            return settingsIcon(Material.OAK_PLANKS, "설치 방식: 가능한 경우만",
                    "재료가 부족하면 아무것도 설치하지 않습니다", "좌클릭: 항상으로 전환");
        }
        return settingsIcon(Material.OAK_SLAB, "설치 방식: 항상",
                "재료가 부족해도 가능한 만큼 설치합니다", "1번 지점에서 가까운 곳부터 설치", "좌클릭: 가능한 경우만으로 전환");
    }

    private ItemStack buildUndoIcon() {
        return settingsIcon(Material.ARROW, "되돌리기", "가장 최근 변경사항을 되돌립니다");
    }

    private ItemStack settingsIcon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        List<Component> loreLines = new ArrayList<>();
        for (String line : lore) {
            loreLines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreLines);
        item.setItemMeta(meta);
        return item;
    }

    /** Called when the undo icon in the axe settings panel is clicked. remove() makes a repeat
     * click a no-op. */
    public void undoLastFill(Player player) {
        LastFill last = lastFills.remove(player.getUniqueId());
        if (last == null) {
            player.sendMessage(Component.text("이미 처리되었거나 되돌릴 작업이 없습니다.", NamedTextColor.RED));
            return;
        }
        for (Map.Entry<Location, Material> entry : last.originalBlocks().entrySet()) {
            entry.getKey().getBlock().setType(entry.getValue(), false);
        }
        refundMaterial(player, last.filledMaterial(), last.amount());
        player.sendMessage(Component.text("마지막 채우기 작업을 되돌리고 재료를 돌려받았습니다.", NamedTextColor.GREEN));
    }

    private void refundMaterial(Player player, Material material, int amount) {
        int remaining = amount;
        int maxStack = new ItemStack(material).getMaxStackSize();
        while (remaining > 0) {
            int give = Math.min(maxStack, remaining);
            player.getInventory().addItem(new ItemStack(material, give));
            remaining -= give;
        }
    }

    private int countInInventory(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private void consumeFromInventory(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) continue;
            int take = Math.min(item.getAmount(), remaining);
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) {
                player.getInventory().setItem(i, null);
            }
            remaining -= take;
        }
    }
}
