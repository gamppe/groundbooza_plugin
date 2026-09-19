package com.example.maincore;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * There's no vanilla/Paper config toggle that fully disables explosions (mobGriefing only
 * covers mob-caused block damage, not TNT), so this cancels every explosion outright -
 * TNT, creepers, beds in the End, respawn anchors, everything.
 */
public class ExplosionListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.setCancelled(true);
    }
}
