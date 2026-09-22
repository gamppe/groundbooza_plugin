package com.example.serverbridge;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ServerBridgePlugin extends JavaPlugin {

    private DatabaseManager databaseManager;

    private int boxSize;
    private String boxTitle;
    private String targetServer;
    private String serverName;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        this.boxSize = config.getInt("box.size", 27);
        this.boxTitle = config.getString("box.title", "개인 상자");
        this.targetServer = config.getString("target-server", "main");
        this.serverName = config.getString("server-name", "");
        boolean boxEnabled = config.getBoolean("features.box", true);
        boolean jobsEnabled = config.getBoolean("features.standalone-jobs", true);

        // Both the box and the cross-schema job lookup need MySQL; a server running neither
        // (the 마법전쟁 event server) should not open a pool at all.
        if (boxEnabled || jobsEnabled) {
            String host = config.getString("mysql.host", "127.0.0.1");
            int port = config.getInt("mysql.port", 3306);
            String database = config.getString("mysql.database", "mc_serverbridge");
            String user = config.getString("mysql.user", "root");
            String password = config.getString("mysql.password", "");
            this.databaseManager = new DatabaseManager(getLogger(), host, port, database, user, password);
        }

        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // Whisper and the switch commands are the one thing every backend gets, however
        // little else it runs - that is what makes a bare event server feel connected.
        WhisperCommand whisperCommand = new WhisperCommand(this);
        getCommand("귓속말").setExecutor(whisperCommand);
        getCommand("귓속말").setTabCompleter(whisperCommand);
        getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", whisperCommand);
        getCommand("마법전쟁서버").setExecutor(
                new ConnectCommand(this, config.getString("servers.magic", "magic"), "마법전쟁"));
        getCommand("땅부자서버").setExecutor(
                new ConnectCommand(this, config.getString("servers.main", "main"), "땅 부자 타이쿤"));

        if (boxEnabled) {
            getCommand("파밍상자").setExecutor(new BoxCommand(this));
            getCommand("파밍이동").setExecutor(new SwitchCommand(this));
            getServer().getPluginManager().registerEvents(new BoxListener(this), this);
        } else {
            disable("파밍상자", "파밍이동");
        }

        // MainCore (main-server only) already runs the richer, land-protection-aware version of
        // these abilities - registering both would double every effect (till twice, drop items
        // twice, etc.), so this simpler copy only activates where MainCore is absent.
        if (jobsEnabled && getServer().getPluginManager().getPlugin("MainCore") == null) {
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

        getLogger().info("ServerBridge enabled. server-name=" + (serverName.isEmpty() ? "(unset)" : serverName)
                + ", target-server=" + targetServer + ", box=" + boxEnabled + ", standalone-jobs=" + jobsEnabled);
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

    /** This backend's own Velocity key, or "" when the server never set one. */
    public String getServerName() {
        return serverName;
    }

    /** Keeps a plugin.yml command from falling through to its usage text when the feature
     * behind it is switched off for this server. */
    private void disable(String... names) {
        for (String name : names) {
            getCommand(name).setExecutor((sender, command, label, args) -> {
                sender.sendMessage("이 서버에서는 사용할 수 없는 명령어입니다.");
                return true;
            });
        }
    }
}
