package com.example.maincore;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** /직업 menu clicks and the job cache's join/quit lifecycle. */
public class JobListener implements Listener {

    private final MainCorePlugin plugin;

    public JobListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getJobManager().loadAsync(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getJobManager().unload(event.getPlayer().getUniqueId());
    }

    // ---------- menus ----------

    @EventHandler(ignoreCancelled = true)
    public void onSelectClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof JobSelectHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (holder.isChanging() && slot == JobSelectHolder.SLOT_BACK) {
            plugin.getJobController().openProfile(player);
            return;
        }
        Job job = JobSelectHolder.jobForSlot(slot);
        if (job != null) {
            plugin.getJobController().requestChoice(player, job, holder.isChanging());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProfileClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof JobProfileHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        switch (event.getSlot()) {
            case JobProfileHolder.SLOT_UPGRADE -> plugin.getJobController().openUpgrades(player);
            case JobProfileHolder.SLOT_ACTION -> plugin.getJobController().runAction(player, event.isShiftClick());
            case JobProfileHolder.SLOT_SHOP -> plugin.getShopController().openSpecialBuy(player, 0);
            case JobProfileHolder.SLOT_CHANGE -> plugin.getJobController().openSelect(player, true);
            default -> { /* card / border - nothing to do */ }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUpgradeClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof JobUpgradeHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        if (slot == JobUpgradeHolder.SLOT_BACK) {
            plugin.getJobController().openProfile(player);
            return;
        }
        int track = JobUpgradeHolder.trackForIconSlot(slot);
        if (track >= 0) {
            plugin.getJobController().upgrade(player, track);
        }
    }
}
