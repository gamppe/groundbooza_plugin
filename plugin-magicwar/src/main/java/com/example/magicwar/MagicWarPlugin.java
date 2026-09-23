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
    private Summons summons;
    private Perks perks;
    private QuestTracker questTracker;
    private ClassController classController;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        this.classManager = new ClassManager();
        this.questManager = new QuestManager();
        this.classGuideItem = new ClassGuideItem(this);
        this.skillItem = new SkillItem(this);
        this.tempBlocks = new TempBlocks();
        this.summons = new Summons();
        this.perks = new Perks(this);
        this.skillCooldowns = new SkillCooldowns(perks);
        StrengthPotionItem strengthPotion = new StrengthPotionItem(this);
        SkillPreview skillPreview = new SkillPreview(this);
        this.frostState = new FrostState(this, tempBlocks, perks);
        // The arena has to exist before the tracker, which asks it whether a round is on, and
        // the tracker before the skills, which report into it.
        this.arenaManager = new ArenaManager(this, new RoundSettings(config), classManager, questManager, classGuideItem);
        this.questTracker = new QuestTracker(arenaManager, classManager, questManager);
        SkillEffects skillEffects = new SkillEffects(this, classManager, skillCooldowns, frostState, tempBlocks, skillItem, skillPreview, summons, perks, questTracker);
        this.classController = new ClassController(this, classManager, questManager, classGuideItem, skillItem, perks, strengthPotion);
        arenaManager.setClassController(classController);

        // A crash or a /stop mid-round leaves the last arena on disk; nothing holds it open
        // at enable time, so this is the one moment deleting it is guaranteed to work.
        arenaManager.deleteLeftoverArenas();
        arenaManager.applyLobbyRules();

        MagicWarCommand command = new MagicWarCommand(arenaManager);
        getCommand("마법전쟁").setExecutor(command);
        getCommand("마법전쟁").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new LobbyListener(arenaManager), this);
        ClassCommand classCommand = new ClassCommand(classController, classManager, questManager, skillCooldowns, perks);
        getCommand("클래스").setExecutor(classCommand);
        getCommand("클래스").setTabCompleter(classCommand);
        getServer().getPluginManager().registerEvents(
                new ClassListener(this, arenaManager, classManager, classGuideItem, skillItem, classController, skillEffects, skillCooldowns), this);
        getServer().getPluginManager().registerEvents(skillEffects, this);
        getServer().getPluginManager().registerEvents(frostState, this);
        getServer().getPluginManager().registerEvents(skillPreview, this);
        getServer().getPluginManager().registerEvents(strengthPotion, this);
        getServer().getPluginManager().registerEvents(new PerkListener(perks, summons), this);
        getServer().getPluginManager().registerEvents(new PortalLock(), this);
        getServer().getScheduler().runTaskTimer(this, frostState::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, tempBlocks::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, summons::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, skillCooldowns::tick, 1L, 1L);
        getServer().getScheduler().runTaskTimer(this, skillEffects::tick, 1L, 1L);
        this.skillEffects = skillEffects;
        getServer().getPluginManager().registerEvents(new QuestListener(questTracker, summons), this);
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

    public Summons getSummons() {
        return summons;
    }

    public Perks getPerks() {
        return perks;
    }

    public QuestTracker getQuestTracker() {
        return questTracker;
    }
}
