package com.example.serverbridge;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerBridgePlugin extends JavaPlugin {

    private DatabaseManager databaseManager;

    private int boxSize;
    private String boxTitle;
    private String targetServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        this.boxSize = config.getInt("box.size", 27);
        this.boxTitle = config.getString("box.title", "개인 상자");
        this.targetServer = config.getString("target-server", "main");

        String host = config.getString("mysql.host", "127.0.0.1");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "mc_serverbridge");
        String user = config.getString("mysql.user", "root");
        String password = config.getString("mysql.password", "");

        this.databaseManager = new DatabaseManager(getLogger(), host, port, database, user, password);

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        getCommand("파밍상자").setExecutor(new BoxCommand(this));
        getCommand("파밍이동").setExecutor(new SwitchCommand(this));

        getServer().getPluginManager().registerEvents(new BoxListener(this), this);

        // MainCore (main-server only) already runs the richer, land-protection-aware version of
        // these abilities - registering both would double every effect (till twice, drop items
        // twice, etc.), so this simpler copy only activates where MainCore is absent.
        if (getServer().getPluginManager().getPlugin("MainCore") == null) {
            SpecialToolListener specialToolListener = new SpecialToolListener();
            getServer().getPluginManager().registerEvents(specialToolListener, this);
            getCommand("groundbuza_compass_set_farm").setExecutor(new CompassSetBiomeCommand(specialToolListener));
            getLogger().info("MainCore not found - enabling standalone special-tool abilities.");
        }

        getLogger().info("ServerBridge enabled. target-server=" + targetServer);
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public int getBoxSize() {
        return boxSize;
    }

    public String getBoxTitle() {
        return boxTitle;
    }

    public String getTargetServer() {
        return targetServer;
    }
}
