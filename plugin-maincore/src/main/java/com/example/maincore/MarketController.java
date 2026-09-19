package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MarketController {

    public static final long LISTING_DURATION_MILLIS = 24L * 60 * 60 * 1000;
    public static final int MAX_LISTINGS_PER_PLAYER = 5;
    private static final long TELEPORT_COST = 1000L;

    private final MainCorePlugin plugin;

    public MarketController(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void startExpirySweep() {
        new BukkitRunnable() {
            @Override
            public void run() {
                sweepExpired();
            }
        }.runTaskTimerAsynchronously(plugin, 20L * 60, 20L * 60); // once a minute
    }

    // Expiry no longer auto-returns anything - it just flips the listing to EXPIRED so the
    // seller can collect it (or re-list it) themselves from 거래현황. The one exception: a
    // 토지선점권 (reservation deed) whose own 3-day timer has ALSO already passed by the time its
    // listing expires gets destroyed outright instead.
    private void sweepExpired() {
        for (MainDatabase.MarketListing listing : plugin.getMainDatabase().getExpiredListings()) {
            if (listing.category() == MainDatabase.MarketCategory.LAND && isDestroyableReservation(listing.landId())) {
                if (!plugin.getMainDatabase().deleteListingIfExists(listing.id())) {
                    continue; // someone bought it in the same instant it expired
                }
                plugin.getLandManager().abandonAsync(listing.landId(), null);
                Player seller = Bukkit.getPlayer(listing.seller());
                if (seller != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> seller.sendMessage(Component.text(
                            "거래소에 등록한 토지선점권의 유통기한이 지나 파기되었습니다.", NamedTextColor.RED)));
                }
                continue;
            }
            if (!plugin.getMainDatabase().markExpired(listing.id())) {
                continue; // someone bought it in the same instant it expired
            }
            Player seller = Bukkit.getPlayer(listing.seller());
            if (seller != null) {
                Bukkit.getScheduler().runTask(plugin, () -> seller.sendMessage(
                        Component.text("거래소에 등록한 물건의 판매 기간이 만료되었습니다. /거래 거래소의 거래현황에서 확인하세요.",
                                NamedTextColor.YELLOW)));
            }
        }
    }

    private boolean isDestroyableReservation(int landId) {
        MainDatabase.Land land = plugin.getLandManager().getLandById(landId);
        return land != null && land.reservation() && land.reservationExpiresAt() != null
                && land.reservationExpiresAt() <= System.currentTimeMillis();
    }

    /** Left-click on an EXPIRED entry: hand the item/land back to its seller. */
    public void collectExpired(Player player, MainDatabase.MarketListing listing) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!listing.seller().equals(player.getUniqueId()) || listing.status() != MainDatabase.MarketStatus.EXPIRED) {
                return;
            }
            if (!plugin.getMainDatabase().deleteListingIfExists(listing.id())) {
                return;
            }
            if (listing.category() == MainDatabase.MarketCategory.LAND) {
                MainDatabase.Land currentLand = plugin.getLandManager().getLandById(listing.landId());
                if (currentLand != null && currentLand.reservation()) {
                    // Still nobody's - just hand the physical deed back, ownership untouched.
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.getInventory().addItem(plugin.getReservationDeedItem().createClaimed(currentLand));
                        player.sendMessage(Component.text("땅을 반환받았습니다.", NamedTextColor.GREEN));
                    });
                    return;
                }
                plugin.getLandManager().transferOwnershipAsync(listing.landId(), listing.seller(), () -> {
                    MainDatabase.Land land = plugin.getLandManager().getLandById(listing.landId());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (land != null) {
                            player.getInventory().addItem(plugin.getDeedItem().create(land, player.getName()));
                        }
                        player.sendMessage(Component.text("땅을 반환받았습니다.", NamedTextColor.GREEN));
                    });
                });
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(listing.item());
                    player.sendMessage(Component.text("아이템을 반환받았습니다.", NamedTextColor.GREEN));
                });
            }
        });
    }

    /** Right-click on an EXPIRED entry: put it straight back on the market for another 24h. */
    public void reregisterExpired(Player player, MainDatabase.MarketListing listing) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!listing.seller().equals(player.getUniqueId()) || listing.status() != MainDatabase.MarketStatus.EXPIRED) {
                return;
            }
            if (!plugin.getMainDatabase().reactivateListing(listing.id(), LISTING_DURATION_MILLIS)) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("다시 거래소에 등록했습니다. (24시간)", NamedTextColor.GREEN)));
        });
    }

    // ---------- registering ----------

    public void registerFromHand(Player player, long price, String memo) {
        if (price <= 0) {
            player.sendMessage(Component.text("가격은 0보다 커야 합니다.", NamedTextColor.RED));
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(Component.text("등록할 아이템을 주 손에 들어주세요.", NamedTextColor.RED));
            return;
        }
        if (plugin.isUntradeable(hand)) {
            player.sendMessage(Component.text("거래 불가능한 아이템입니다.", NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int activeCount = plugin.getMainDatabase().countActiveBySeller(player.getUniqueId());
            if (activeCount >= MAX_LISTINGS_PER_PLAYER) {
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(
                        Component.text("거래소에는 최대 " + MAX_LISTINGS_PER_PLAYER + "개까지만 올릴 수 있습니다.",
                                NamedTextColor.RED)));
                return;
            }

            Integer landId = plugin.getDeedItem().getLandId(hand);
            if (landId != null) {
                registerLand(player, landId, price, memo);
                return;
            }
            Integer reservationLandId = plugin.getReservationDeedItem().getLandId(hand);
            if (reservationLandId != null) {
                registerReservationLand(player, reservationLandId, price, memo);
                return;
            }
            registerItem(player, hand, price, memo);
        });
    }

    private void registerLand(Player player, int landId, long price, String memo) {
        MainDatabase.Land land = plugin.getLandManager().getLandById(landId);
        if (land == null || !land.owner().equals(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("효력이 없는 땅문서입니다.", NamedTextColor.RED)));
            return;
        }
        int listingId = plugin.getMainDatabase().insertLandListing(player.getUniqueId(), landId, price, memo,
                LISTING_DURATION_MILLIS);
        if (listingId == -1) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("등록에 실패했습니다.", NamedTextColor.RED)));
            return;
        }
        plugin.getLandManager().transferOwnershipAsync(landId, MainDatabase.MARKET_OWNER, () -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                ItemStack inHand = player.getInventory().getItemInMainHand();
                if (plugin.getDeedItem().getLandId(inHand) != null
                        && plugin.getDeedItem().getLandId(inHand) == landId) {
                    inHand.setAmount(inHand.getAmount() - 1);
                }
                player.sendMessage(Component.text("'" + land.name() + "' 땅을 거래소에 등록했습니다. (24시간)",
                        NamedTextColor.GREEN));
            });
        });
    }

    /** A 토지선점권 isn't owned by the seller in DB terms (nobody is) - physical possession is
     * what makes them the seller here, so there's no ownership check to make beyond "the land
     * still exists and hasn't already sold". */
    private void registerReservationLand(Player player, int landId, long price, String memo) {
        MainDatabase.Land land = plugin.getLandManager().getLandById(landId);
        if (land == null || !land.reservation() || !player.getUniqueId().equals(land.holder())) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("효력이 없는 토지선점권입니다.", NamedTextColor.RED)));
            return;
        }
        int listingId = plugin.getMainDatabase().insertLandListing(player.getUniqueId(), landId, price, memo,
                LISTING_DURATION_MILLIS);
        if (listingId == -1) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("등록에 실패했습니다.", NamedTextColor.RED)));
            return;
        }
        // Owner shows MARKET_OWNER while listed (matching normal lands), but is_reservation and
        // its expiry are untouched by this - updateLandOwner only ever writes owner_uuid.
        plugin.getLandManager().transferOwnershipAsync(landId, MainDatabase.MARKET_OWNER, () -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                ItemStack inHand = player.getInventory().getItemInMainHand();
                Integer inHandLandId = plugin.getReservationDeedItem().getLandId(inHand);
                if (inHandLandId != null && inHandLandId == landId) {
                    inHand.setAmount(inHand.getAmount() - 1);
                }
                player.sendMessage(Component.text("토지선점권을 거래소에 등록했습니다. (24시간)", NamedTextColor.GREEN));
            });
        });
    }

    private void registerItem(Player player, ItemStack heldItem, long price, String memo) {
        ItemStack toSell = heldItem.clone();
        int listingId = plugin.getMainDatabase().insertItemListing(player.getUniqueId(), toSell, price, memo,
                LISTING_DURATION_MILLIS);
        if (listingId == -1) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("등록에 실패했습니다.", NamedTextColor.RED)));
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.getInventory().setItemInMainHand(null);
            player.sendMessage(Component.text("아이템을 거래소에 등록했습니다. (24시간)", NamedTextColor.GREEN));
        });
    }

    // ---------- browsing ----------

    public void openRoot(Player player) {
        MarketRootHolder holder = new MarketRootHolder();
        Inventory inv = Bukkit.createInventory(holder, MarketRootHolder.SIZE, Component.text("거래소"));
        inv.setItem(MarketRootHolder.SLOT_REGISTER, simpleIcon(Material.NETHER_STAR, "아이템 등록", NamedTextColor.LIGHT_PURPLE));
        inv.setItem(MarketRootHolder.SLOT_LAND, simpleIcon(Material.MAP, "거래소 - 땅문서", NamedTextColor.AQUA));
        inv.setItem(MarketRootHolder.SLOT_ITEM, simpleIcon(Material.DIAMOND, "거래소 - 아이템", NamedTextColor.AQUA));
        inv.setItem(MarketRootHolder.SLOT_STATUS, simpleIcon(Material.ENDER_EYE, "거래현황", NamedTextColor.LIGHT_PURPLE));
        holder.setInventory(inv);
        player.openInventory(inv);
    }

    public void openBrowse(Player player, MainDatabase.MarketCategory category, int page) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            int total = plugin.getMainDatabase().countByCategory(category);
            int maxPage = Math.max(0, (total - 1) / MarketBrowseHolder.PAGE_SIZE);
            int clampedPage = Math.max(0, Math.min(page, maxPage));
            List<MainDatabase.MarketListing> listings = plugin.getMainDatabase()
                    .listByCategory(category, clampedPage * MarketBrowseHolder.PAGE_SIZE, MarketBrowseHolder.PAGE_SIZE);

            Bukkit.getScheduler().runTask(plugin, () -> {
                boolean hasPrev = clampedPage > 0;
                boolean hasNext = (clampedPage + 1) * MarketBrowseHolder.PAGE_SIZE < total;
                MarketBrowseHolder holder = new MarketBrowseHolder(category, clampedPage, hasPrev, hasNext);
                String baseTitle = category == MainDatabase.MarketCategory.LAND ? "거래소 - 땅문서" : "거래소 - 아이템";
                int totalPages = maxPage + 1;
                String title = baseTitle + " (" + (clampedPage + 1) + " / " + totalPages + "페이지)";
                Inventory inv = Bukkit.createInventory(holder, MarketBrowseHolder.SIZE, Component.text(title));

                if (hasPrev) {
                    inv.setItem(MarketBrowseHolder.SLOT_PREV, simpleIcon(Material.NETHER_STAR, "이전 페이지", NamedTextColor.YELLOW));
                }
                if (hasNext) {
                    inv.setItem(MarketBrowseHolder.SLOT_NEXT, simpleIcon(Material.NETHER_STAR, "다음 페이지", NamedTextColor.YELLOW));
                }
                inv.setItem(MarketBrowseHolder.SLOT_BACK, simpleIcon(Material.ARROW, "뒤로가기", NamedTextColor.YELLOW));

                int[] inner = MarketBrowseHolder.innerSlots();
                for (int i = 0; i < listings.size() && i < inner.length; i++) {
                    MainDatabase.MarketListing listing = listings.get(i);
                    inv.setItem(inner[i], buildDisplayItem(listing));
                    holder.put(inner[i], listing);
                }

                holder.setInventory(inv);
                player.openInventory(inv);
            });
        });
    }

    private ItemStack buildDisplayItem(MainDatabase.MarketListing listing) {
        String sellerName = Bukkit.getOfflinePlayer(listing.seller()).getName();
        ItemStack display;
        String itemName;

        if (listing.category() == MainDatabase.MarketCategory.LAND) {
            MainDatabase.Land land = plugin.getLandManager().getLandById(listing.landId());
            display = new ItemStack(Material.MAP);
            itemName = land != null ? land.name() : "(알 수 없는 땅)";
        } else {
            display = listing.item().clone();
            itemName = itemDisplayName(display);
        }

        ItemMeta meta = display.getItemMeta();
        meta.displayName(Component.text(sellerName + "님의 " + itemName, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        if (listing.memo() != null && !listing.memo().isBlank()) {
            lore.add(Component.text(listing.memo(), NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, true));
        }
        if (listing.category() == MainDatabase.MarketCategory.ITEM && meta.hasLore()) {
            lore.addAll(meta.lore());
        }
        if (listing.category() == MainDatabase.MarketCategory.LAND) {
            MainDatabase.Land land = plugin.getLandManager().getLandById(listing.landId());
            if (land != null) {
                lore.add(Component.text("범위: " + rangeText(land), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("바이옴: " + biomeText(land), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.text("우클릭을 눌러 해당 지역으로 이동 - " + TELEPORT_COST + "크레딧", NamedTextColor.DARK_AQUA)
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text(String.format("%,d 크레딧", listing.price()), NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(formatRemaining(listing.expiresAt().getTime()), NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private String rangeText(MainDatabase.Land land) {
        int minX = LandGrid.minX(land.cellX());
        int minZ = LandGrid.minZ(land.cellZ());
        int maxX = LandGrid.maxX(land.cellX());
        int maxZ = LandGrid.maxZ(land.cellZ());
        return "(" + minX + ", " + minZ + ") ~ (" + maxX + ", " + maxZ + ")";
    }

    private String biomeText(MainDatabase.Land land) {
        org.bukkit.World world = Bukkit.getWorlds().get(0);
        int centerX = (LandGrid.minX(land.cellX()) + LandGrid.maxX(land.cellX())) / 2;
        int centerZ = (LandGrid.minZ(land.cellZ()) + LandGrid.maxZ(land.cellZ())) / 2;
        int y = world.getHighestBlockYAt(centerX, centerZ);
        String raw = world.getBiome(centerX, y, centerZ).name().toLowerCase().replace('_', ' ');
        return raw.substring(0, 1).toUpperCase() + raw.substring(1);
    }

    private String itemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
        }
        String raw = item.getType().name().toLowerCase().replace('_', ' ');
        return raw.substring(0, 1).toUpperCase() + raw.substring(1);
    }

    private String formatRemaining(long expiresAtMillis) {
        long remaining = expiresAtMillis - System.currentTimeMillis();
        if (remaining <= 0) return "곧 만료";
        long hours = remaining / (60 * 60 * 1000);
        long minutes = (remaining / (60 * 1000)) % 60;
        return hours + "시간 " + minutes + "분 남음";
    }

    private ItemStack simpleIcon(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    // ---------- transaction status / payout claiming ----------

    public void openTransactionStatus(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<MainDatabase.MarketListing> listings = plugin.getMainDatabase().getListingsBySeller(player.getUniqueId());

            Bukkit.getScheduler().runTask(plugin, () -> {
                MarketStatusHolder holder = new MarketStatusHolder();
                Inventory inv = Bukkit.createInventory(holder, MarketStatusHolder.SIZE, Component.text("거래현황"));
                inv.setItem(MarketStatusHolder.SLOT_BACK, simpleIcon(Material.ARROW, "뒤로가기", NamedTextColor.YELLOW));

                int maxShown = MarketStatusHolder.ITEM_SLOTS.length;
                for (int i = 0; i < listings.size() && i < maxShown; i++) {
                    MainDatabase.MarketListing listing = listings.get(i);
                    int itemSlot = MarketStatusHolder.ITEM_SLOTS[i];
                    int statusSlot = MarketStatusHolder.STATUS_SLOTS[i];

                    inv.setItem(itemSlot, buildStatusItemDisplay(listing));
                    inv.setItem(statusSlot, buildStatusIcon(listing));
                    holder.put(statusSlot, listing);
                }

                holder.setInventory(inv);
                player.openInventory(inv);
            });
        });
    }

    private ItemStack buildStatusItemDisplay(MainDatabase.MarketListing listing) {
        String sellerName = Bukkit.getOfflinePlayer(listing.seller()).getName();
        ItemStack display;
        String itemName;
        if (listing.category() == MainDatabase.MarketCategory.LAND) {
            MainDatabase.Land land = plugin.getLandManager().getLandById(listing.landId());
            display = new ItemStack(Material.MAP);
            itemName = land != null ? land.name() : "(알 수 없는 땅)";
        } else {
            display = listing.item().clone();
            itemName = itemDisplayName(display);
        }
        ItemMeta meta = display.getItemMeta();
        meta.displayName(Component.text(sellerName + "님의 " + itemName, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack buildStatusIcon(MainDatabase.MarketListing listing) {
        return switch (listing.status()) {
            case ACTIVE -> simpleIcon(Material.BARRIER, "거래 대기중", NamedTextColor.GRAY);
            case SOLD -> buildClaimIcon(listing);
            case EXPIRED -> buildExpiredIcon();
        };
    }

    private ItemStack buildExpiredIcon() {
        ItemStack item = new ItemStack(Material.NETHERITE_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("거래 시간 만료", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("좌클릭하여 수령", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("우클릭하여 재등록", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildClaimIcon(MainDatabase.MarketListing listing) {
        long fee = listing.price() / 10;
        long net = listing.price() - fee;
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("대금 수령", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("판매금액 : " + listing.price() + "크레딧", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("수수료 : " + fee + "크레딧", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("수령액 : " + net + "크레딧", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
        ));
        item.setItemMeta(meta);
        return item;
    }

    public void claimSale(Player player, MainDatabase.MarketListing listing) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!listing.seller().equals(player.getUniqueId()) || listing.status() != MainDatabase.MarketStatus.SOLD) {
                return;
            }
            if (!plugin.getMainDatabase().deleteListingIfExists(listing.id())) {
                return; // already claimed (e.g. double click)
            }
            long fee = listing.price() / 10;
            long net = listing.price() - fee;
            long updated = plugin.getMainDatabase().addBalance(player.getUniqueId(), net);
            plugin.getEconomyCache().set(player.getUniqueId(), updated);

            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage(Component.text("거래 대금 " + net + "크레딧을 얻었습니다.", NamedTextColor.YELLOW)));
        });
    }

    // ---------- buying ----------

    public void promptPurchase(Player player, MainDatabase.MarketListing listing) {
        player.sendMessage(Component.text("정말로 구매하시겠습니까? (가격: " + listing.price() + "크레딧) ", NamedTextColor.AQUA)
                .append(Component.text("[✓]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_market_buy_confirm " + listing.id())))
                .append(Component.text(" "))
                .append(Component.text("[✗]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_market_buy_cancel"))));
    }

    public void promptTeleport(Player player, int landId) {
        plugin.getTeleportRequestManager().request(player.getUniqueId(), landId);
        MainDatabase.Land land = plugin.getLandManager().getLandById(landId);
        String name = land != null ? land.name() : "그 땅";
        player.sendMessage(Component.text(name + " 땅으로 이동하시겠습니까? (" + TELEPORT_COST + " 크레딧 소모) ", NamedTextColor.AQUA)
                .append(Component.text("[✓]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_deed_tp_confirm")))
                .append(Component.text(" "))
                .append(Component.text("[✗]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/groundbuza_deed_tp_cancel"))));
    }

    public void buy(Player buyer, int listingId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            MainDatabase.MarketListing listing = plugin.getMainDatabase().getListingById(listingId);
            if (listing == null) {
                notifyGone(buyer);
                return;
            }
            if (listing.seller().equals(buyer.getUniqueId())) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        buyer.sendMessage(Component.text("본인이 등록한 물건은 구매할 수 없습니다.", NamedTextColor.RED)));
                return;
            }
            if (!plugin.getMainDatabase().markSold(listingId)) {
                notifyGone(buyer);
                return;
            }

            long balance = plugin.getMainDatabase().getBalance(buyer.getUniqueId());
            if (balance < listing.price()) {
                plugin.getMainDatabase().revertToActive(listingId);
                Bukkit.getScheduler().runTask(plugin, () -> buyer.sendMessage(
                        Component.text(String.format("크레딧이 부족합니다. 필요: %,d, 보유: %,d", listing.price(), balance),
                                NamedTextColor.RED)));
                return;
            }

            long updatedBuyer = plugin.getMainDatabase().addBalance(buyer.getUniqueId(), -listing.price());
            plugin.getEconomyCache().set(buyer.getUniqueId(), updatedBuyer);
            // The seller does NOT get paid automatically - they collect their 90% cut later
            // from /거래 거래소's 거래현황 tab (a 10% fee is taken off the top).

            if (listing.category() == MainDatabase.MarketCategory.LAND) {
                MainDatabase.Land beforeSale = plugin.getLandManager().getLandById(listing.landId());
                boolean wasReservation = beforeSale != null && beforeSale.reservation();
                Runnable afterTransfer = () -> {
                    MainDatabase.Land land = plugin.getLandManager().getLandById(listing.landId());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (land != null) {
                            buyer.getInventory().addItem(plugin.getDeedItem().create(land, buyer.getName()));
                            String label = wasReservation ? "토지선점권" : "'" + land.name() + "' 땅";
                            buyer.sendMessage(Component.text(
                                    label + (wasReservation ? "을(를) 구매하여 정식 땅문서로 전환했습니다!" : "을(를) 구매했습니다!"),
                                    NamedTextColor.GREEN));
                        }
                        notifySeller(listing.seller(), listing.price());
                    });
                };
                if (wasReservation) {
                    plugin.getLandManager().convertReservationToNormalAsync(listing.landId(), buyer.getUniqueId(), afterTransfer);
                } else {
                    plugin.getLandManager().transferOwnershipAsync(listing.landId(), buyer.getUniqueId(), afterTransfer);
                }
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    buyer.getInventory().addItem(listing.item());
                    buyer.sendMessage(Component.text("아이템을 구매했습니다!", NamedTextColor.GREEN));
                    notifySeller(listing.seller(), listing.price());
                });
            }
        });
    }

    private void notifyGone(Player buyer) {
        Bukkit.getScheduler().runTask(plugin, () ->
                buyer.sendMessage(Component.text("이미 판매되었거나 만료된 물건입니다.", NamedTextColor.RED)));
    }

    private void notifySeller(UUID seller, long price) {
        Player sellerPlayer = Bukkit.getPlayer(seller);
        if (sellerPlayer != null) {
            sellerPlayer.sendMessage(Component.text(
                    String.format("등록하신 물건이 %,d 크레딧에 판매되었습니다. /거래 거래소의 거래현황에서 대금을 수령하세요.", price),
                    NamedTextColor.GOLD));
        }
    }
}
