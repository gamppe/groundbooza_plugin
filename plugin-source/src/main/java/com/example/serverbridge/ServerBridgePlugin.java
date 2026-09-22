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
        WhisperCommand whisperCommand = new WhisperCommand(this);
        getCommand("귓속말").setExecutor(whisperCommand);
        getCommand("귓속말").setTabCompleter(whisperCommand);
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", whisperCommand);

        getServer().getPluginManager().registerEvents(new BoxListener(this), this);

        // MainCore (main-server only) already runs the richer, land-protection-aware version of
        // these abilities - registering both would double every effect (till twice, drop items
        // twice, etc.), so this simpler copy only activates where MainCore is absent.
        if (getServer().getPluginManager().getPlugin("MainCore") == null) {
            // Job data is read straight out of MainCore's schema so job-locked tools and the
            // player-side job abilities behave the same over here.
            JobCache jobs = new JobCache(this, config.getString("jobs-schema", "mc_maincore"));
            MinerAbilities minerAbilities = new MinerAbilities(jobs);
            FarmerAbilities farmerAbilities = new FarmerAbilities(jobs);
            jobs.setOnLoaded(minerAbilities::syncEfficiency);
            SpecialToolListener specialToolListener = new SpecialToolListener(jobs);
            getServer().getPluginManager().registerEvents(jobs, this);
            getServer().getPluginManager().registerEvents(specialToolListener, this);
            getServer().getPluginManager().registerEvents(new CropRules(this), this);
            getServer().getPluginManager().registerEvents(new AnimalFeeding(farmerAbilities), this);
            getServer().getPluginManager().registerEvents(minerAbilities, this);
            getServer().getPluginManager().registerEvents(farmerAbilities, this);
            getServer().getPluginManager().registerEvents(new FishItems(), this);
            getServer().getPluginManager().registerEvents(new FishingLoot(jobs), this);
            getServer().getPluginManager().registerEvents(new WildDeedItem(), this);
            FisherAbilities fisherAbilities = new FisherAbilities(this, jobs);
            getServer().getPluginManager().registerEvents(fisherAbilities, this);
            getServer().getScheduler().runTaskTimer(this, fisherAbilities::tick, 1L, 5L);
            LavaVoidFishingListener lavaVoidFishingListener = new LavaVoidFishingListener(this, jobs, fisherAbilities);
            getServer().getPluginManager().registerEvents(lavaVoidFishingListener, this);
            getServer().getScheduler().runTaskTimer(this, lavaVoidFishingListener::tick, 1L, 1L);
            getServer().getPluginManager().registerEvents(new NoEnchantListener(item -> JobCache.toolJob(item) != null), this);
            DyeRecipes dyeRecipes = new DyeRecipes(this);
            getServer().getPluginManager().registerEvents(dyeRecipes, this);
            dyeRecipes.register();
            WoodRecipes woodRecipes = new WoodRecipes(this);
            getServer().getPluginManager().registerEvents(woodRecipes, this);
            woodRecipes.register();
            getCommand("groundbuza_compass_set_farm").setExecutor(new CompassSetBiomeCommand(specialToolListener, jobs));
            getServer().getScheduler().runTaskTimer(this, specialToolListener::tickMagnetPickaxe, 1L, 2L);
            getServer().getScheduler().runTaskTimer(this, minerAbilities::tick, 1L, 5L);
            getServer().getScheduler().runTaskTimer(this, farmerAbilities::tick, 1L, 5L);
            for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
                jobs.loadAsync(online.getUniqueId()); // /reload or late enable
            }
            getLogger().info("MainCore not found - enabling standalone special-tool abilities + job sync.");
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
