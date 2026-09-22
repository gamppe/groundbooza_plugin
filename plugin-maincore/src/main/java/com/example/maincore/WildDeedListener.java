package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Right-clicking a 주인 없는 땅문서 hands over a random unclaimed overworld cell, as long as the
 * player still has room in their /땅 문서 (the same 27-land cap every other claim checks). */
public class WildDeedListener implements Listener {

    /** How far from world spawn, in 48-block cells, a granted plot may sit. */
    private static final int SEARCH_RADIUS_CELLS = 40;
    /** Candidate cells to try before settling for one without checking its terrain. */
    private static final int TERRAIN_ATTEMPTS = 40;
    private static final String LAND_NAME = "주인 없던 땅";

    private final MainCorePlugin plugin;
    private final Random random = new Random();
    /** claimAsync is async, so a fast double right-click could spend one paper twice. */
    private final Set<UUID> inFlight = new HashSet<>();

    public WildDeedListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!plugin.getWildDeedItem().isWildDeed(item)) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (plugin.getLandManager().countOwned(uuid) >= DeedBookHolder.SIZE) {
            player.sendMessage(Component.text("보유 가능한 땅 개수(최대 " + DeedBookHolder.SIZE + "개)를 초과할 수 없습니다.",
                    NamedTextColor.RED));
            return;
        }
        World world = plugin.getServer().getWorlds().get(0);
        if (world.getEnvironment() != World.Environment.NORMAL) {
            player.sendMessage(Component.text("오버월드를 찾을 수 없습니다.", NamedTextColor.RED));
            return;
        }
        if (!inFlight.add(uuid)) {
            return;
        }

        int[] cell = findFreeCell(world);
        if (cell == null) {
            inFlight.remove(uuid);
            player.sendMessage(Component.text("비어있는 땅을 찾지 못했습니다. 잠시 후 다시 시도해주세요.", NamedTextColor.RED));
            return;
        }
        plugin.getLandManager().claimAsync(uuid, LAND_NAME, cell[0], cell[1], land -> {
            inFlight.remove(uuid);
            if (land == null) {
                player.sendMessage(Component.text("등록에 실패했습니다. 다시 시도해주세요.", NamedTextColor.RED));
                return;
            }
            item.setAmount(item.getAmount() - 1);
            player.getInventory().addItem(plugin.getDeedItem().create(land, player.getName()));
            player.getWorld().playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 30, 0.5, 0.6, 0.5);
            player.sendMessage(Component.text("문서가 활성화되어 땅을 얻었습니다! (" + LandGrid.minX(cell[0]) + ", "
                    + LandGrid.minZ(cell[1]) + " 부근) /땅 문서 에서 이동할 수 있습니다.", NamedTextColor.GREEN));
        });
    }

    /** A random cell around spawn that nobody owns. Cells whose middle is water, lava or open
     * air are skipped so the prize isn't a patch of ocean; only already-generated chunks are
     * inspected, both to avoid generating fresh terrain here and because anything near spawn
     * is generated anyway. After TERRAIN_ATTEMPTS the first unclaimed cell wins regardless. */
    private int[] findFreeCell(World world) {
        int centerX = LandGrid.cellX(world.getSpawnLocation());
        int centerZ = LandGrid.cellZ(world.getSpawnLocation());
        int[] fallback = null;
        for (int i = 0; i < TERRAIN_ATTEMPTS; i++) {
            int cellX = centerX + random.nextInt(SEARCH_RADIUS_CELLS * 2 + 1) - SEARCH_RADIUS_CELLS;
            int cellZ = centerZ + random.nextInt(SEARCH_RADIUS_CELLS * 2 + 1) - SEARCH_RADIUS_CELLS;
            if (plugin.getLandManager().getLandAt(cellX, cellZ) != null) {
                continue;
            }
            if (fallback == null) {
                fallback = new int[]{cellX, cellZ};
            }
            int blockX = LandGrid.minX(cellX) + LandGrid.CELL_SIZE / 2;
            int blockZ = LandGrid.minZ(cellZ) + LandGrid.CELL_SIZE / 2;
            if (!world.isChunkGenerated(blockX >> 4, blockZ >> 4)) {
                continue;
            }
            Block top = world.getHighestBlockAt(blockX, blockZ);
            if (top.getType() == Material.WATER || top.getType() == Material.LAVA || !top.getType().isSolid()) {
                continue;
            }
            return new int[]{cellX, cellZ};
        }
        return fallback;
    }
}
