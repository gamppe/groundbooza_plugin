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
    private ClassManager classManager;
    private QuestManager questManager;
    private ClassGuideItem classGuideItem;
    private SkillItem skillItem;
    private ClassController classController;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        this.classManager = new ClassManager();
        this.questManager = new QuestManager();
        this.classGuideItem = new ClassGuideItem(this);
        this.skillItem = new SkillItem(this);
        this.arenaManager = new ArenaManager(this, new RoundSettings(config), classManager, questManager, classGuideItem);
        this.classController = new ClassController(this, classManager, questManager, classGuideItem, skillItem);
        arenaManager.setClassController(classController);

        // A crash or a /stop mid-round leaves the last arena on disk; nothing holds it open
        // at enable time, so this is the one moment deleting it is guaranteed to work.
        arenaManager.deleteLeftoverArenas();

        MagicWarCommand command = new MagicWarCommand(arenaManager);
        getCommand("마법전쟁").setExecutor(command);
        getCommand("마법전쟁").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new LobbyListener(arenaManager), this);
        getCommand("클래스").setExecutor(new ClassCommand(classController));
        getServer().getPluginManager().registerEvents(
                new ClassListener(this, arenaManager, classManager, classGuideItem, skillItem, classController), this);
        getServer().getPluginManager().registerEvents(new QuestListener(arenaManager, classManager, questManager), this);
        MagicWarScoreboard scoreboard = new MagicWarScoreboard(arenaManager, classManager, questManager);
        getServer().getScheduler().runTaskTimer(this, scoreboard::tick, 20L, 20L);

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

    public ClassManager getClassManager() {
        return classManager;
    }
}
