package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-clicking a blank deed registers the cell you're standing on as that land.
 * Payment already happened when the blank deed itself was bought (/땅 구매), so this is
 * just the claim step - no credits change hands here.
 */
public class LandBuyListener implements Listener {

    private final MainCorePlugin plugin;

    public LandBuyListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    public static long priceForNth(int alreadyOwned) {
        if (alreadyOwned <= 0) {
            return 0L;
        }
        return 200_000L * (1L << (alreadyOwned - 1));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();

        if (plugin.getReservationDeedItem().isBlank(item)) {
            event.setCancelled(true);
            handleReservationClaim(event.getPlayer(), item);
            return;
        }
        if (plugin.getReservationDeedItem().isClaimed(item)) {
            event.setCancelled(true);
            handleReservationFinalize(event.getPlayer(), item);
            return;
        }

        Integer pendingId = plugin.getPendingDeedItem().getPendingDeedId(item);
        if (pendingId == null) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();

        MainDatabase.PendingDeed deed = plugin.getLandManager().getPendingDeedById(pendingId);
        if (deed == null || !deed.owner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("효력이 없는 문서입니다.", NamedTextColor.RED));
            return;
        }
        String landName = deed.name();

        if (plugin.getLandManager().countOwned(player.getUniqueId()) >= DeedBookHolder.SIZE) {
            player.sendMessage(Component.text("보유 가능한 땅 개수(최대 " + DeedBookHolder.SIZE + "개)를 초과할 수 없습니다.",
                    NamedTextColor.RED));
            return;
        }

        int cellX = LandGrid.cellX(player.getLocation());
        int cellZ = LandGrid.cellZ(player.getLocation());

        if (plugin.getLandManager().getLandAt(cellX, cellZ) != null) {
            player.sendMessage(Component.text("이미 누군가 소유한 구역입니다.", NamedTextColor.RED));
            return;
        }

