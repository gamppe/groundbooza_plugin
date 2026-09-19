package com.example.maincore;

import org.bukkit.Bukkit;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Shows a player-only WorldBorder around the relevant 48x48 land cell:
 * - while holding a blank (pending) deed, the cell they're currently standing in (live)
 * - while holding an owned deed, that land's fixed cell
 * - while flying inside your own land, that land's cell (so the boundary is visible
 *   even with empty hands while flying around)
 * Otherwise the player's real world border is restored.
 */
public class LandVisualizer {

    private final MainCorePlugin plugin;

    public LandVisualizer(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    tick(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L); // twice a second
    }

    private void tick(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            hand = player.getInventory().getItemInOffHand();
        }

        if (plugin.getPendingDeedItem().isPending(hand) || plugin.getReservationDeedItem().isBlank(hand)) {
            int cellX = LandGrid.cellX(player.getLocation());
            int cellZ = LandGrid.cellZ(player.getLocation());
            showCellBorder(player, cellX, cellZ);
            return;
        }

        Integer landId = plugin.getDeedItem().getLandId(hand);
        if (landId != null) {
            MainDatabase.Land land = plugin.getLandManager().getLandById(landId);
            plugin.getDeedItem().refreshLore(hand, land, player.getUniqueId());
            if (land != null && land.owner().equals(player.getUniqueId())) {
                showCellBorder(player, land.cellX(), land.cellZ());
                return;
            }
            // Invalid deed (abandoned, or no longer yours after a trade) - just scrap paper,
            // no border to show for it.
        }

        Integer reservationLandId = plugin.getReservationDeedItem().getLandId(hand);
        if (reservationLandId != null) {
            MainDatabase.Land land = plugin.getLandManager().getLandById(reservationLandId);
            plugin.getReservationDeedItem().refreshLore(hand, land);
            if (land != null && land.reservation() && player.getUniqueId().equals(land.holder())) {
                showCellBorder(player, land.cellX(), land.cellZ());
                return;
            }
        }

        if (player.isFlying()) {
            MainDatabase.Land here = plugin.getLandManager().getLandAt(player.getLocation());
            if (here != null && here.owner().equals(player.getUniqueId())) {
                showCellBorder(player, here.cellX(), here.cellZ());
                return;
            }
        }

        player.setWorldBorder(null); // nothing relevant - show the real border again
    }

    private void showCellBorder(Player player, int cellX, int cellZ) {
        double centerX = LandGrid.minX(cellX) + LandGrid.CELL_SIZE / 2.0;
        double centerZ = LandGrid.minZ(cellZ) + LandGrid.CELL_SIZE / 2.0;

        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(LandGrid.CELL_SIZE);
        border.setWarningDistance(0);
        border.setWarningTime(0);
        player.setWorldBorder(border);
    }
}
