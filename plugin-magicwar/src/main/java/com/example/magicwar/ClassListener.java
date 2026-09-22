package com.example.magicwar;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;

/** Guide right-clicks, clicks inside both class screens, and the "you have to pick one" rule. */
public class ClassListener implements Listener {

    private final MagicWarPlugin plugin;
    private final ArenaManager arena;
    private final ClassManager classes;
    private final ClassGuideItem guide;
    private final ClassController controller;

    public ClassListener(MagicWarPlugin plugin, ArenaManager arena, ClassManager classes,
                         ClassGuideItem guide, ClassController controller) {
        this.plugin = plugin;
        this.arena = arena;
        this.classes = classes;
        this.guide = guide;
        this.controller = controller;
    }

    @EventHandler(ignoreCancelled = true)
    public void onGuideUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!guide.isGuide(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        controller.open(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSelectClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ClassSelectHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        MagicClass picked = ClassSelectHolder.classForSlot(event.getSlot());
        if (picked != null) {
            controller.choose(player, picked);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onUpgradeClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ClassUpgradeHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int track = ClassUpgradeHolder.trackForIconSlot(event.getSlot());
        if (track >= 0) {
            controller.upgrade(player, track);
        }
    }

    /** Closing the picker without choosing puts it straight back up - but only in the arena, so
     * a player sent home (or a round that ended) is never trapped in it. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ClassSelectHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player) || classes.hasClass(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && arena.isRunning() && !arena.isLobby(player.getWorld())
                    && !classes.hasClass(player.getUniqueId())) {
                controller.openSelect(player);
            }
        });
    }
}
