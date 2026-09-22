package com.example.magicwar;

import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/** Keeps the lobby read-only: adventure mode on arrival, and block edits refused outright for
 * anyone who is not in creative (adventure already blocks them - this also covers a player an
 * admin left in survival). */
public class LobbyListener implements Listener {

    private final ArenaManager arena;

    public LobbyListener(ArenaManager arena) {
        this.arena = arena;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (arena.isLobby(event.getPlayer().getWorld())) {
            arena.applyLobbyMode(event.getPlayer());
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (arena.isLobby(event.getPlayer().getWorld())) {
            arena.applyLobbyMode(event.getPlayer());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // Dying in the arena sends you back to the lobby rather than to an arena bed/spawn.
        if (!arena.isRunning() || !arena.isLobby(event.getPlayer().getWorld())) {
            var lobby = arena.lobby();
            if (lobby != null) {
                event.setRespawnLocation(lobby.getSpawnLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (arena.isLobby(event.getBlock().getWorld()) && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (arena.isLobby(event.getBlock().getWorld()) && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }
}
