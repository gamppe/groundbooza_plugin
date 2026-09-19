package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;

/** Keeps regular players confined to the overworld - no Nether or End travel. */
public class DimensionLockListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) {
            return;
        }
        if (event.getTo() == null || event.getTo().getWorld() == null) {
            return;
        }
        World.Environment destination = event.getTo().getWorld().getEnvironment();
        if (destination == World.Environment.NETHER || destination == World.Environment.THE_END) {
            event.setCancelled(true);
            player.sendMessage(Component.text("이 서버에서는 다른 차원으로 이동할 수 없습니다.", NamedTextColor.RED));
        }
    }
}
