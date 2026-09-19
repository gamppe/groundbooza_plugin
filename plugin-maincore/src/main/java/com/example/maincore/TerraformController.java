package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 상점 > 구매 > 테라포밍: paid bulk edits of the 48x48 land cell the player is standing in.
 * Every op first *plans* (scans chunk snapshots, so it's cheap) and shows a "N블록, M크레딧"
 * chat prompt; confirming re-checks ownership, charges up front, then applies the edit in
 * per-tick batches. Blocks that changed in between are skipped and refunded at the end.
 */
public class TerraformController {

    public enum Op {
        FLATTEN("평탄화", Material.GRASS_BLOCK, 150L,
                "내가 서 있는 높이부터 위의 블록을 전부 제거합니다"),
        CLEAR_PLANTS("풀 제거", Material.SHORT_GRASS, 20L,
                "땅의 잔디·나무·잎·꽃·작물·이끼·덩굴·산호를 전부 제거합니다"),
        CLEAR_LIQUIDS("액체 제거", Material.WATER_BUCKET, 20L,
                "땅의 물과 용암을 전부 제거합니다"),
        FILL_TOP("메우기", Material.DIRT, 20L,
                "서 있는 땅과 그 아래 2블록까지의 빈 공간을 흙으로 메웁니다"),
        FILL_ALL("전부 메우기", Material.COARSE_DIRT, 25L,
                "서 있는 땅 아래의 모든 빈 공간을 흙으로 메웁니다"),
        BRUSH("선택해 없애기", Material.BRUSH, 200L,
                "붓을 받습니다. 블록을 우클릭하면 같은 종류의 블록을 땅에서 전부 제거합니다");

        public final String label;
        public final Material icon;
        public final long unitPrice;
        public final String description;

        Op(String label, Material icon, long unitPrice, String description) {
            this.label = label;
            this.icon = icon;
            this.unitPrice = unitPrice;
            this.description = description;
        }
    }

    /** A scanned edit waiting for [✓]: `targets` are Block keys; `place` is what to set them to
     * (AIR for removals, DIRT for fills); `match` is what they must still be at apply time
     * (null = "anything non-air", used for FLATTEN). */
    private record Plan(Op op, World world, int cellX, int cellZ, List<Long> targets, Material place,
                        Material match, boolean waterlogOnly) {}

    private static final int BLOCKS_PER_TICK = 4000;
    public static final long BRUSH_LIFETIME_MILLIS = 10 * 60_000L;

    private static final Set<Material> EXPLICIT_PLANTS = EnumSet.of(
            Material.SHORT_GRASS, Material.TALL_GRASS, Material.FERN, Material.LARGE_FERN, Material.DEAD_BUSH,
            Material.SEAGRASS, Material.TALL_SEAGRASS, Material.KELP, Material.KELP_PLANT,
            Material.VINE, Material.GLOW_LICHEN, Material.HANGING_ROOTS, Material.SPORE_BLOSSOM,
            Material.CAVE_VINES, Material.CAVE_VINES_PLANT, Material.WEEPING_VINES, Material.WEEPING_VINES_PLANT,
            Material.TWISTING_VINES, Material.TWISTING_VINES_PLANT,
            Material.MOSS_CARPET, Material.PALE_MOSS_CARPET, Material.PALE_HANGING_MOSS,
            Material.SUGAR_CANE, Material.BAMBOO, Material.BAMBOO_SAPLING, Material.CACTUS, Material.LILY_PAD,
            Material.SWEET_BERRY_BUSH, Material.AZALEA, Material.FLOWERING_AZALEA, Material.MANGROVE_ROOTS,
            Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.BROWN_MUSHROOM_BLOCK, Material.RED_MUSHROOM_BLOCK,
            Material.MUSHROOM_STEM, Material.NETHER_WART, Material.NETHER_WART_BLOCK, Material.WARPED_WART_BLOCK,
            Material.CRIMSON_ROOTS, Material.WARPED_ROOTS, Material.NETHER_SPROUTS,
            Material.BIG_DRIPLEAF, Material.BIG_DRIPLEAF_STEM, Material.SMALL_DRIPLEAF,
            Material.CHORUS_PLANT, Material.CHORUS_FLOWER, Material.MELON, Material.PUMPKIN,
            Material.ATTACHED_MELON_STEM, Material.ATTACHED_PUMPKIN_STEM, Material.COCOA);

    private final MainCorePlugin plugin;
    private final NamespacedKey brushKey;
    private final NamespacedKey brushExpiresKey;
    private final Map<UUID, Plan> pending = new ConcurrentHashMap<>();
    /** Players whose confirmed edit is still being applied (or charged). */
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    public TerraformController(MainCorePlugin plugin) {
        this.plugin = plugin;
        this.brushKey = new NamespacedKey(plugin, "terraform_brush");
        this.brushExpiresKey = new NamespacedKey(plugin, "terraform_brush_expires");
    }

    // ---------- menu ----------

    public void openMenu(Player player) {
        TerraformHolder holder = new TerraformHolder();
        Inventory inv = Bukkit.createInventory(holder, TerraformHolder.SIZE, Component.text("상점 - 테라포밍"));
        for (Op op : Op.values()) {
            List<String> lore = new ArrayList<>();
            lore.add(op.description);
            lore.add(String.format("블록당 %,d 크레딧", op.unitPrice));
            lore.add("내가 소유한 땅 위에서만 사용할 수 있습니다");
            lore.add(op == Op.BRUSH ? "클릭: 붓 받기 (10분 후 소멸)" : "클릭: 블록 수와 비용 확인 후 진행");
            inv.setItem(TerraformHolder.slotFor(op), icon(op.icon, op.label, NamedTextColor.GREEN, lore));
        }
        inv.setItem(TerraformHolder.SLOT_BACK, icon(Material.ARROW, "뒤로가기", NamedTextColor.YELLOW, List.of()));
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    /** Menu click: hand out a brush, or scan the cell and ask for confirmation. */
    public void select(Player player, Op op) {
        if (op == Op.BRUSH) {
            player.closeInventory();
            player.getInventory().addItem(createBrush());
            player.sendMessage(Component.text("붓을 받았습니다. 내 땅에서 블록을 우클릭하면 같은 종류의 블록을 전부 제거합니다. (10분 후 소멸)",
                    NamedTextColor.GREEN));
            return;
        }
        MainDatabase.Land land = ownedLandAt(player, player.getLocation());
        if (land == null) {
            return;
        }
        player.closeInventory();
        World world = player.getWorld();
        int feetY = player.getLocation().getBlockY();
        List<Long> targets;
        Material place;
        Material match;
        switch (op) {
            case FLATTEN -> {
                targets = scan(world, land, feetY, world.getMaxHeight() - 1, t -> !t.isAir());
                place = Material.AIR;
                match = null;
            }
            case CLEAR_PLANTS -> {
                targets = scan(world, land, world.getMinHeight(), world.getMaxHeight() - 1, TerraformController::isPlant);
                place = Material.AIR;
                match = null;
            }
            case CLEAR_LIQUIDS -> {
                propose(player, op, planLiquids(world, land));
                return;
            }
            case FILL_TOP -> {
                targets = scan(world, land, feetY - 3, feetY - 1, Material::isAir);
                place = Material.DIRT;
                match = Material.AIR;
            }
            case FILL_ALL -> {
                targets = scan(world, land, world.getMinHeight(), feetY - 1, Material::isAir);
                place = Material.DIRT;
                match = Material.AIR;
            }
            default -> {
                return;
            }
        }
        propose(player, op, new Plan(op, world, land.cellX(), land.cellZ(), targets, place, match, false));
    }

    /** Brush right-click: every block of the clicked type in that cell. */
    public void brushClick(Player player, Block clicked) {
        MainDatabase.Land land = ownedLandAt(player, clicked.getLocation());
        if (land == null) {
            return;
        }
        Material type = clicked.getType();
        if (type.isAir() || type == Material.BEDROCK) {
            player.sendMessage(Component.text("제거할 수 없는 블록입니다.", NamedTextColor.RED));
            return;
        }
        World world = clicked.getWorld();
        List<Long> targets = scan(world, land, world.getMinHeight(), world.getMaxHeight() - 1, t -> t == type);
        propose(player, Op.BRUSH, new Plan(Op.BRUSH, world, land.cellX(), land.cellZ(), targets, Material.AIR, type, false));
    }

    private MainDatabase.Land ownedLandAt(Player player, Location where) {
        MainDatabase.Land land = plugin.getLandManager().getLandAt(where);
        if (land == null || !land.owner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("내가 소유한 땅 위에서만 사용할 수 있습니다.", NamedTextColor.RED));
            return null;
        }
        return land;
    }

    // ---------- scanning ----------

    private interface TypeFilter {
        boolean test(Material type);
    }

    /** Walks the cell's 3x3 chunks via snapshots (no live block access) and collects the keys of
     * every block in [minY, maxY] whose type passes the filter. */
    private List<Long> scan(World world, MainDatabase.Land land, int minY, int maxY, TypeFilter filter) {
        List<Long> out = new ArrayList<>();
        int lo = Math.max(minY, world.getMinHeight());
        int hi = Math.min(maxY, world.getMaxHeight() - 1);
        if (lo > hi) {
            return out;
        }
        int minX = LandGrid.minX(land.cellX());
        int minZ = LandGrid.minZ(land.cellZ());
        for (int cx = minX >> 4; cx <= (minX + LandGrid.CELL_SIZE - 1) >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= (minZ + LandGrid.CELL_SIZE - 1) >> 4; cz++) {
                ChunkSnapshot snap = world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = lo; y <= hi; y++) {
                            if (filter.test(snap.getBlockType(x, y, z))) {
                                out.add(Block.getBlockKey((cx << 4) + x, y, (cz << 4) + z));
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Liquids need block data (waterlogged flags), so this one scans a little more carefully:
     * water/lava/bubble columns are removed outright, waterlogged blocks are just drained. */
    private Plan planLiquids(World world, MainDatabase.Land land) {
        List<Long> removals = new ArrayList<>();
        List<Long> drains = new ArrayList<>();
        int minX = LandGrid.minX(land.cellX());
        int minZ = LandGrid.minZ(land.cellZ());
        int lo = world.getMinHeight();
        int hi = world.getMaxHeight() - 1;
        for (int cx = minX >> 4; cx <= (minX + LandGrid.CELL_SIZE - 1) >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= (minZ + LandGrid.CELL_SIZE - 1) >> 4; cz++) {
                ChunkSnapshot snap = world.getChunkAt(cx, cz).getChunkSnapshot(false, false, false);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = lo; y <= hi; y++) {
                            Material type = snap.getBlockType(x, y, z);
                            if (type == Material.WATER || type == Material.LAVA || type == Material.BUBBLE_COLUMN) {
                                removals.add(Block.getBlockKey((cx << 4) + x, y, (cz << 4) + z));
                            } else if (!type.isAir() && !type.isOccluding()
                                    && snap.getBlockData(x, y, z) instanceof Waterlogged w && w.isWaterlogged()) {
                                // Only partial blocks can be waterlogged, so full/air ones skip the
                                // (comparatively pricey) block-data lookup.
                                drains.add(Block.getBlockKey((cx << 4) + x, y, (cz << 4) + z));
                            }
                        }
                    }
                }
            }
        }
        // One plan: removals first, then drains flagged by waterlogOnly on a second plan would
        // complicate the prompt - so drains are appended and told apart at apply time by type.
        removals.addAll(drains);
        return new Plan(Op.CLEAR_LIQUIDS, world, land.cellX(), land.cellZ(), removals, Material.AIR, null, true);
    }

    private static boolean isPlant(Material type) {
        if (type.isAir()) return false;
        return EXPLICIT_PLANTS.contains(type)
                || Tag.LEAVES.isTagged(type) || Tag.LOGS.isTagged(type) || Tag.FLOWERS.isTagged(type)
                || Tag.SAPLINGS.isTagged(type) || Tag.CROPS.isTagged(type) || Tag.MOSS_BLOCKS.isTagged(type)
                || Tag.CORALS.isTagged(type) || Tag.CORAL_BLOCKS.isTagged(type) || Tag.WALL_CORALS.isTagged(type);
    }

    // ---------- confirm / apply ----------

    private void propose(Player player, Op op, Plan plan) {
        UUID uuid = player.getUniqueId();
        if (running.contains(uuid)) {
            player.sendMessage(Component.text("이전 테라포밍 작업이 아직 진행 중입니다.", NamedTextColor.RED));
            return;
        }
        int count = plan.targets().size();
        if (count == 0) {
            player.sendMessage(Component.text(op.label + ": 대상 블록이 없습니다.", NamedTextColor.YELLOW));
            return;
        }
        long cost = count * op.unitPrice;
        pending.put(uuid, plan);
        player.sendMessage(Component.text(String.format("%s: %,d블록, %,d 크레딧이 청구됩니다. 진행할까요? ", op.label, count, cost),
                        NamedTextColor.AQUA)
                .append(Component.text("[✓]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_terraform_confirm")))
                .append(Component.text(" "))
                .append(Component.text("[✗]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_terraform_cancel"))));
    }

    public void confirm(Player player) {
        UUID uuid = player.getUniqueId();
        Plan plan = pending.remove(uuid);
        if (plan == null) {
            player.sendMessage(Component.text("대기 중인 테라포밍 작업이 없습니다.", NamedTextColor.RED));
            return;
        }
        MainDatabase.Land land = plugin.getLandManager().getLandAt(plan.cellX(), plan.cellZ());
        if (land == null || !land.owner().equals(uuid)) {
            player.sendMessage(Component.text("더 이상 내 땅이 아닙니다.", NamedTextColor.RED));
            return;
        }
        if (!running.add(uuid)) {
            player.sendMessage(Component.text("이전 테라포밍 작업이 아직 진행 중입니다.", NamedTextColor.RED));
            return;
        }
        long cost = plan.targets().size() * plan.op().unitPrice;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance = plugin.getMainDatabase().getBalance(uuid);
            if (balance < cost) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    running.remove(uuid);
                    player.sendMessage(Component.text(String.format("크레딧이 부족합니다. 필요: %,d, 보유: %,d", cost, balance),
                            NamedTextColor.RED));
                });
                return;
            }
            long updated = plugin.getMainDatabase().addBalance(uuid, -cost);
            plugin.getEconomyCache().set(uuid, updated);
            Bukkit.getScheduler().runTask(plugin, () -> apply(player, plan, cost));
        });
    }

    public void cancel(Player player) {
        if (pending.remove(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("테라포밍 작업을 취소했습니다.", NamedTextColor.GRAY));
        }
    }

    /** Applies the plan BLOCKS_PER_TICK at a time, then refunds whatever no longer qualified. */
    private void apply(Player player, Plan plan, long charged) {
        UUID uuid = player.getUniqueId();
        List<Long> targets = plan.targets();
        new BukkitRunnable() {
            int index = 0;
            int changed = 0;

            @Override
            public void run() {
                int end = Math.min(targets.size(), index + BLOCKS_PER_TICK);
                for (; index < end; index++) {
                    Block block = plan.world().getBlockAtKey(targets.get(index));
                    if (applyOne(plan, block)) {
                        changed++;
                    }
                }
                if (index < targets.size()) {
                    return;
                }
                cancel();
                running.remove(uuid);
                long actual = changed * plan.op().unitPrice;
                long refund = charged - actual;
                if (refund > 0) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        long updated = plugin.getMainDatabase().addBalance(uuid, refund);
                        plugin.getEconomyCache().set(uuid, updated);
                    });
                }
                player.sendMessage(Component.text(
                        String.format("%s 완료: %,d블록, %,d 크레딧 청구", plan.op().label, changed, actual), NamedTextColor.GREEN));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** True if the block was actually edited. Re-checks the block so an edit made in between
     * (a chest someone placed, water that already drained) isn't billed. */
    private boolean applyOne(Plan plan, Block block) {
        Material type = block.getType();
        if (plan.waterlogOnly()) {
            if (type == Material.WATER || type == Material.LAVA || type == Material.BUBBLE_COLUMN) {
                block.setType(Material.AIR, false);
                return true;
            }
            if (block.getBlockData() instanceof Waterlogged w && w.isWaterlogged()) {
                w.setWaterlogged(false);
                block.setBlockData(w, false);
                return true;
            }
            return false;
        }
        if (plan.match() == null) {
            if (type.isAir()) return false;
            if (plan.op() == Op.CLEAR_PLANTS && !isPlant(type)) return false;
        } else if (plan.match() == Material.AIR ? !type.isAir() : type != plan.match()) {
            return false;
        }
        block.setType(plan.place(), false);
        return true;
    }

    // ---------- brush item ----------

    public ItemStack createBrush() {
        long expiresAt = System.currentTimeMillis() + BRUSH_LIFETIME_MILLIS;
        ItemStack item = new ItemStack(Material.BRUSH);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("테라포밍 붓", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("내 땅의 블록을 우클릭: 같은 종류의 블록을 전부 제거", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text(String.format("블록당 %,d 크레딧", Op.BRUSH.unitPrice), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("10분 후 소멸", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("거래불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(brushKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(brushExpiresKey, PersistentDataType.LONG, expiresAt);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBrush(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(brushKey, PersistentDataType.BOOLEAN));
    }

    public boolean isExpired(ItemStack brush) {
        Long expiresAt = brush.getItemMeta().getPersistentDataContainer().get(brushExpiresKey, PersistentDataType.LONG);
        return expiresAt == null || expiresAt <= System.currentTimeMillis();
    }

    /** Called every few seconds from MainCorePlugin: expired brushes vanish from inventories. */
    public void sweepExpiredBrushes() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = player.getInventory().getContents();
            boolean removedAny = false;
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (isBrush(item) && isExpired(item)) {
                    player.getInventory().setItem(i, null);
                    removedAny = true;
                }
            }
            if (removedAny) {
                player.sendMessage(Component.text("테라포밍 붓이 소멸했습니다.", NamedTextColor.GRAY));
            }
        }
    }

    private ItemStack icon(Material material, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }
}
