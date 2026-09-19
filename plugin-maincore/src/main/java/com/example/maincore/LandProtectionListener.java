package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

public class LandProtectionListener implements Listener {

    private final MainCorePlugin plugin;

    public LandProtectionListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Returns true (and cancels+notifies) if this player isn't allowed to act on `loc`. */
    private boolean blockIfNotOwner(Player player, Location loc, org.bukkit.event.Cancellable event) {
        if (player.isOp()) return false;
        if (plugin.getLandManager().canBuild(loc, player.getUniqueId())) return false;
        event.setCancelled(true);
        player.sendMessage(Component.text("이곳은 다른 사람의 땅입니다.", NamedTextColor.RED));
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        blockIfNotOwner(event.getPlayer(), event.getBlock().getLocation(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        blockIfNotOwner(event.getPlayer(), event.getBlock().getLocation(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getIgnitingEntity() instanceof Player player) {
            blockIfNotOwner(player, event.getBlock().getLocation(), event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onContainerOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof Container container)) {
            return; // not a chest/barrel/furnace/etc - e.g. crafting table, player's own inventory
        }
        blockIfNotOwner(player, container.getLocation(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Location target = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
        blockIfNotOwner(event.getPlayer(), target, event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        blockIfNotOwner(event.getPlayer(), event.getBlockClicked().getLocation(), event);
    }
}
