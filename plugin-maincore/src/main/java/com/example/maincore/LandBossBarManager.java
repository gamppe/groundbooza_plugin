package com.example.maincore;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LandBossBarManager {

    private final Map<UUID, BossBar> activeBars = new ConcurrentHashMap<>();

    /** Call whenever a player's current land may have changed. */
    public void refresh(Player player, MainDatabase.Land land) {
        if (land == null) {
            BossBar existing = activeBars.remove(player.getUniqueId());
            if (existing != null) {
                player.hideBossBar(existing);
            }
            return;
        }

        BossBar bar = activeBars.get(player.getUniqueId());
        Component title = Component.text(land.name(), NamedTextColor.YELLOW);
        if (bar == null) {
            bar = BossBar.bossBar(title, 1f, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
            activeBars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(title);
        }
    }

    public void clear(Player player) {
        BossBar existing = activeBars.remove(player.getUniqueId());
        if (existing != null) {
            player.hideBossBar(existing);
        }
    }
}
