package com.example.maincore;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class MainCorePlugin extends JavaPlugin {

    private MainDatabase mainDatabase;
    private LandManager landManager;
    private EconomyCache economyCache;
    private LandDeedItem deedItem;
    private CreditItem creditItem;
    private TradeManager tradeManager;
    private ScoreboardManager scoreboardManager;
    private FlightListener flightListener;
    private LandBossBarManager landBossBarManager;
    private LandVisualizer landVisualizer;
    private PendingDeedItem pendingDeedItem;
    private TeleportRequestManager teleportRequestManager;
    private AbandonRequestManager abandonRequestManager;
    private PendingPurchaseManager pendingPurchaseManager;
    private PvpProtectionState pvpProtectionState;
    private MarketController marketController;
    private StarterKitListener starterKitListener;
    private RentalToolItem rentalToolItem;
    private RentalVoucherItem rentalVoucherItem;
    private ShopController shopController;
    private SpecialHoeItem specialHoeItem;
    private SpecialPickaxeItem specialPickaxeItem;
    private SpecialAxeItem specialAxeItem;
    private FishingRodTierItem fishingRodTierItem;
    private CompassBiomeFinderItem compassBiomeFinderItem;
    private SpecialToolListener specialToolListener;
    private ReservationDeedItem reservationDeedItem;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        String host = config.getString("mysql.host", "127.0.0.1");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "mc_maincore");
        String user = config.getString("mysql.user", "root");
        String password = config.getString("mysql.password", "");

        this.mainDatabase = new MainDatabase(getLogger(), host, port, database, user, password);
        this.landManager = new LandManager(this, mainDatabase);
        this.landManager.loadAll();

        this.economyCache = new EconomyCache(this);
        this.deedItem = new LandDeedItem(this);
        this.creditItem = new CreditItem(this);
        this.tradeManager = new TradeManager();
        this.scoreboardManager = new ScoreboardManager(this);
        this.flightListener = new FlightListener(this);
        this.landBossBarManager = new LandBossBarManager();
        this.pendingDeedItem = new PendingDeedItem(this);
        this.landVisualizer = new LandVisualizer(this);
        this.teleportRequestManager = new TeleportRequestManager();
        this.abandonRequestManager = new AbandonRequestManager();
        this.pendingPurchaseManager = new PendingPurchaseManager();
        this.pvpProtectionState = new PvpProtectionState();
        this.marketController = new MarketController(this);
        this.rentalToolItem = new RentalToolItem(this);
        this.rentalVoucherItem = new RentalVoucherItem(this);
        this.specialHoeItem = new SpecialHoeItem(this);
        this.specialPickaxeItem = new SpecialPickaxeItem(this);
        this.specialAxeItem = new SpecialAxeItem(this);
        this.fishingRodTierItem = new FishingRodTierItem(this);
        this.compassBiomeFinderItem = new CompassBiomeFinderItem(this);
        this.reservationDeedItem = new ReservationDeedItem(this);
        this.shopController = new ShopController(this);

        LandCommand landCommand = new LandCommand(this);
        getCommand("땅").setExecutor(landCommand);
        getCommand("땅").setTabCompleter(landCommand);

        getCommand("크레딧").setExecutor(new CreditCommand(this));

        CreditGiveCommand creditGiveCommand = new CreditGiveCommand(this);
        getCommand("크레딧지급").setExecutor(creditGiveCommand);
        getCommand("크레딧지급").setTabCompleter(creditGiveCommand);

        TradeCommand tradeCommand = new TradeCommand(this);
        getCommand("거래").setExecutor(tradeCommand);
        getCommand("거래").setTabCompleter(tradeCommand);

        getCommand("도움말").setExecutor(new HelpCommand());
        getCommand("groundbuza_deed_tp_confirm").setExecutor(new DeedTeleportConfirmCommand(this));
        getCommand("groundbuza_deed_tp_cancel").setExecutor(new DeedTeleportCancelCommand(this));
        getCommand("groundbuza_land_abandon_confirm").setExecutor(new AbandonConfirmCommand(this));
        getCommand("groundbuza_land_abandon_cancel").setExecutor(new AbandonCancelCommand(this));
        getCommand("groundbuza_land_buy_confirm").setExecutor(new LandBuyConfirmCommand(this));
        getCommand("groundbuza_land_buy_cancel").setExecutor(new LandBuyCancelCommand(this));
        getCommand("pvp").setExecutor(new PvpCommand(this));
        getCommand("groundbuza_market_buy_confirm").setExecutor(new MarketBuyConfirmCommand(this));
        getCommand("groundbuza_market_buy_cancel").setExecutor(new MarketBuyCancelCommand());
        getCommand("거래소").setExecutor(new MarketCommand(this));
        getCommand("상점").setExecutor(new ShopCommand(this));

        getServer().getPluginManager().registerEvents(new LandProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(flightListener, this);
        getServer().getPluginManager().registerEvents(new DeedPickupListener(this), this);
        getServer().getPluginManager().registerEvents(new ToolPickupListener(this), this);
        getServer().getPluginManager().registerEvents(new DeedBookListener(this), this);
        getServer().getPluginManager().registerEvents(new CreditRedeemListener(this), this);
        getServer().getPluginManager().registerEvents(new TradeListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new LandBuyListener(this), this);
        getServer().getPluginManager().registerEvents(new DeedTeleportListener(this), this);
        getServer().getPluginManager().registerEvents(new DimensionLockListener(), this);
        getServer().getPluginManager().registerEvents(new LavaProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ExplosionListener(), this);
        getServer().getPluginManager().registerEvents(new EntityProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new AnimalSpawnListener(), this);
        starterKitListener = new StarterKitListener(this);
        getServer().getPluginManager().registerEvents(starterKitListener, this);
        getCommand("초보자팩").setExecutor(new StarterKitCommand(this));
        getServer().getPluginManager().registerEvents(new MarketListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        specialToolListener = new SpecialToolListener(this);
        getServer().getPluginManager().registerEvents(specialToolListener, this);
        getCommand("groundbuza_compass_set").setExecutor(new CompassSetBiomeCommand(this));
        Bukkit.getScheduler().runTaskTimer(this, specialToolListener::tickWorldEditAxe, 1L, 1L);

        LavaVoidFishingListener lavaVoidFishingListener = new LavaVoidFishingListener(this);
        getServer().getPluginManager().registerEvents(lavaVoidFishingListener, this);
        Bukkit.getScheduler().runTaskTimer(this, lavaVoidFishingListener::tick, 1L, 1L);
        removeExistingAnimalsOnce();
        marketController.startExpirySweep();
        new RentalExpiryTask(this).start();

        Bukkit.getWorlds().forEach(world -> world.setGameRule(GameRule.DO_FIRE_TICK, false));

        scoreboardManager.start();
        landVisualizer.start();

        getLogger().info("MainCore enabled.");
    }

    // One-time cleanup: kill every existing animal the moment this update is first loaded,
    // then never again (a marker file stops it from also wiping out bred/imported animals
    // on every future restart).
    private void removeExistingAnimalsOnce() {
        java.io.File marker = new java.io.File(getDataFolder(), ".animal_cleanup_done");
        if (marker.exists()) {
            return;
        }
        int removed = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Animals animal : world.getEntitiesByClass(org.bukkit.entity.Animals.class)) {
                animal.remove();
                removed++;
            }
        }
        getLogger().info("One-time cleanup removed " + removed + " existing animals.");
        try {
            marker.getParentFile().mkdirs();
            marker.createNewFile();
        } catch (java.io.IOException e) {
            getLogger().warning("Failed to write animal cleanup marker: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (mainDatabase != null) {
            mainDatabase.close();
        }
    }

    public MainDatabase getMainDatabase() {
        return mainDatabase;
    }

    public LandManager getLandManager() {
        return landManager;
    }

    public EconomyCache getEconomyCache() {
        return economyCache;
    }

    public LandDeedItem getDeedItem() {
        return deedItem;
    }

    public CreditItem getCreditItem() {
        return creditItem;
    }

    public TradeManager getTradeManager() {
        return tradeManager;
    }

    public FlightListener getFlightListener() {
        return flightListener;
    }

    public LandBossBarManager getLandBossBarManager() {
        return landBossBarManager;
    }

    public PendingDeedItem getPendingDeedItem() {
        return pendingDeedItem;
    }

    public LandVisualizer getLandVisualizer() {
        return landVisualizer;
    }

    public TeleportRequestManager getTeleportRequestManager() {
        return teleportRequestManager;
    }

    public AbandonRequestManager getAbandonRequestManager() {
        return abandonRequestManager;
    }

    public PendingPurchaseManager getPendingPurchaseManager() {
        return pendingPurchaseManager;
    }

    public PvpProtectionState getPvpProtectionState() {
        return pvpProtectionState;
    }

    public MarketController getMarketController() {
        return marketController;
    }

    public StarterKitListener getStarterKitListener() {
        return starterKitListener;
    }

    public RentalToolItem getRentalToolItem() {
        return rentalToolItem;
    }

    public RentalVoucherItem getRentalVoucherItem() {
        return rentalVoucherItem;
    }

    public ShopController getShopController() {
        return shopController;
    }

    public SpecialHoeItem getSpecialHoeItem() {
        return specialHoeItem;
    }

    public SpecialPickaxeItem getSpecialPickaxeItem() {
        return specialPickaxeItem;
    }

    public SpecialAxeItem getSpecialAxeItem() {
        return specialAxeItem;
    }

    public FishingRodTierItem getFishingRodTierItem() {
        return fishingRodTierItem;
    }

    public CompassBiomeFinderItem getCompassBiomeFinderItem() {
        return compassBiomeFinderItem;
    }

    public SpecialToolListener getSpecialToolListener() {
        return specialToolListener;
    }

    public ReservationDeedItem getReservationDeedItem() {
        return reservationDeedItem;
    }

    /** Anything tagged 거래불가 - checked by both the marketplace and plain /거래 staging so the
     * lore's promise actually holds everywhere, not just on /거래 거래소. Claimed land deeds
     * (normal or 토지선점권) are deliberately NOT here - trading those is their whole point. */
    public boolean isUntradeable(ItemStack item) {
        return rentalToolItem.isRentalTool(item)
                || specialHoeItem.isSpecialHoe(item)
                || specialPickaxeItem.isSpecialPickaxe(item)
                || specialAxeItem.isSpecialAxe(item)
                || fishingRodTierItem.isSpecialRod(item)
                || compassBiomeFinderItem.isSpecialCompass(item)
                || pendingDeedItem.isPending(item)
                || reservationDeedItem.isBlank(item);
    }

    /** Rental tools and special tools (unlike blank/claimed deeds) are locked to a single owner
     * strictly - dropped or otherwise, nobody else may even pick one up. */
    public boolean isOwnerLockedTool(ItemStack item) {
        return rentalToolItem.isRentalTool(item)
                || specialHoeItem.isSpecialHoe(item)
                || specialPickaxeItem.isSpecialPickaxe(item)
                || specialAxeItem.isSpecialAxe(item)
                || fishingRodTierItem.isSpecialRod(item)
                || compassBiomeFinderItem.isSpecialCompass(item);
    }

    public void tagToolOwner(ItemStack item, UUID owner) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "tool_owner"), PersistentDataType.STRING, owner.toString());
        item.setItemMeta(meta);
    }

    public UUID getToolOwner(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(this, "tool_owner"), PersistentDataType.STRING);
        return raw == null ? null : UUID.fromString(raw);
    }
}