        plugin.getLandManager().claimAsync(player.getUniqueId(), landName, cellX, cellZ, land -> {
            if (land == null) {
                player.sendMessage(Component.text("등록에 실패했습니다. (이미 소유된 구역일 수 있음)", NamedTextColor.RED));
                return;
            }

            item.setAmount(item.getAmount() - 1);
            plugin.getLandManager().deletePendingDeedAsync(pendingId, null);
            player.getInventory().addItem(plugin.getDeedItem().create(land, player.getName()));
            player.sendMessage(Component.text("'" + landName + "' 땅을 등록했습니다!", NamedTextColor.GREEN));

            markSurface(player.getWorld(), cellX, cellZ);
            plugin.getFlightListener().syncNonMoveCase(player);
            plugin.getLandBossBarManager().refresh(player, land);
        });
    }

    private static final BlockFace[] SIGN_ROTATIONS = {
            BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST, BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
            BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST, BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST
    };

    private BlockFace yawToRotation(float yaw) {
        float normalized = ((yaw % 360) + 360) % 360;
        int index = Math.round(normalized / 22.5f) & 15;
        return SIGN_ROTATIONS[index];
    }

    /** 토지선점권 claim: the cell ends up owned by nobody (RESERVATION_OWNER) with its own 3-day
     * expiry, and gets a "for sale" sign on top instead of the usual terracotta border. */
    private void handleReservationClaim(Player player, ItemStack item) {
        Integer pendingId = plugin.getReservationDeedItem().getPendingDeedId(item);
        MainDatabase.PendingDeed deed = pendingId == null ? null : plugin.getLandManager().getPendingDeedById(pendingId);
        if (deed == null || !deed.owner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("효력이 없는 문서입니다.", NamedTextColor.RED));
            return;
        }
        if (plugin.getLandManager().countOwned(player.getUniqueId()) >= DeedBookHolder.SIZE) {
            player.sendMessage(Component.text("보유 가능한 땅 개수(최대 " + DeedBookHolder.SIZE + "개)를 초과할 수 없습니다.",
                    NamedTextColor.RED));
            return;
        }

        int cellX = LandGrid.cellX(player.getLocation());
        int cellZ = LandGrid.cellZ(player.getLocation());

        if (plugin.getLandManager().getLandAt(cellX, cellZ) != null) {
            player.sendMessage(Component.text("이미 누군가 소유한 구역입니다.", NamedTextColor.RED));
            return;
        }

        long expiresAt = System.currentTimeMillis() + ReservationDeedItem.DURATION_MILLIS;
        java.util.UUID holder = player.getUniqueId();

        plugin.getLandManager().claimReservationAsync(holder, ReservationDeedItem.FIXED_NAME, cellX, cellZ, expiresAt, land -> {
            if (land == null) {
                player.sendMessage(Component.text("등록에 실패했습니다. (이미 소유된 구역일 수 있음)", NamedTextColor.RED));
                return;
            }

            item.setAmount(item.getAmount() - 1);
            plugin.getLandManager().deletePendingDeedAsync(pendingId, null);
            player.getInventory().addItem(plugin.getReservationDeedItem().createClaimed(land));
            player.sendMessage(Component.text(
                    "'" + ReservationDeedItem.FIXED_NAME + "' 땅을 선점했습니다. 거래되기 전까지는 아무도 건축할 수 없습니다.",
                    NamedTextColor.GREEN));

            placeForSaleSigns(player.getWorld(), cellX, cellZ, player.getName());
        });
    }

    // Rings the claimed plot's outer edge with "for sale" signs instead of the usual terracotta
    // border, one per edge block, each facing outward away from the plot's center.
    private void placeForSaleSigns(World world, int cellX, int cellZ, String playerName) {
        int minX = LandGrid.minX(cellX);
        int minZ = LandGrid.minZ(cellZ);
        int maxX = LandGrid.maxX(cellX);
        int maxZ = LandGrid.maxZ(cellZ);
        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;

        for (int x = minX; x <= maxX; x++) {
            placeForSaleSign(world, x, minZ, centerX, centerZ, playerName);
            placeForSaleSign(world, x, maxZ, centerX, centerZ, playerName);
        }
        for (int z = minZ; z <= maxZ; z++) {
            placeForSaleSign(world, minX, z, centerX, centerZ, playerName);
            placeForSaleSign(world, maxX, z, centerX, centerZ, playerName);
        }
    }

    private void placeForSaleSign(World world, int x, int z, double centerX, double centerZ, String playerName) {
        Block ground = world.getHighestBlockAt(x, z);
        Block signBlock = ground.getRelative(0, 1, 0);
        signBlock.setType(Material.OAK_SIGN, false);

        double dx = (x + 0.5) - centerX;
        double dz = (z + 0.5) - centerZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        if (signBlock.getBlockData() instanceof Rotatable rotatable) {
            rotatable.setRotation(yawToRotation(yaw));
            signBlock.setBlockData(rotatable);
        }
        if (signBlock.getState() instanceof Sign sign) {
            sign.line(0, Component.text("현위치 매매합니다~"));
            sign.line(1, Component.text("-" + playerName + "-"));
            sign.update(true);
        }
    }

    /** Once a claimed reservation deed's land has actually sold (owner is now a real player),
     * right-clicking it converts it in hand into a proper LandDeedItem. */
    private void handleReservationFinalize(Player player, ItemStack item) {
        Integer landId = plugin.getReservationDeedItem().getLandId(item);
        if (landId == null) {
            return;
        }
        MainDatabase.Land land = plugin.getLandManager().getLandById(landId);
        if (land == null || land.reservation() || !land.owner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("아직 정식 땅문서로 전환할 수 없습니다.", NamedTextColor.RED));
            return;
        }
        item.setAmount(item.getAmount() - 1);
        player.getInventory().addItem(plugin.getDeedItem().create(land, player.getName()));
        player.sendMessage(Component.text("정식 땅문서로 전환했습니다.", NamedTextColor.GREEN));
    }

    // Marks just the plot's outer edge with terracotta so the claim boundary is visible
    // from above, without paving over the whole 48x48 surface.
    private void markSurface(World world, int cellX, int cellZ) {
        int minX = LandGrid.minX(cellX);
        int minZ = LandGrid.minZ(cellZ);
        int maxX = LandGrid.maxX(cellX);
        int maxZ = LandGrid.maxZ(cellZ);
        for (int x = minX; x <= maxX; x++) {
            world.getHighestBlockAt(x, minZ).setType(Material.TERRACOTTA);
            world.getHighestBlockAt(x, maxZ).setType(Material.TERRACOTTA);
        }
        for (int z = minZ; z <= maxZ; z++) {
            world.getHighestBlockAt(minX, z).setType(Material.TERRACOTTA);
            world.getHighestBlockAt(maxX, z).setType(Material.TERRACOTTA);
        }
    }
}
