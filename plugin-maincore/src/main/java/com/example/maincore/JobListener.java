package com.example.maincore;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.Set;

/** /직업 menu clicks, the job cache's join/quit lifecycle, and 어부's biome-dependent catches. */
public class JobListener implements Listener {

    private static final Set<Material> VANILLA_FISH = Set.of(
            Material.COD, Material.SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH);

    private final MainCorePlugin plugin;
    private final Random random = new Random();

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

    // ---------- 어부: what you catch depends on where the hook landed ----------

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.getJobManager().hasJob(player.getUniqueId(), Job.FISHER)) {
            return;
        }
        if (!(event.getCaught() instanceof Item caughtItem)) {
            return;
        }
        ItemStack stack = caughtItem.getItemStack();
        if (!VANILLA_FISH.contains(stack.getType())) {
            return; // treasure / junk rolls are left alone
        }
        Biome biome = event.getHook().getLocation().getBlock().getBiome();
        Material replacement = fishFor(biome);
        if (replacement != stack.getType()) {
            caughtItem.setItemStack(new ItemStack(replacement, stack.getAmount()));
        }
    }

    /** Cold water → salmon, warm/tropical water → tropical fish (with some pufferfish), open
     * ocean → cod, and everything inland is a cod/salmon coin flip. Matched on the biome key
     * so new biome variants slot in without upkeep. */
    private Material fishFor(Biome biome) {
        String key = biome.getKey().getKey();
        if (key.contains("frozen") || key.contains("cold") || key.contains("snowy")
                || key.contains("ice") || key.contains("taiga") || key.contains("grove")) {
            return Material.SALMON;
        }
        if (key.contains("warm") || key.contains("jungle") || key.contains("mangrove") || key.contains("lush")) {
            return random.nextDouble() < 0.2 ? Material.PUFFERFISH : Material.TROPICAL_FISH;
        }
        if (key.contains("ocean") || key.contains("beach")) {
            return Material.COD;
        }
        return random.nextBoolean() ? Material.COD : Material.SALMON;
    }
}
