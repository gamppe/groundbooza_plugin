package com.example.magicwar;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * No other dimensions, at all.
 *
 * <p>An arena is one throwaway overworld per round, so there is no {@code <arena>_nether} for a
 * portal to lead to and nothing on the far side of an end portal either. Left alone a lit
 * portal would simply swallow whoever walked in and do nothing, which reads as a bug.
 *
 * <p>There is a game reason too: a world border is per world, so anyone who got out would be
 * standing somewhere the shrinking zone cannot reach. A round that can be waited out in another
 * dimension does not end.
 */
public class PortalLock implements Listener {

    /** Lighting a nether portal, and the end portal a stronghold frame would complete. */
    @EventHandler(ignoreCancelled = true)
    public void onCreate(PortalCreateEvent event) {
        event.setCancelled(true);
        if (event.getEntity() instanceof Player player) {
            tell(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        event.setCancelled(true);
        tell(event.getPlayer());
    }

    /** Mobs and dropped items find portals too, and would vanish into a world that is not there. */
    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        event.setCancelled(true);
    }

    /** A stronghold can be sitting in the arena with its frame already half filled, so the eyes
     * are stopped before the portal ever forms. */
    @EventHandler(ignoreCancelled = true)
    public void onEye(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || block == null || block.getType() != Material.END_PORTAL_FRAME
                || event.getMaterial() != Material.ENDER_EYE) {
            return;
        }
        event.setCancelled(true);
        tell(event.getPlayer());
    }

    private static void tell(Player player) {
        player.sendActionBar(Component.text("이 서버에는 다른 차원이 없습니다", NamedTextColor.RED));
    }
}
