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
    private SkillCooldowns skillCooldowns;
    private SkillEffects skillEffects;
    private FrostState frostState;
    private TempBlocks tempBlocks;
    private ClassController classController;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        this.classManager = new ClassManager();
        this.questManager = new QuestManager();
        this.classGuideItem = new ClassGuideItem(this);
        this.skillItem = new SkillItem(this);
        this.skillCooldowns = new SkillCooldowns();
        this.tempBlocks = new TempBlocks();
        EruptionPreview eruptionPreview = new EruptionPreview(this);
        this.frostState = new FrostState(this, tempBlocks);
        SkillEffects skillEffects = new SkillEffects(this, classManager, skillCooldowns, frostState, tempBlocks, skillItem, eruptionPreview);
        this.arenaManager = new ArenaManager(this, new RoundSettings(config), classManager, questManager, classGuideItem);
        this.classController = new ClassController(this, classManager, questManager, classGuideItem, skillItem);
        arenaManager.setClassController(classController);

        // A crash or a /stop mid-round leaves the last arena on disk; nothing holds it open
        // at enable time, so this is the one moment deleting it is guaranteed to work.
        arenaManager.deleteLeftoverArenas();
        arenaManager.applyLobbyRules();

        MagicWarCommand command = new MagicWarCommand(arenaManager);
        getCommand("마법전쟁").setExecutor(command);
        getCommand("마법전쟁").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new LobbyListener(arenaManager), this);
        ClassCommand classCommand = new ClassCommand(classController, classManager, questManager, skillCooldowns);
        getCommand("클래스").setExecutor(classCommand);
        getCommand("클래스").setTabCompleter(classCommand);
        getServer().getPluginManager().registerEvents(
                new ClassListener(this, arenaManager, classManager, classGuideItem, skillItem, classController, skillEffects, skillCooldowns), this);
        getServer().getPluginManager().registerEvents(skillEffects, this);
        getServer().getPluginManager().registerEvents(frostState, this);
        getServer().getPluginManager().registerEvents(eruptionPreview, this);
        getServer().getScheduler().runTaskTimer(this, frostState::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, tempBlocks::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, skillCooldowns::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, skillEffects::tick, 1L, 1L);
        this.skillEffects = skillEffects;
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

    public SkillCooldowns getSkillCooldowns() {
        return skillCooldowns;
    }

    public SkillEffects getSkillEffects() {
        return skillEffects;
    }

    public FrostState getFrostState() {
        return frostState;
    }

    public TempBlocks getTempBlocks() {
        return tempBlocks;
    }
}
