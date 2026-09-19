package com.example.maincore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class FlightListener implements Listener {

    private final MainCorePlugin plugin;

    public FlightListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || LandGrid.isSameCell(from, to)) {
            return; // only care when crossing into a different 48x48 cell
        }

        Player player = event.getPlayer();
        MainDatabase.Land destinationLand = plugin.getLandManager().getLandAt(to);
        plugin.getLandBossBarManager().refresh(player, destinationLand);

        if (player.isOp()) {
            // Ops always have flight everywhere, and are never walled in.
            player.setAllowFlight(true);
            return;
        }

        boolean ownsDestination = destinationLand != null && destinationLand.owner().equals(player.getUniqueId());

        if (player.isFlying() && !ownsDestination) {
            // Invisible wall: can't fly out of your own land until you land and stop flying.
            event.setCancelled(true);
            event.setTo(from);
            player.sendMessage(Component.text("비행 중에는 본인 땅 밖으로 나갈 수 없습니다. 착지 후 비행을 해제하세요.",
                    NamedTextColor.YELLOW));
            return;
        }

        applyFlightState(player, ownsDestination);
    }

    private void applyFlightState(Player player, boolean ownsHere) {
        player.setAllowFlight(ownsHere);
        if (!ownsHere && player.isFlying()) {
            player.setFlying(false);
        }
    }

    /** Periodic safety-net sync (e.g. right after buying the cell you're already standing on). */
    public void syncNonMoveCase(Player player) {
        if (player.isOp()) {
            player.setAllowFlight(true);
            return;
        }
        boolean ownsHere = plugin.getLandManager().isOwner(player.getLocation(), player.getUniqueId());
        applyFlightState(player, ownsHere);
    }
}
