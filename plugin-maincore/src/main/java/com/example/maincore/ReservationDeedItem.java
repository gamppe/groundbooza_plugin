package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 토지선점권 (land reservation deed): claimed like a normal deed, but the land it binds to is
 * nominally owned by nobody (RESERVATION_OWNER) until it's actually sold - so no one, buyer
 * included, can build there until then. Fully tradeable in the meantime, both directly and via
 * the marketplace; whichever trade actually completes converts it into a real deed.
 */
public class ReservationDeedItem {

    public static final long DURATION_MILLIS = 3L * 24 * 60 * 60 * 1000; // 3 days
    public static final String FIXED_NAME = "임시점유중";

    private final NamespacedKey blankMarkerKey;
    private final NamespacedKey claimedMarkerKey;
    private final NamespacedKey landIdKey;
    private final NamespacedKey pendingIdKey;

    public ReservationDeedItem(MainCorePlugin plugin) {
        this.blankMarkerKey = new NamespacedKey(plugin, "reservation_blank");
        this.claimedMarkerKey = new NamespacedKey(plugin, "reservation_claimed");
        this.landIdKey = new NamespacedKey(plugin, "reservation_land_id");
        this.pendingIdKey = new NamespacedKey(plugin, "reservation_pending_id");
    }

    /** Physical copy of a DB-backed pending deed (see MainDatabase.PendingDeed) - not the
     * entitlement itself, just a printout. Losing/dropping/handing it off doesn't matter: /땅
     * 문서 can always reprint one, and only the row's owner can actually claim land with it. */
    public ItemStack createBlank(MainDatabase.PendingDeed deed) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("빈 토지선점권 문서", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("우클릭한 위치를 선점합니다.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("명목상 누구의 소유도 아니게 되며,", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("거래되기 전까지는 아무도 건축할 수 없습니다.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("유통기한: 3일 (거래소에 등록 중엔 유지)", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("가격은 보유 개수에 따라 2배씩 증가 (최대 5개)", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("거래불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("차원이동불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(blankMarkerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(pendingIdKey, PersistentDataType.INTEGER, deed.id());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isBlank(ItemStack item) {
        return hasFlag(item, blankMarkerKey);
    }

    /** Which pending_deeds DB row this physical copy points to - the actual source of truth. */
    public Integer getPendingDeedId(ItemStack item) {
        if (!isBlank(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(pendingIdKey, PersistentDataType.INTEGER);
    }

    /** The claimed deed, bound to a specific (still-unsold) reservation land. */
    public ItemStack createClaimed(MainDatabase.Land land) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof MapMeta mapMeta) {
            MapView view = Bukkit.createMap(Bukkit.getWorlds().get(0));
            view.setLocked(true);
            mapMeta.setMapView(view);
        }
        meta.addItemFlags(ItemFlag.values());
        meta.displayName(Component.text(land.name(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(claimedMarkerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(landIdKey, PersistentDataType.INTEGER, land.id());
        item.setItemMeta(meta);
        applyLore(item, land);
        return item;
    }

    public boolean isClaimed(ItemStack item) {
        return hasFlag(item, claimedMarkerKey);
    }

    public boolean isReservationDeed(ItemStack item) {
        return isBlank(item) || isClaimed(item);
    }

    public Integer getLandId(ItemStack item) {
        if (!isClaimed(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(landIdKey, PersistentDataType.INTEGER);
    }

    /** Rebuilds the lore from the land's current DB state - call after refetching it. */
    public void refreshLore(ItemStack item, MainDatabase.Land currentLand) {
        applyLore(item, currentLand);
    }

    private void applyLore(ItemStack item, MainDatabase.Land land) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();

        if (land == null) {
            lore.add(Component.text("효력 없음", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
            return;
        }

        int minX = LandGrid.minX(land.cellX());
        int minZ = LandGrid.minZ(land.cellZ());
        int maxX = LandGrid.maxX(land.cellX());
        int maxZ = LandGrid.maxZ(land.cellZ());
        lore.add(Component.text("범위: (" + minX + ", " + minZ + ") ~ (" + maxX + ", " + maxZ + ")",
                NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("차원이동불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));

        if (land.reservation()) {
            lore.add(Component.text("소유주: 없음 (매매 대상)", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            String expiry = land.reservationExpiresAt() == null ? "알 수 없음"
                    : new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(land.reservationExpiresAt()));
            lore.add(Component.text("유통기한: " + expiry, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("거래가 완료되면 정식 땅문서가 됩니다.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("효력 있음", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false));
        } else {
            // Ownership already flipped to a real player (a trade completed) - just needs a
            // final right-click to swap this item for a proper LandDeedItem.
            lore.add(Component.text("소유주: " + Bukkit.getOfflinePlayer(land.owner()).getName(),
                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("우클릭하여 정식 땅문서로 전환하세요.", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private boolean hasFlag(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }
}
