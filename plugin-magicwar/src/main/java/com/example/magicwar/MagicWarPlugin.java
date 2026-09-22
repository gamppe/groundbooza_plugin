package com.example.magicwar;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 마법전쟁 event server. Two worlds live here: the medieval lobby (adventure mode, nothing
 * breakable) and a throwaway arena that is generated fresh for every round and deleted
 * afterwards, so a match can wreck the terrain without anyone having to clean up.
 */
public class MagicWarPlugin extends JavaPlugin {

    private ArenaManager arenaManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        this.arenaManager = new ArenaManager(this,
                config.getString("lobby-world", "world"),
                config.getString("arena-prefix", "arena_"),
                config.getInt("countdown-seconds", 10),
                config.getInt("border-half-width", 999));

        // A crash or a /stop mid-round leaves the last arena on disk; nothing holds it open
        // at enable time, so this is the one moment deleting it is guaranteed to work.
        arenaManager.deleteLeftoverArenas();

        MagicWarCommand command = new MagicWarCommand(arenaManager);
        getCommand("마법전쟁").setExecutor(command);
        getCommand("마법전쟁").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new LobbyListener(arenaManager), this);

        getLogger().info("MagicWar enabled. lobby=" + arenaManager.lobbyWorldName());
    }

    @Override
    public void onDisable() {
        if (arenaManager != null) {
            arenaManager.shutdown();
        }
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }
}
