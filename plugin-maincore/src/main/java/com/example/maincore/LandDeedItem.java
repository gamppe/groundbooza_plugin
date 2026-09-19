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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LandDeedItem {

    private final NamespacedKey landIdKey;
    private final NamespacedKey markerKey;

    public LandDeedItem(MainCorePlugin plugin) {
        this.landIdKey = new NamespacedKey(plugin, "land_id");
        this.markerKey = new NamespacedKey(plugin, "land_deed");
    }

    public ItemStack create(MainDatabase.Land land, String ownerName) {
        // FILLED_MAP purely for the "drawn map" icon look - it's given a fresh, unexplored,
        // locked MapView so it renders correctly but never functions as a real navigable map.
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof MapMeta mapMeta) {
            MapView view = Bukkit.createMap(Bukkit.getWorlds().get(0));
            view.setLocked(true);
            mapMeta.setMapView(view);
        }
        // Hides the vanilla auto-added map tooltip lines (scale/zoom level etc.) - we fully
        // control what's shown via our own lore instead.
        meta.addItemFlags(ItemFlag.values());

        meta.displayName(Component.text(land.name(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        meta.getPersistentDataContainer().set(landIdKey, PersistentDataType.INTEGER, land.id());
        meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BOOLEAN, true);

        item.setItemMeta(meta);
        applyLore(item, land, ownerName, true);
        return item;
    }

    /**
     * Rebuilds the lore from the land's *current* DB state (ownership can change via trades,
     * or the land can be abandoned/deleted) rather than whatever was true when the item was
     * first created. Call this periodically while the item is being held.
     */
    public void refreshLore(ItemStack item, MainDatabase.Land currentLand, UUID viewer) {
        if (currentLand == null) {
            applyLore(item, null, null, false);
            return;
        }
        String ownerName = Bukkit.getOfflinePlayer(currentLand.owner()).getName();
        boolean valid = currentLand.owner().equals(viewer);
        applyLore(item, currentLand, ownerName, valid);
    }

    private void applyLore(ItemStack item, MainDatabase.Land land, String ownerName, boolean valid) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();

        if (land != null) {
            int minX = LandGrid.minX(land.cellX());
            int minZ = LandGrid.minZ(land.cellZ());
            int maxX = LandGrid.maxX(land.cellX());
            int maxZ = LandGrid.maxZ(land.cellZ());
            lore.add(Component.text("소유주: " + ownerName, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("범위: (" + minX + ", " + minZ + ") ~ (" + maxX + ", " + maxZ + ")",
                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("차원이동불가", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        }

        lore.add(valid
                ? Component.text("효력 있음", NamedTextColor.BLUE).decoration(TextDecoration.ITALIC, false)
                : Component.text("효력 없음", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    public boolean isDeed(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        Boolean flag = item.getItemMeta().getPersistentDataContainer().get(markerKey, PersistentDataType.BOOLEAN);
        return Boolean.TRUE.equals(flag);
    }

    public Integer getLandId(ItemStack item) {
        if (!isDeed(item)) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(landIdKey, PersistentDataType.INTEGER);
    }
}
