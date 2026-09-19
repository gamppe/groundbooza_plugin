package com.example.maincore;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

/**
 * Lava can't be cancelled at "who placed it" like block break/place, so instead this blocks
 * any lava flow whose destination is inside someone's claimed land unless the flow started
 * within that same land - i.e. you can use lava freely on your own plot, but nobody can pour
 * it in from outside (or from a neighboring plot) to grief you.
 */
public class LavaProtectionListener implements Listener {

    private final MainCorePlugin plugin;

    public LavaProtectionListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (event.getBlock().getType() != Material.LAVA) {
            return;
        }
        MainDatabase.Land destLand = plugin.getLandManager().getLandAt(event.getToBlock().getLocation());
        if (destLand == null) {
            return; // unclaimed destination - not our concern
        }
        MainDatabase.Land sourceLand = plugin.getLandManager().getLandAt(event.getBlock().getLocation());
        if (sourceLand != null && sourceLand.id() == destLand.id()) {
            return; // flowing within the same plot - the owner's own business
        }
        event.setCancelled(true);
    }
}
