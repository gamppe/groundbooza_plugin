package com.example.maincore;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinListener implements Listener {

    private final MainCorePlugin plugin;

    public JoinListener(MainCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getEconomyCache().refreshAsync(event.getPlayer().getUniqueId());
        event.getPlayer().setAllowFlight(false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getEconomyCache().remove(event.getPlayer().getUniqueId());
    }
}
