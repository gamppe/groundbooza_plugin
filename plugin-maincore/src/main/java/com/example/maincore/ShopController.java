package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShopController {

    private static final long RENTAL_PRICE = 10L;
    private static final long SPECIAL_PRICE = 10L;
    private static final long DYE_PRICE = 10L;
    private static final long RESERVATION_BASE_PRICE = 20_000L;
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
    private final Map<ShopCategory, List<ShopItem>> catalog = new EnumMap<>(ShopCategory.class);
    private final List<ShopItem> specialCatalog = new ArrayList<>();
    private final Set<UUID> pendingReservationPurchases = ConcurrentHashMap.newKeySet();

    public ShopController(MainCorePlugin plugin) {
        this.plugin = plugin;
        for (ShopCategory category : ShopCategory.values()) {
            catalog.put(category, new ArrayList<>());
        }
        buildToolRentalCatalog();
        buildSpecialCatalog();
        buildDyeCatalog();
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

    private List<ShopItem> catalogFor(ShopCategory category) {
        return category == null ? specialCatalog : catalog.get(category);
    }

    private void buildSpecialCatalog() {
        SpecialHoeItem hoe = plugin.getSpecialHoeItem();
        SpecialPickaxeItem pickaxe = plugin.getSpecialPickaxeItem();
        SpecialAxeItem axe = plugin.getSpecialAxeItem();
        FishingRodTierItem rod = plugin.getFishingRodTierItem();
        CompassBiomeFinderItem compass = plugin.getCompassBiomeFinderItem();

        specialCatalog.add(new ShopItem("만능 개간 괭이", hoe.create(), SPECIAL_PRICE, hoe::create));
        specialCatalog.add(new ShopItem("자석 곡괭이", pickaxe.create(), SPECIAL_PRICE, pickaxe::create));
        specialCatalog.add(new ShopItem("지형 채우기 도끼", axe.create(), SPECIAL_PRICE, axe::create));
        for (FishingRodTierItem.Tier tier : FishingRodTierItem.Tier.values()) {
            specialCatalog.add(new ShopItem(tier.label, rod.create(tier), SPECIAL_PRICE, () -> rod.create(tier)));
        }
        specialCatalog.add(new ShopItem("바이옴 탐지 나침반", compass.create(), SPECIAL_PRICE, compass::create));

        ReservationDeedItem reservation = plugin.getReservationDeedItem();
        // Display-only icon - id -1 never resolves to a real row, and this is never actually
        // granted (ShopListener routes clicks on it to buyReservationDeed() instead).
        MainDatabase.PendingDeed previewDeed = new MainDatabase.PendingDeed(-1, MainDatabase.RESERVATION_OWNER, null, true);
        specialCatalog.add(new ShopItem("빈 토지선점권 문서",
                reservation.createBlank(previewDeed), RESERVATION_BASE_PRICE,
                () -> reservation.createBlank(previewDeed)));
    }

    /** The reservation deed has its own dynamic pricing/caps, so ShopListener routes clicks on
     * it here instead of through the generic buy(). */
    public void buyReservationDeed(Player player) {
        ReservationDeedItem reservation = plugin.getReservationDeedItem();
        UUID playerId = player.getUniqueId();

        // The actual balance check/deduct happens async, so a rapid double-click can otherwise
        // fire a second purchase before the first one's item lands back in the inventory - both
        // would then see the same stale held-count and slip past the cap. Block re-entry instead.
        if (!pendingReservationPurchases.add(playerId)) {
            player.sendMessage(Component.text("이미 구매를 처리 중입니다.", NamedTextColor.RED));
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

        long price = RESERVATION_BASE_PRICE * (1L << held);
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
                    player.getInventory().addItem(reservation.createBlank(deed));
                    player.sendMessage(Component.text(
                            "빈 토지선점권 문서를 구매했습니다. (가격: " + String.format("%,d", price) + "크레딧)", NamedTextColor.GREEN));
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

    private void buildToolRentalCatalog() {
        List<ShopItem> items = catalog.get(ShopCategory.TOOL_RENTAL);
        addToolTrio(items, "곡괭이", Material.GOLDEN_PICKAXE, Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE);
        addToolTrio(items, "도끼", Material.GOLDEN_AXE, Material.IRON_AXE, Material.DIAMOND_AXE);
        addToolTrio(items, "삽", Material.GOLDEN_SHOVEL, Material.IRON_SHOVEL, Material.DIAMOND_SHOVEL);
        addToolTrio(items, "검", Material.GOLDEN_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD);

        RentalToolItem rental = plugin.getRentalToolItem();
        items.add(new ShopItem("낚싯대", rental.createPreview(Material.FISHING_ROD, "낚싯대"), RENTAL_PRICE,
                () -> rental.createRental(Material.FISHING_ROD, "낚싯대")));
    }

    private void addToolTrio(List<ShopItem> items, String label, Material gold, Material iron, Material diamond) {
        RentalToolItem rental = plugin.getRentalToolItem();
        items.add(new ShopItem("금 " + label, rental.createPreview(gold, "금 " + label), RENTAL_PRICE,
                () -> rental.createRental(gold, "금 " + label)));
        items.add(new ShopItem("철 " + label, rental.createPreview(iron, "철 " + label), RENTAL_PRICE,
                () -> rental.createRental(iron, "철 " + label)));
        items.add(new ShopItem("다이아 " + label, rental.createPreview(diamond, "다이아 " + label), RENTAL_PRICE,
                () -> rental.createRental(diamond, "다이아 " + label)));
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
            ItemStack icon = simpleIcon(category.icon(), category.label(), NamedTextColor.AQUA);
            if (category == ShopCategory.TOOL_RENTAL) {
                icon.addUnsafeEnchantment(Enchantment.EFFICIENCY, 1);
            }
            inv.setItem(ShopBuyHolder.slotFor(category), icon);
        }
        inv.setItem(ShopBuyHolder.SLOT_BACK, simpleIcon(Material.ARROW, "뒤로가기", NamedTextColor.YELLOW));
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    public void openSpecialBuy(Player player, int page) {
        openCategory(player, null, page);
    }

    public void openCategory(Player player, ShopCategory category, int page) {
        List<ShopItem> items = catalogFor(category);
        int maxPage = Math.max(0, (items.size() - 1) / ShopBrowseHolder.PAGE_SIZE);
        int clampedPage = Math.max(0, Math.min(page, maxPage));
        boolean hasPrev = clampedPage > 0;
        boolean hasNext = (clampedPage + 1) * ShopBrowseHolder.PAGE_SIZE < items.size();

        ShopBrowseHolder holder = new ShopBrowseHolder(category, clampedPage, hasPrev, hasNext);
        int totalPages = maxPage + 1;
        String label = category == null ? "특수구매" : category.label();
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
    public ShopItem getItem(ShopCategory category, int index) {
        List<ShopItem> items = catalogFor(category);
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }

    /** Left-click buys 1; shift-click buys a full stack's worth (see ShopListener). Shop items
     * buy immediately - no chat confirmation, unlike the player-run-marketplace. */
    public void buy(Player player, ShopCategory category, int index, int quantity) {
        ShopItem item = getItem(category, index);
        if (item == null) {
            player.sendMessage(Component.text("존재하지 않는 상품입니다.", NamedTextColor.RED));
            return;
        }
        int amount = Math.max(1, quantity);

        if (category == ShopCategory.TOOL_RENTAL && amount == 1 && plugin.getRentalVoucherItem().consumeOne(player)) {
            giveGranted(player, item);
            player.sendMessage(Component.text("도구 대여권을 사용해 " + item.name() + "을(를) 대여했습니다.", NamedTextColor.GREEN));
            return;
        }

        long totalPrice = item.price() * amount;
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

    /** Hands over a freshly-granted copy of a shop item, tagging tool-type items (rental +
     * special tools) with the buyer as their sole legitimate owner. */
    private void giveGranted(Player player, ShopItem item) {
        ItemStack granted = item.grant().get();
        if (plugin.isOwnerLockedTool(granted)) {
            plugin.tagToolOwner(granted, player.getUniqueId());
        }
        player.getInventory().addItem(granted);
    }
}
