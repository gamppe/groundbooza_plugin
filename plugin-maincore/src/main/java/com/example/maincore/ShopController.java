package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopController {

    private static final long SPECIAL_PRICE = 10L;
    private static final long DYE_PRICE = 10L;
    private static final long FREE_PRICE = 0L;
    private static final long SEED_PRICE = 10L;
    private static final long SPAWN_EGG_PRICE = 500L;
    private static final long HORSE_LEAD_PRICE = 500L;
    private static final long SADDLE_PRICE = 300L;
    private static final long DYEABLE_BLOCK_PRICE = 10L;
    /** Flat price - the old 2^n ladder is gone; the anti-hoarding lever is now the purchase
     * cooldown (one deed per RESERVATION_COOLDOWN_MILLIS of real time, per player). */
    private static final long RESERVATION_PRICE = 30_000L;
    private static final long RESERVATION_COOLDOWN_MILLIS = 3 * 60 * 60_000L;
    private static final int MAX_RESERVATION_DEEDS = 5;

    private static final Map<Material, String> DYE_NAMES = new java.util.LinkedHashMap<>();
    static {
        DYE_NAMES.put(Material.WHITE_DYE, "흰색 염료");
        DYE_NAMES.put(Material.LIGHT_GRAY_DYE, "회백색 염료");
        DYE_NAMES.put(Material.GRAY_DYE, "회색 염료");
        DYE_NAMES.put(Material.BLACK_DYE, "검은색 염료");
        DYE_NAMES.put(Material.BROWN_DYE, "갈색 염료");
        DYE_NAMES.put(Material.RED_DYE, "빨간색 염료");
        DYE_NAMES.put(Material.ORANGE_DYE, "주황색 염료");
        DYE_NAMES.put(Material.YELLOW_DYE, "노란색 염료");
        DYE_NAMES.put(Material.LIME_DYE, "연두색 염료");
        DYE_NAMES.put(Material.GREEN_DYE, "초록색 염료");
        DYE_NAMES.put(Material.CYAN_DYE, "청록색 염료");
        DYE_NAMES.put(Material.LIGHT_BLUE_DYE, "하늘색 염료");
        DYE_NAMES.put(Material.BLUE_DYE, "파란색 염료");
        DYE_NAMES.put(Material.PURPLE_DYE, "보라색 염료");
        DYE_NAMES.put(Material.MAGENTA_DYE, "자홍색 염료");
        DYE_NAMES.put(Material.PINK_DYE, "분홍색 염료");
    }

    private final MainCorePlugin plugin;
    private final NamespacedKey reservationBuyKey;
    private final Map<ShopCategory, List<ShopItem>> catalog = new EnumMap<>(ShopCategory.class);
    /** 특수구매 is split per job - a player only ever sees (and can buy from) their own job's list. */
    private final Map<Job, List<ShopItem>> specialCatalogs = new EnumMap<>(Job.class);
    private final Set<UUID> pendingReservationPurchases = ConcurrentHashMap.newKeySet();

    public ShopController(MainCorePlugin plugin) {
        this.plugin = plugin;
        this.reservationBuyKey = new NamespacedKey(plugin, "reservation_last_buy");
        for (ShopCategory category : ShopCategory.values()) {
            catalog.put(category, new ArrayList<>());
        }
        for (Job job : Job.values()) {
            specialCatalogs.put(job, new ArrayList<>());
        }
        buildSpecialCatalogs();
        buildDyeCatalog();
        buildDyeableBlockCatalog();
        buildNecessitiesCatalog();
    }

    private void buildNecessitiesCatalog() {
        HorseLeadItem leads = plugin.getHorseLeadItem();
        catalog.get(ShopCategory.NECESSITIES).add(new ShopItem("말 보관 목줄", leads.createEmpty(), HORSE_LEAD_PRICE, leads::createEmpty));
        addPlain(catalog.get(ShopCategory.NECESSITIES), Material.SADDLE, "안장", SADDLE_PRICE);
    }

    /** Every dyeable family in white - DyeRecipes guarantees each can be recoloured 1:1. */
    private void buildDyeableBlockCatalog() {
        for (Map.Entry<Material, String> entry : DyeRecipes.WHITE_BLOCKS.entrySet()) {
            addPlain(catalog.get(ShopCategory.DYEABLE_BLOCK), entry.getKey(), entry.getValue(), DYEABLE_BLOCK_PRICE);
        }
    }

    private void buildDyeCatalog() {
        List<ShopItem> items = catalog.get(ShopCategory.DYE);
        for (Map.Entry<Material, String> entry : DYE_NAMES.entrySet()) {
            Material material = entry.getKey();
            String name = entry.getValue();
            ItemStack icon = simpleIcon(material, name, NamedTextColor.AQUA);
            items.add(new ShopItem(name, icon, DYE_PRICE, () -> new ItemStack(material)));
        }
    }

    /** category null = 특수구매, which needs the job to know which list to show. */
    private List<ShopItem> catalogFor(ShopCategory category, Job job) {
        if (category != null) {
            return catalog.get(category);
        }
        return job == null ? List.of() : specialCatalogs.get(job);
    }

    private static final Map<Material, String> SEED_NAMES = new java.util.LinkedHashMap<>();
    static {
        SEED_NAMES.put(Material.WHEAT_SEEDS, "밀 씨앗");
        SEED_NAMES.put(Material.BEETROOT_SEEDS, "비트 씨앗");
        SEED_NAMES.put(Material.MELON_SEEDS, "수박씨");
        SEED_NAMES.put(Material.PUMPKIN_SEEDS, "호박씨");
        SEED_NAMES.put(Material.COCOA_BEANS, "코코아 콩");
        SEED_NAMES.put(Material.SUGAR_CANE, "사탕수수");
        SEED_NAMES.put(Material.BAMBOO, "대나무");
        SEED_NAMES.put(Material.SWEET_BERRIES, "달콤한 열매");
        SEED_NAMES.put(Material.NETHER_WART, "네더 와트");
    }

    private void buildSpecialCatalogs() {
        SpecialHoeItem hoe = plugin.getSpecialHoeItem();
        SpecialPickaxeItem pickaxe = plugin.getSpecialPickaxeItem();
        SpecialAxeItem axe = plugin.getSpecialAxeItem();
        FishingRodTierItem rod = plugin.getFishingRodTierItem();
        CompassBiomeFinderItem compass = plugin.getCompassBiomeFinderItem();
        SpeedBootsItem boots = plugin.getSpeedBootsItem();

        // Upgradeable tools are granted at level 0 here; giveGranted() bumps them to the buyer's
        // current upgrade level right before handing them over.
        List<ShopItem> farmer = specialCatalogs.get(Job.FARMER);
        farmer.add(new ShopItem("만능 개간 괭이", hoe.create(), SPECIAL_PRICE, hoe::create));
        addPlain(farmer, Material.DIRT, "흙", FREE_PRICE);
        addPlain(farmer, Material.OAK_FENCE, "참나무 울타리", FREE_PRICE);
        addPlain(farmer, Material.OAK_FENCE_GATE, "참나무 울타리 문", FREE_PRICE);
        addPlain(farmer, Material.GLOWSTONE, "발광석", FREE_PRICE);
        for (Map.Entry<Material, String> entry : SEED_NAMES.entrySet()) {
            addPlain(farmer, entry.getKey(), entry.getValue(), SEED_PRICE);
        }
        // Carrots/potatoes can't be planted as food - these tagged seeds are the only way.
        CropRules crops = plugin.getCropRules();
        for (Material crop : List.of(Material.CARROTS, Material.POTATOES)) {
            farmer.add(new ShopItem(CropRules.seedLabel(crop), crops.createSeed(crop), SEED_PRICE, () -> crops.createSeed(crop)));
        }
        // Spawn eggs start on page 2: pad the rest of page 1 with empty slots (null entries are
        // skipped by openCategory), then one egg per animal variant.
        padToNextPage(farmer);
        AnimalEggItem eggs = plugin.getAnimalEggItem();
        for (AnimalEggItem.Variant variant : AnimalEggItem.all()) {
            farmer.add(new ShopItem(variant.label() + " 스폰알", eggs.create(variant), SPAWN_EGG_PRICE, () -> eggs.create(variant)));
        }

        List<ShopItem> fisher = specialCatalogs.get(Job.FISHER);
        addPlain(fisher, Material.FISHING_ROD, "낚시대", FREE_PRICE);
        for (FishingRodTierItem.Tier tier : FishingRodTierItem.Tier.values()) {
            fisher.add(new ShopItem(tier.label, rod.create(tier), SPECIAL_PRICE, () -> rod.create(tier)));
        }

        List<ShopItem> miner = specialCatalogs.get(Job.MINER);
        miner.add(new ShopItem("자석 곡괭이", pickaxe.create(), SPECIAL_PRICE, pickaxe::create));
        addPlain(miner, Material.TORCH, "횃불", FREE_PRICE);

        List<ShopItem> builder = specialCatalogs.get(Job.BUILDER);
        builder.add(new ShopItem("지형 채우기 도끼", axe.create(), SPECIAL_PRICE, axe::create));
        for (Map.Entry<Material, String> entry : DYE_NAMES.entrySet()) {
            addPlain(builder, entry.getKey(), entry.getValue(), FREE_PRICE);
        }

        List<ShopItem> adventurer = specialCatalogs.get(Job.ADVENTURER);
        adventurer.add(new ShopItem("이동속도 신발", boots.create(), SPECIAL_PRICE, boots::create));
        adventurer.add(new ShopItem("바이옴 탐지 나침반", compass.create(), SPECIAL_PRICE, compass::create));

        ReservationDeedItem reservation = plugin.getReservationDeedItem();
        // Display-only icon - id -1 never resolves to a real row, and this is never actually
        // granted (ShopListener routes clicks on it to buyReservationDeed() instead).
        MainDatabase.PendingDeed previewDeed = new MainDatabase.PendingDeed(-1, MainDatabase.RESERVATION_OWNER, null, true);
        adventurer.add(new ShopItem("빈 토지선점권 문서",
                reservation.createBlank(previewDeed), RESERVATION_PRICE,
                () -> reservation.createBlank(previewDeed)));
    }

    private static void padToNextPage(List<ShopItem> items) {
        while (items.size() % ShopBrowseHolder.PAGE_SIZE != 0) {
            items.add(null);
        }
    }

    private void addPlain(List<ShopItem> items, Material material, String name, long price) {
        ItemStack icon = simpleIcon(material, name, NamedTextColor.AQUA);
        items.add(new ShopItem(name, icon, price, () -> new ItemStack(material)));
    }

    /** The reservation deed has its own dynamic pricing/caps, so ShopListener routes clicks on
     * it here instead of through the generic buy(). */
    public void buyReservationDeed(Player player) {
        ReservationDeedItem reservation = plugin.getReservationDeedItem();
        UUID playerId = player.getUniqueId();

        if (!plugin.getJobManager().hasJob(playerId, Job.ADVENTURER)) {
            player.sendMessage(Component.text("토지선점권 문서는 모험가만 구매할 수 있습니다.", NamedTextColor.RED));
            return;
        }

        // The actual balance check/deduct happens async, so a rapid double-click can otherwise
        // fire a second purchase before the first one's item lands back in the inventory - both
        // would then see the same stale held-count and slip past the cap. Block re-entry instead.
        if (!pendingReservationPurchases.add(playerId)) {
            player.sendMessage(Component.text("이미 구매를 처리 중입니다.", NamedTextColor.RED));
            return;
        }

        long now = System.currentTimeMillis();
        long lastBuy = player.getPersistentDataContainer().getOrDefault(reservationBuyKey, PersistentDataType.LONG, 0L);
        if (now - lastBuy < RESERVATION_COOLDOWN_MILLIS) {
            pendingReservationPurchases.remove(playerId);
            long minutesLeft = (lastBuy + RESERVATION_COOLDOWN_MILLIS - now + 59_999L) / 60_000L;
            player.sendMessage(Component.text(String.format("토지선점권 문서는 3시간에 1개만 구매할 수 있습니다. (%d시간 %d분 후)",
                    minutesLeft / 60, minutesLeft % 60), NamedTextColor.RED));
            return;
        }

        int held = countHeldReservationDeeds(player);
        if (held >= MAX_RESERVATION_DEEDS) {
            pendingReservationPurchases.remove(playerId);
            player.sendMessage(Component.text(
                    "토지선점권 문서는 최대 " + MAX_RESERVATION_DEEDS + "개까지만 보유할 수 있습니다.", NamedTextColor.RED));
            return;
        }
        if (plugin.getLandManager().countOwned(playerId) >= DeedBookHolder.SIZE) {
            pendingReservationPurchases.remove(playerId);
            player.sendMessage(Component.text("보유 가능한 땅 개수(최대 " + DeedBookHolder.SIZE + "개)를 초과할 수 없습니다.",
                    NamedTextColor.RED));
            return;
        }

        long price = RESERVATION_PRICE;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance = plugin.getMainDatabase().getBalance(playerId);
            if (balance < price) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    pendingReservationPurchases.remove(playerId);
                    player.sendMessage(Component.text(String.format("크레딧이 부족합니다. 필요: %,d, 보유: %,d", price, balance),
                            NamedTextColor.RED));
                });
                return;
            }
            long updated = plugin.getMainDatabase().addBalance(playerId, -price);
            plugin.getEconomyCache().set(playerId, updated);

            Bukkit.getScheduler().runTask(plugin, () -> {
                // The DB row (not the physical paper) is the actual entitlement, so it's
                // recorded first - the item handed out is just a regenerable printout of it.
                plugin.getLandManager().createPendingDeedAsync(playerId, null, true, deed -> {
                    if (deed == null) {
                        player.sendMessage(Component.text("구매 처리 중 오류가 발생했습니다.", NamedTextColor.RED));
                        pendingReservationPurchases.remove(playerId);
                        return;
                    }
                    player.getPersistentDataContainer().set(reservationBuyKey, PersistentDataType.LONG, System.currentTimeMillis());
                    player.getInventory().addItem(reservation.createBlank(deed));
                    player.sendMessage(Component.text(
                            "빈 토지선점권 문서를 구매했습니다. (가격: " + String.format("%,d", price) + "크레딧, 다음 구매는 3시간 후)", NamedTextColor.GREEN));
                    pendingReservationPurchases.remove(playerId);
                });
            });
        });
    }

    /** DB-backed pending deeds (unclaimed, regardless of where the physical paper actually is)
     * plus already-claimed ones showing in /땅 문서 - both count against the 5-deed cap. */
    private int countHeldReservationDeeds(Player player) {
        int claimed = 0;
        for (MainDatabase.Land land : plugin.getLandManager().getOwned(player.getUniqueId())) {
            if (land.reservation()) claimed++;
        }
        int pending = plugin.getLandManager().countPendingDeeds(player.getUniqueId(), true);
        return claimed + pending;
    }

    // ---------- menus ----------

    public void openRoot(Player player) {
        ShopRootHolder holder = new ShopRootHolder();
        Inventory inv = Bukkit.createInventory(holder, ShopRootHolder.SIZE, Component.text("상점"));
        inv.setItem(ShopRootHolder.SLOT_BUY, simpleIcon(Material.BUNDLE, "구매", NamedTextColor.GOLD));
        inv.setItem(ShopRootHolder.SLOT_SPECIAL, simpleIcon(Material.ENCHANTED_BOOK, "특수구매", NamedTextColor.LIGHT_PURPLE));
        inv.setItem(ShopRootHolder.SLOT_SELL, simpleIcon(Material.EMERALD, "판매", NamedTextColor.GREEN));
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    public void openBuyMenu(Player player) {
        ShopBuyHolder holder = new ShopBuyHolder();
        Inventory inv = Bukkit.createInventory(holder, ShopBuyHolder.SIZE, Component.text("상점 - 구매"));
        for (ShopCategory category : ShopCategory.values()) {
            inv.setItem(ShopBuyHolder.slotFor(category), simpleIcon(category.icon(), category.label(), NamedTextColor.AQUA));
        }
        inv.setItem(ShopBuyHolder.SLOT_TERRAFORM, simpleIcon(Material.GRASS_BLOCK, "테라포밍", NamedTextColor.GREEN));
        inv.setItem(ShopBuyHolder.SLOT_BACK, simpleIcon(Material.ARROW, "뒤로가기", NamedTextColor.YELLOW));
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    /** 특수구매 shows only the player's own job's list - jobless players are pointed at /직업. */
    public void openSpecialBuy(Player player, int page) {
        Job job = plugin.getJobManager().getJob(player.getUniqueId());
        if (job == null) {
            player.closeInventory();
            player.sendMessage(Component.text("특수구매는 직업이 있어야 이용할 수 있습니다. /직업 으로 먼저 선택하세요.", NamedTextColor.RED));
            return;
        }
        openCategory(player, null, job, page);
    }

    public void openCategory(Player player, ShopCategory category, int page) {
        openCategory(player, category, null, page);
    }

    private void openCategory(Player player, ShopCategory category, Job job, int page) {
        List<ShopItem> items = catalogFor(category, job);
        int maxPage = Math.max(0, (items.size() - 1) / ShopBrowseHolder.PAGE_SIZE);
        int clampedPage = Math.max(0, Math.min(page, maxPage));
        boolean hasPrev = clampedPage > 0;
        boolean hasNext = (clampedPage + 1) * ShopBrowseHolder.PAGE_SIZE < items.size();

        ShopBrowseHolder holder = new ShopBrowseHolder(category, job, clampedPage, hasPrev, hasNext);
        int totalPages = maxPage + 1;
        String label = category == null ? job.label() + " 특수구매" : category.label();
        String title = "상점 - " + label + " (" + (clampedPage + 1) + " / " + totalPages + "페이지)";
        Inventory inv = Bukkit.createInventory(holder, ShopBrowseHolder.SIZE, Component.text(title));

        if (hasPrev) {
            inv.setItem(ShopBrowseHolder.SLOT_PREV, simpleIcon(Material.NETHER_STAR, "이전 페이지", NamedTextColor.YELLOW));
        }
        if (hasNext) {
            inv.setItem(ShopBrowseHolder.SLOT_NEXT, simpleIcon(Material.NETHER_STAR, "다음 페이지", NamedTextColor.YELLOW));
        }
        inv.setItem(ShopBrowseHolder.SLOT_BACK, simpleIcon(Material.ARROW, "뒤로가기", NamedTextColor.YELLOW));

        int[] inner = ShopBrowseHolder.innerSlots();
        int start = clampedPage * ShopBrowseHolder.PAGE_SIZE;
        for (int i = 0; i < inner.length && start + i < items.size(); i++) {
            ShopItem item = items.get(start + i);
            if (item == null) {
                continue; // page-break padding
            }
            inv.setItem(inner[i], buildDisplayItem(item));
            holder.put(inner[i], start + i);
        }

        holder.setInventory(inv);
        player.openInventory(inv);
    }

    private ItemStack buildDisplayItem(ShopItem item) {
        ItemStack display = item.icon().clone();
        ItemMeta meta = display.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore()) {
            lore.addAll(meta.lore());
        }
        lore.add(Component.text(String.format("%,d 크레딧", item.price()), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("클릭하여 구매", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack simpleIcon(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    // ---------- buying ----------

    /** Null if the index is out of range (item removed from a catalog while a menu was open). */
    public ShopItem getItem(ShopCategory category, Job job, int index) {
        List<ShopItem> items = catalogFor(category, job);
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }

    /** Left-click buys 1; shift-click buys a full stack's worth (see ShopListener). Shop items
     * buy immediately - no chat confirmation, unlike the player-run-marketplace. */
    public void buy(Player player, ShopCategory category, Job job, int index, int quantity) {
        // A 특수구매 menu opened under one job must not keep working after a job change.
        if (category == null && !plugin.getJobManager().hasJob(player.getUniqueId(), job)) {
            player.closeInventory();
            player.sendMessage(Component.text("현재 직업으로는 구매할 수 없는 상품입니다.", NamedTextColor.RED));
            return;
        }
        ShopItem item = getItem(category, job, index);
        if (item == null) {
            player.sendMessage(Component.text("존재하지 않는 상품입니다.", NamedTextColor.RED));
            return;
        }
        int amount = Math.max(1, quantity);

        long totalPrice = item.price() * amount;
        if (totalPrice == 0) {
            // Free job perks (흙, 횃불, 염료...) skip the balance round-trip entirely.
            for (int i = 0; i < amount; i++) {
                giveGranted(player, item);
            }
            player.sendMessage(Component.text(amount + "개의 " + item.name() + "을(를) 받았습니다!", NamedTextColor.GREEN));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long balance = plugin.getMainDatabase().getBalance(player.getUniqueId());
            if (balance < totalPrice) {
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(
                        Component.text(String.format("크레딧이 부족합니다. 필요: %,d, 보유: %,d", totalPrice, balance),
                                NamedTextColor.RED)));
                return;
            }
            long updated = plugin.getMainDatabase().addBalance(player.getUniqueId(), -totalPrice);
            plugin.getEconomyCache().set(player.getUniqueId(), updated);

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < amount; i++) {
                    giveGranted(player, item);
                }
                player.sendMessage(Component.text(
                        amount + "개의 " + item.name() + "을(를) 구매했습니다! (" + totalPrice + "크레딧)",
                        NamedTextColor.GREEN));
            });
        });
    }

    /** Hands over a freshly-granted copy of a shop item, tagging special tools with the buyer as
     * their sole legitimate owner, and stamping job tools with the buyer's current upgrade level. */
    private void giveGranted(Player player, ShopItem item) {
        ItemStack granted = item.grant().get();
        if (plugin.isOwnerLockedTool(granted)) {
            plugin.tagToolOwner(granted, player.getUniqueId());
        }
        if (plugin.getJobManager().toolJob(granted) != null) {
            plugin.getJobManager().applyLevel(granted, plugin.getJobManager().getItemLevel(player.getUniqueId()));
        }
        player.getInventory().addItem(granted);
    }
}
